package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.IncidentTopicController;
import com.amalitech.hilfe.dto.IncidentTopicListResponse;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.IncidentCategoryService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentTopicController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class IncidentTopicControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    IncidentCategoryService incidentCategoryService;
    @MockitoBean
    TokenService tokenService;

    private JwtTokenService.AuthPrincipal adminPrincipal() {
        return new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
    }

    @Test
    void listTopics_returns200WithPagedData() throws Exception {
        IncidentTopicListResponse row = new IncidentTopicListResponse(
                "type-1",
                "Projector",
                "Projector issues",
                true,
                LookupResponse.from("cat-1", "Facilities"),
                new IncidentTopicListResponse.AgentGroupSummary(
                        "group-1",
                        "IT Support")
        );
        when(incidentCategoryService.listTopics(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        mvc.perform(get("/incident-topics")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident topics retrieved successfully"))
                .andExpect(jsonPath("$.data.items[0].id").value("type-1"))
                .andExpect(jsonPath("$.data.items[0].category.id").value("cat-1"))
                .andExpect(jsonPath("$.data.items[0].agentGroup.id").value("group-1"));
    }

    @Test
    void listTopics_invalidStatus_returns400() throws Exception {
        when(incidentCategoryService.listTopics(any(), any(), any(), eq("archived"), any(), any()))
                .thenThrow(new ArmsAuthException("Invalid status. Allowed values are active or inactive", 400));

        mvc.perform(get("/incident-topics")
                        .param("status", "archived")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN")))))
                .andExpect(status().isBadRequest());
    }
}
