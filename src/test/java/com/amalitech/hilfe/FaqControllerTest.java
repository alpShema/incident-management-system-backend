package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.FaqController;
import com.amalitech.hilfe.dto.FaqInspectionResult;
import com.amalitech.hilfe.dto.FaqInspectionRow;
import com.amalitech.hilfe.dto.FaqInspectionSummary;
import com.amalitech.hilfe.dto.FaqRowStatus;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.FaqService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FaqController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class FaqControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean FaqService faqService;
    @MockitoBean TokenService tokenService;

    private UsernamePasswordAuthenticationToken adminAuth() {
        var principal = new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "faq.create"));
    }

    private MockMultipartFile csvFile() {
        return new MockMultipartFile("file", "faqs.csv", "text/csv",
                "question,answer\nQ1,A1\n".getBytes(StandardCharsets.UTF_8));
    }

    // Confirms the actual serialized JSON — not just the record accessors an in-process
    // service test reads — matches what the frontend's inspect-preview modal expects:
    // the enum as its plain name, and a missing field surfaced as an absent/null value
    // (which is what drives the "(missing question)" placeholder in the UI) rather than "".
    @Test
    void inspect_unfilteredFirstPage_returnsSummaryAndRowsWithEnumNameAndNullMissingFields() throws Exception {
        var readyRow = new FaqInspectionRow(2, "Q1", "A1", false, false, FaqRowStatus.READY);
        var brokenRow = new FaqInspectionRow(3, null, "A2", true, false, FaqRowStatus.NEEDS_ATTENTION);
        var result = new FaqInspectionResult(
                new FaqInspectionSummary(2, 1, 1),
                PageResponse.from(new PageImpl<>(List.of(readyRow, brokenRow), PageRequest.of(0, 20), 2)));
        when(faqService.inspectBulkImport(any(), isNull(), any())).thenReturn(result);

        mvc.perform(multipart("/faqs/inspect").file(csvFile())
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalRows").value(2))
                .andExpect(jsonPath("$.data.summary.readyCount").value(1))
                .andExpect(jsonPath("$.data.summary.needsAttentionCount").value(1))
                .andExpect(jsonPath("$.data.rows.items[0].status").value("READY"))
                .andExpect(jsonPath("$.data.rows.items[1].status").value("NEEDS_ATTENTION"))
                .andExpect(jsonPath("$.data.rows.items[1].question").doesNotExist())
                .andExpect(jsonPath("$.data.rows.items[1].questionMissing").value(true))
                .andExpect(jsonPath("$.data.rows.items[1].answer").value("A2"));
    }

    // Mirrors the "Show rows with errors" screenshot: the row list is filtered down to
    // the 2 problem rows, but the summary (and rows.totalElements) must still describe
    // the whole 100-row file -- that pairing is what backs "Showing 1 to 2 of 100 FAQs".
    @Test
    void inspect_statusFilterNeedsAttention_summaryStillReflectsWholeFile() throws Exception {
        var brokenRow1 = new FaqInspectionRow(3, null, "A2", true, false, FaqRowStatus.NEEDS_ATTENTION);
        var brokenRow2 = new FaqInspectionRow(4, "Q3", null, false, true, FaqRowStatus.NEEDS_ATTENTION);
        var result = new FaqInspectionResult(
                new FaqInspectionSummary(100, 98, 2),
                PageResponse.from(new PageImpl<>(List.of(brokenRow1, brokenRow2), PageRequest.of(0, 20), 2)));
        when(faqService.inspectBulkImport(any(), eq(FaqRowStatus.NEEDS_ATTENTION), any())).thenReturn(result);

        mvc.perform(multipart("/faqs/inspect").file(csvFile())
                        .param("status", "NEEDS_ATTENTION")
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalRows").value(100))
                .andExpect(jsonPath("$.data.rows.items.length()").value(2))
                .andExpect(jsonPath("$.data.rows.totalElements").value(2));

        verify(faqService).inspectBulkImport(any(), eq(FaqRowStatus.NEEDS_ATTENTION), any());
    }

    @Test
    void inspect_lowercaseStatusParam_returns400() throws Exception {
        mvc.perform(multipart("/faqs/inspect").file(csvFile())
                        .param("status", "needs_attention")
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inspect_emptyFile_returns400WithoutCallingService() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "faqs.csv", "text/csv", new byte[0]);

        mvc.perform(multipart("/faqs/inspect").file(emptyFile)
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The uploaded file is empty. Please choose a file and try again."));

        verify(faqService, never()).inspectBulkImport(any(), any(), any());
    }

    @Test
    void inspect_nonCsvFile_returns400() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile("file", "faqs.txt", "text/plain",
                "question,answer\nQ1,A1\n".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/faqs/inspect").file(txtFile)
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only CSV files are accepted. Please upload a file with a .csv extension."));
    }
}
