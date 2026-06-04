package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.IncidentController;
import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class IncidentControllerTest {

    @Autowired MockMvc mvc;
    final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean IncidentService incidentService;
    @MockitoBean ActivityLogService activityLogService;
    @MockitoBean TokenService tokenService;

    private JwtTokenService.AuthPrincipal clientPrincipal() {
        return new JwtTokenService.AuthPrincipal("user-1", "user@test.com", RoleCode.CLIENT);
    }

    private JwtTokenService.AuthPrincipal agentPrincipal() {
        return new JwtTokenService.AuthPrincipal("agent-user-1", "agent@test.com", RoleCode.AGENT);
    }

    private JwtTokenService.AuthPrincipal adminPrincipal() {
        return new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(adminPrincipal(), null, List.of(() -> "dashboard.admin"));
    }

    private UsernamePasswordAuthenticationToken agentAuth() {
        return new UsernamePasswordAuthenticationToken(agentPrincipal(), null, List.of(() -> "dashboard.agent"));
    }

    private UsernamePasswordAuthenticationToken clientAuth() {
        return new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.read.own"));
    }

    private IncidentResponse stubResponse() {
        return new IncidentResponse(
                "inc-1", 1, "Test Incident", "Description",
                null, null, new LookupResponse("sev-low", "Low"), null,
                new com.amalitech.hilfe.dto.CreatorResponse("user-1", "John Doe", "http://img.png"),
                null, false, null, null, null, null, null, null, null);
    }

    // ── POST /incidents ───────────────────────────────────────────────────────

    @Test
    void createIncident_validRequest_returns201() throws Exception {
        when(incidentService.createIncident(any(), any(CreateIncidentRequest.class))).thenReturn(stubResponse());

        mvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIncidentRequest("Test Incident", "Description", "type-1", "loc-1", null, null)))
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.create")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Incident created successfully"))
                .andExpect(jsonPath("$.data.priority.id").value("sev-low"))
                .andExpect(jsonPath("$.data.severity").doesNotExist());
    }

    @Test
    void createIncident_unsupportedContentType_returns415() throws Exception {
        mvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("title=Test&description=Desc")
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.create")))))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Unsupported Media Type. Please submit the request body as application/json."));
    }

    @Test
    void createIncident_missingTitle_returns400() throws Exception {
        mvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIncidentRequest("", "Description", "type-1", "loc-1", null, null)))
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.create")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createIncident_titleOver100Characters_returns400() throws Exception {
        mvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIncidentRequest("a".repeat(101), "Description", "type-1", "loc-1", null, null)))
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.create")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title: title must not exceed 100 characters"));

        verify(incidentService, never()).createIncident(anyString(), any(CreateIncidentRequest.class));
    }

    @Test
    void createIncident_descriptionOver1000Characters_returns400() throws Exception {
        mvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIncidentRequest("Title", "a".repeat(1001), "type-1", "loc-1", null, null)))
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.create")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("description: description must not exceed 1000 characters"));

        verify(incidentService, never()).createIncident(anyString(), any(CreateIncidentRequest.class));
    }

    @Test
    void createIncident_invalidLocationId_returns404() throws Exception {
        when(incidentService.createIncident(any(), any()))
                .thenThrow(new ArmsAuthException("Location with the provided ID could not be found.", 404));

        mvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIncidentRequest("Title", "Description", "type-1", "bad-loc", null, null)))
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.create")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Location with the provided ID could not be found."));
    }

    @Test
    void createIncident_invalidIncidentTypeId_returns404() throws Exception {
        when(incidentService.createIncident(any(), any()))
                .thenThrow(new ArmsAuthException("Incident type with the provided ID could not be found.", 404));

        mvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIncidentRequest("Title", "Description", "bad-type", "loc-1", null, null)))
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.create")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Incident type with the provided ID could not be found."));
    }

    // ── GET /incidents/{id} ───────────────────────────────────────────────────

    @Test
    void getIncident_found_returns200() throws Exception {
        when(incidentService.getIncident("user-1", RoleCode.CLIENT, "inc-1")).thenReturn(stubResponse());

        mvc.perform(get("/incidents/inc-1")
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.read.own")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("inc-1"));
    }

    @Test
    void getIncident_notFound_returns404() throws Exception {
        when(incidentService.getIncident("user-1", RoleCode.CLIENT, "missing"))
                .thenThrow(new ArmsAuthException("Incident not found", 404));

        mvc.perform(get("/incidents/missing")
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.read.own")))))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /incidents/{id}/status ──────────────────────────────────────────

    @Test
    void updateStatus_validRequest_returns200() throws Exception {
        when(incidentService.updateStatus(anyString(), any(RoleCode.class), eq("inc-1"), any(UpdateIncidentStatusRequest.class))).thenReturn(stubResponse());

        mvc.perform(patch("/incidents/inc-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateIncidentStatusRequest("status-pending", "Waiting for parts")))
                        .with(authentication(new UsernamePasswordAuthenticationToken(agentPrincipal(), null, List.of(() -> "incident.status.change")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident status updated successfully"));
    }

    // ── PATCH /incidents/{id}/severity ────────────────────────────────────────

    @Test
    void updateSeverity_validRequest_returns200() throws Exception {
        when(incidentService.updateSeverity(anyString(), eq("inc-1"), any(UpdateIncidentSeverityRequest.class))).thenReturn(stubResponse());

        mvc.perform(patch("/incidents/inc-1/severity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateIncidentSeverityRequest("sev-high")))
                        .with(authentication(new UsernamePasswordAuthenticationToken(agentPrincipal(), null, List.of(() -> "incident.severity.change")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident severity updated successfully"));
    }

    // ── PATCH /incidents/{id}/assign ──────────────────────────────────────────

    @Test
    void assignIncident_validRequest_returns200() throws Exception {
        when(incidentService.assignIncident(anyString(), eq("inc-1"), any(AssignIncidentRequest.class))).thenReturn(stubResponse());

        mvc.perform(patch("/incidents/inc-1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignIncidentRequest("agent-1")))
                        .with(authentication(new UsernamePasswordAuthenticationToken(adminPrincipal(), null, List.of(() -> "incident.assign")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident assigned successfully"));
    }

    // ── GET /incidents (admin all) ────────────────────────────────────────────

    @Test
    void listAllIncidents_noAuth_returns401() throws Exception {
        mvc.perform(get("/incidents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required. Please log in to access this resource."));
    }

    @Test
    void listAllIncidents_clientAuth_returns403() throws Exception {
        mvc.perform(get("/incidents").with(authentication(clientAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAllIncidents_agentAuth_returns403() throws Exception {
        mvc.perform(get("/incidents").with(authentication(agentAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAllIncidents_adminAuth_returns200() throws Exception {
        when(incidentService.queryAllIncidents(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incidents retrieved successfully"))
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listAllIncidents_keywordOnly_returns200() throws Exception {
        when(incidentService.queryAllIncidents(eq("projector"), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents").param("query", "projector").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listAllIncidents_filterOnly_returns200() throws Exception {
        when(incidentService.queryAllIncidents(isNull(), eq("status-open"), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents").param("statusId", "status-open").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listAllIncidents_combinedKeywordAndFilter_returns200() throws Exception {
        when(incidentService.queryAllIncidents(eq("fire"), eq("status-open"), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents").param("query", "fire").param("statusId", "status-open").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listAllIncidents_emptyResult_returns200() throws Exception {
        when(incidentService.queryAllIncidents(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/incidents").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ── GET /incidents/my-incidents ───────────────────────────────────────────

    @Test
    void listMyIncidents_noAuth_returns401() throws Exception {
        mvc.perform(get("/incidents/my-incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listMyIncidents_clientAuth_returns200() throws Exception {
        when(incidentService.queryIncidents(eq("user-1"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/my-incidents").with(authentication(clientAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incidents retrieved successfully"))
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listMyIncidents_agentAuth_returns200() throws Exception {
        when(incidentService.queryIncidents(eq("agent-user-1"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/my-incidents").with(authentication(agentAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incidents retrieved successfully"));
    }

    @Test
    void listMyIncidents_adminAuth_returns200() throws Exception {
        when(incidentService.queryIncidents(eq("admin-1"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/my-incidents").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incidents retrieved successfully"));
    }

    @Test
    void listMyIncidents_keywordOnly_returns200() throws Exception {
        when(incidentService.queryIncidents(anyString(), eq("fire"), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/my-incidents").param("query", "fire").with(authentication(clientAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listMyIncidents_combinedQueryAndFilter_returns200() throws Exception {
        when(incidentService.queryIncidents(anyString(), eq("fire"), eq("status-open"), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/my-incidents").param("query", "fire").param("statusId", "status-open").with(authentication(clientAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listMyIncidents_emptyResult_returns200() throws Exception {
        when(incidentService.queryIncidents(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/incidents/my-incidents").with(authentication(clientAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ── GET /incidents/dept-incidents ─────────────────────────────────────────

    @Test
    void listDeptIncidents_noAuth_returns401() throws Exception {
        mvc.perform(get("/incidents/dept-incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listDeptIncidents_clientAuth_returns403() throws Exception {
        mvc.perform(get("/incidents/dept-incidents").with(authentication(clientAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listDeptIncidents_adminAuth_returns200() throws Exception {
        when(incidentService.queryDeptIncidents(eq("admin-1"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/dept-incidents").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Department incidents retrieved successfully"))
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listDeptIncidents_agentAuth_returns200() throws Exception {
        when(incidentService.queryDeptIncidents(eq("agent-user-1"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/dept-incidents").with(authentication(agentAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Department incidents retrieved successfully"));
    }

    @Test
    void listDeptIncidents_keywordOnly_returns200() throws Exception {
        when(incidentService.queryDeptIncidents(anyString(), eq("fire"), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/dept-incidents").param("query", "fire").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listDeptIncidents_combinedQueryAndFilter_returns200() throws Exception {
        when(incidentService.queryDeptIncidents(anyString(), eq("fire"), eq("status-open"), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/dept-incidents").param("query", "fire").param("statusId", "status-open").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listDeptIncidents_emptyResult_returns200() throws Exception {
        when(incidentService.queryDeptIncidents(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/incidents/dept-incidents").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ── GET /incidents/assigned-incidents ─────────────────────────────────────

    @Test
    void listAssignedIncidents_noAuth_returns401() throws Exception {
        mvc.perform(get("/incidents/assigned-incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAssignedIncidents_clientAuth_returns403() throws Exception {
        mvc.perform(get("/incidents/assigned-incidents").with(authentication(clientAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAssignedIncidents_adminAuth_returns200() throws Exception {
        when(incidentService.queryAssignedIncidents(eq("admin-1"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/assigned-incidents").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Assigned incidents retrieved successfully"))
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listAssignedIncidents_agentAuth_returns200() throws Exception {
        when(incidentService.queryAssignedIncidents(eq("agent-user-1"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/assigned-incidents").with(authentication(agentAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Assigned incidents retrieved successfully"));
    }

    @Test
    void listAssignedIncidents_keywordOnly_returns200() throws Exception {
        when(incidentService.queryAssignedIncidents(anyString(), eq("fire"), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/assigned-incidents").param("query", "fire").with(authentication(agentAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listAssignedIncidents_combinedQueryAndFilter_returns200() throws Exception {
        when(incidentService.queryAssignedIncidents(anyString(), eq("fire"), eq("status-open"), isNull(), isNull(), isNull(), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubResponse())));

        mvc.perform(get("/incidents/assigned-incidents").param("query", "fire").param("statusId", "status-open").with(authentication(agentAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void listAssignedIncidents_emptyResult_returns200() throws Exception {
        when(incidentService.queryAssignedIncidents(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/incidents/assigned-incidents").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ── GET /incidents/search ─────────────────────────────────────────────────

    @Test
    void searchIncidents_validQuery_returns200() throws Exception {
        var pagedResponse = new PageImpl<>(List.of(stubResponse()));
        when(incidentService.searchIncidents(any(), eq("projector"), any(), any(), any())).thenReturn(pagedResponse);

        mvc.perform(get("/incidents/search").param("query", "projector")
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.read.own")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incidents retrieved successfully"))
                .andExpect(jsonPath("$.data.items[0].id").value("inc-1"));
    }

    @Test
    void searchIncidents_blankQuery_returns400() throws Exception {
        when(incidentService.searchIncidents(any(), eq(""), any(), any(), any()))
                .thenThrow(new ArmsAuthException("Search query must not be blank", 400));

        mvc.perform(get("/incidents/search").param("query", "")
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.read.own")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Search query must not be blank"));
    }

    @Test
    void searchIncidents_missingQueryParam_returns400() throws Exception {
        mvc.perform(get("/incidents/search")
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.read.own")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'query' is missing"));
    }

    @Test
    void searchIncidents_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/incidents/search").param("query", "fire"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchIncidents_emptyResults_returns200WithEmptyPage() throws Exception {
        when(incidentService.searchIncidents(any(), anyString(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/incidents/search").param("query", "nonexistent")
                        .with(authentication(new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.read.own")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }
}
