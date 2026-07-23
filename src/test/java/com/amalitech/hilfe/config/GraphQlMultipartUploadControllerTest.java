package com.amalitech.hilfe.config;

import com.amalitech.hilfe.controllers.graphql.FaqResolver;
import com.amalitech.hilfe.dto.FaqBulkUploadResult;
import com.amalitech.hilfe.dto.FaqInspectionResult;
import com.amalitech.hilfe.dto.FaqInspectionRow;
import com.amalitech.hilfe.dto.FaqInspectionSummary;
import com.amalitech.hilfe.dto.FaqRowStatus;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.FaqService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration;
import org.springframework.boot.graphql.autoconfigure.servlet.GraphQlWebMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Exercises the actual multipart wire protocol (operations + map + file part) against the
// real GraphQL execution pipeline: FaqResolver, the Upload scalar (GraphQlConfig),
// GraphQlExceptionResolver, and GraphQlResponseFilter, secured by the real SecurityConfig
// filter chain. This is the one genuinely new piece of infrastructure the GraphQL FAQ
// bulk-import feature adds, so it gets its own integration-style slice test rather than
// relying on FaqService's existing unit-test coverage.
@WebMvcTest(controllers = GraphQlMultipartUploadController.class)
@ImportAutoConfiguration({GraphQlAutoConfiguration.class, GraphQlWebMvcAutoConfiguration.class})
@Import({FaqResolver.class, GraphQlConfig.class, GraphQlResponseFilter.class, GraphQlExceptionResolver.class,
        SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class, JacksonConfig.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class GraphQlMultipartUploadControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean FaqService faqService;
    @MockitoBean TokenService tokenService;

    private UsernamePasswordAuthenticationToken adminAuth() {
        var principal = new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "faq.create"));
    }

    private MockMultipartFile csvPart() {
        return new MockMultipartFile("0", "faqs.csv", "text/csv",
                "question,answer\nQ1,A1\n".getBytes(StandardCharsets.UTF_8));
    }

    private static final String BULK_IMPORT_OPERATIONS =
            "{\"query\":\"mutation($file: Upload!) { bulkImportFaqs(file: $file) { created updated failed errors { row reason } } }\","
                    + "\"variables\":{\"file\":null}}";
    private static final String SINGLE_FILE_MAP = "{\"0\":[\"variables.file\"]}";

    @Test
    void bulkImportFaqs_validMultipartRequest_succeedsAndSurfacesCustomMessage() throws Exception {
        when(faqService.bulkImport(any())).thenReturn(
                new FaqBulkUploadResult(1, 0, 0, List.of()));

        mvc.perform(multipart("/graphql/upload")
                        .file(csvPart())
                        .param("operations", BULK_IMPORT_OPERATIONS)
                        .param("map", SINGLE_FILE_MAP)
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bulkImportFaqs.created").value(1))
                .andExpect(jsonPath("$.data.bulkImportFaqs.updated").value(0))
                .andExpect(jsonPath("$.data.bulkImportFaqs.failed").value(0))
                // Proves the request-attribute bridge between the resolver's
                // GraphQlResponseMessage.set(...) call and GraphQlResponseFilter survives
                // .block()-ing the WebGraphQlHandler on this transport -- if the thread-local
                // bridge broke, this would silently fall back to the generic "Success" message.
                .andExpect(jsonPath("$.message").value("1 FAQ(s) created, 0 updated, 0 row(s) skipped."));
    }

    @Test
    void bulkImportFaqs_noAuthority_rejectedWithAuthenticationRequiredError() throws Exception {
        mvc.perform(multipart("/graphql/upload")
                        .file(csvPart())
                        .param("operations", BULK_IMPORT_OPERATIONS)
                        .param("map", SINGLE_FILE_MAP)
                        .with(csrf()))
                // GraphQL errors are reported inside the response body's errors[] array with
                // HTTP 200 (this codebase's established convention, see
                // GraphQlExceptionResolverTest) -- not as an HTTP 401/403 status code.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bulkImportFaqs").doesNotExist())
                .andExpect(jsonPath("$.errors[0].message").value("Authentication required. Please log in to access this resource."))
                .andExpect(jsonPath("$.errors[0].extensions.status").value(401));
    }

    @Test
    void inspectFaqImport_missingAnswerColumn_returnsNeedsAttentionRow() throws Exception {
        var row = new FaqInspectionRow(2, "Q1", null, false, true, FaqRowStatus.NEEDS_ATTENTION);
        var result = new FaqInspectionResult(
                new FaqInspectionSummary(1, 0, 1),
                PageResponse.from(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1)));
        when(faqService.inspectBulkImport(any(), isNull(), any())).thenReturn(result);

        String operations = "{\"query\":\"query($file: Upload!) { inspectFaqImport(file: $file) { "
                + "summary { totalRows readyCount needsAttentionCount } "
                + "rows { items { row question answer questionMissing answerMissing status } } } }\","
                + "\"variables\":{\"file\":null}}";

        mvc.perform(multipart("/graphql/upload")
                        .file(csvPart())
                        .param("operations", operations)
                        .param("map", SINGLE_FILE_MAP)
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inspectFaqImport.summary.needsAttentionCount").value(1))
                .andExpect(jsonPath("$.data.inspectFaqImport.rows.items[0].status").value("NEEDS_ATTENTION"))
                .andExpect(jsonPath("$.data.inspectFaqImport.rows.items[0].answer").doesNotExist());
    }

    @Test
    void handle_malformedOperationsJson_returnsWellFormedErrorEnvelope() throws Exception {
        mvc.perform(multipart("/graphql/upload")
                        .file(csvPart())
                        .param("operations", "not valid json")
                        .param("map", SINGLE_FILE_MAP)
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors[0].extensions.status").value(400));
    }

    @Test
    void handle_missingFilePart_returnsWellFormedErrorEnvelope() throws Exception {
        mvc.perform(multipart("/graphql/upload")
                        .param("operations", BULK_IMPORT_OPERATIONS)
                        .param("map", SINGLE_FILE_MAP)
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors[0].message").value("No file part found for multipart field '0'."));
    }

    @Test
    void faqImportTemplate_matchesRestDownloadableTemplate() throws Exception {
        mvc.perform(post("/graphql")
                        .contentType(APPLICATION_JSON)
                        .content("{\"query\":\"{ faqImportTemplate }\"}")
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.faqImportTemplate").value(FaqService.CSV_IMPORT_TEMPLATE));
    }
}
