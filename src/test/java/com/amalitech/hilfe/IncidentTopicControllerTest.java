package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.IncidentTopicController;
import com.amalitech.hilfe.dto.IncidentTopicListResponse;
import com.amalitech.hilfe.dto.IncidentTopicResponse;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.dto.UpdateIncidentTypeStatusRequest;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.IncidentCategoryService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentTopicController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class IncidentTopicControllerTest {

    @Autowired MockMvc mvc;
    final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean IncidentCategoryService incidentCategoryService;
    @MockitoBean TokenService tokenService;

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
                false,
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
    void listTopics_noStatusFilter_returns200WithAllTopics() throws Exception {
        IncidentTopicListResponse active = new IncidentTopicListResponse(
                "type-1", "Projector", "Projector issues", true, false, true,
                LookupResponse.from("cat-1", "Facilities"), null);
        IncidentTopicListResponse inactive = new IncidentTopicListResponse(
                "type-2", "Old Topic", "Deprecated", true, false, false,
                LookupResponse.from("cat-1", "Facilities"), null);
        when(incidentCategoryService.listTopics(any(), any(), any(), eq((Boolean) null), any(), any()))
                .thenReturn(new PageImpl<>(List.of(active, inactive), PageRequest.of(0, 20), 2));

        mvc.perform(get("/incident-topics")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].status").value(true))
                .andExpect(jsonPath("$.data.items[1].status").value(false));
    }

    @Test
    void listTopics_statusFalse_returns200WithInactiveTopics() throws Exception {
        IncidentTopicListResponse inactive = new IncidentTopicListResponse(
                "type-2", "Old Topic", "Deprecated", true, false, false,
                LookupResponse.from("cat-1", "Facilities"), null);
        when(incidentCategoryService.listTopics(any(), any(), any(), eq(false), any(), any()))
                .thenReturn(new PageImpl<>(List.of(inactive), PageRequest.of(0, 20), 1));

        mvc.perform(get("/incident-topics")
                        .param("status", "false")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void updateTopicStatus_deactivate_returns200() throws Exception {
        IncidentTopicResponse response = new IncidentTopicResponse(
                "type-1", "Projector", "Projector issues", true, false, false, null, null);
        when(incidentCategoryService.updateTopicStatus("type-1", false)).thenReturn(response);

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident-type.delete"));

        mvc.perform(patch("/incident-topics/type-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateIncidentTypeStatusRequest(false)))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident topic status updated successfully"))
                .andExpect(jsonPath("$.data.status").value(false));
    }

    @Test
    void updateTopicStatus_activate_returns200() throws Exception {
        IncidentTopicResponse response = new IncidentTopicResponse(
                "type-1", "Projector", "Projector issues", true, false, true, null, null);
        when(incidentCategoryService.updateTopicStatus("type-1", true)).thenReturn(response);

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident-type.delete"));

        mvc.perform(patch("/incident-topics/type-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateIncidentTypeStatusRequest(true)))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(true));
    }

    @Test
    void listTopics_invalidStatus_returns400() throws Exception {
        mvc.perform(get("/incident-topics")
                        .param("status", "archived")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN")))))
                .andExpect(status().isBadRequest());
    }
}
