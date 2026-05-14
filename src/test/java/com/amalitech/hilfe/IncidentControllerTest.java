package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.IncidentController;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.dto.AssignIncidentRequest;
import com.amalitech.hilfe.dto.CreateIncidentRequest;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.UpdateIncidentSeverityRequest;
import com.amalitech.hilfe.dto.UpdateIncidentStatusRequest;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
@Import({GlobalExceptionHandler.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class IncidentControllerTest {

    @Autowired MockMvc mvc;
    final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean IncidentService incidentService;
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

    private IncidentResponse stubResponse() {
        return new IncidentResponse(
                "inc-1", 1, "Test Incident", "Description",
                null, null, null, null,
                null, false, null, null, null);
    }

    // ── POST /incidents ───────────────────────────────────────────────────────

    @Test
    void createIncident_validRequest_returns201() throws Exception {
        when(incidentService.createIncident(anyString(), any(CreateIncidentRequest.class))).thenReturn(stubResponse());

        var principal = clientPrincipal();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "incident.create"));

        mvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIncidentRequest("Test Incident", "Description", "type-1", "loc-1", null, null)))
                        .with(authentication(auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Incident created successfully"));
    }

    @Test
    void createIncident_missingTitle_returns400() throws Exception {
        var principal = clientPrincipal();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "incident.create"));

        mvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIncidentRequest("", "Description", "type-1", "loc-1", null, null)))
                        .with(authentication(auth)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createIncident_invalidLocationId_returns404() throws Exception {
        when(incidentService.createIncident(any(), any()))
                .thenThrow(new ArmsAuthException("Location with the provided ID could not be found.", 404));

        var auth = new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.create"));

        mvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIncidentRequest("Title", "Description", "type-1", "bad-loc", null, null)))
                        .with(authentication(auth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Location with the provided ID could not be found."));
    }

    @Test
    void createIncident_invalidIncidentTypeId_returns404() throws Exception {
        when(incidentService.createIncident(any(), any()))
                .thenThrow(new ArmsAuthException("Incident type with the provided ID could not be found.", 404));

        var auth = new UsernamePasswordAuthenticationToken(clientPrincipal(), null, List.of(() -> "incident.create"));

        mvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIncidentRequest("Title", "Description", "bad-type", "loc-1", null, null)))
                        .with(authentication(auth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Incident type with the provided ID could not be found."));
    }

    // ── GET /incidents/{id} ───────────────────────────────────────────────────

    @Test
    void getIncident_found_returns200() throws Exception {
        when(incidentService.getIncident("inc-1")).thenReturn(stubResponse());

        var principal = clientPrincipal();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "incident.read.own"));

        mvc.perform(get("/incidents/inc-1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("inc-1"));
    }

    @Test
    void getIncident_notFound_returns404() throws Exception {
        when(incidentService.getIncident("missing"))
                .thenThrow(new ArmsAuthException("Incident not found", 404));

        var principal = clientPrincipal();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "incident.read.own"));

        mvc.perform(get("/incidents/missing")
                        .with(authentication(auth)))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /incidents/{id}/status ──────────────────────────────────────────

    @Test
    void updateStatus_validRequest_returns200() throws Exception {
        when(incidentService.updateStatus(anyString(), eq("inc-1"), any(UpdateIncidentStatusRequest.class))).thenReturn(stubResponse());

        var principal = agentPrincipal();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "incident.status.change"));

        mvc.perform(patch("/incidents/inc-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateIncidentStatusRequest("status-pending")))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident status updated successfully"));
    }

    // ── PATCH /incidents/{id}/severity ────────────────────────────────────────

    @Test
    void updateSeverity_validRequest_returns200() throws Exception {
        when(incidentService.updateSeverity(anyString(), eq("inc-1"), any(UpdateIncidentSeverityRequest.class))).thenReturn(stubResponse());

        var principal = agentPrincipal();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "incident.severity.change"));

        mvc.perform(patch("/incidents/inc-1/severity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateIncidentSeverityRequest("sev-high")))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident severity updated successfully"));
    }

    // ── PATCH /incidents/{id}/assign ──────────────────────────────────────────

    @Test
    void assignIncident_validRequest_returns200() throws Exception {
        when(incidentService.assignIncident(anyString(), eq("inc-1"), any(AssignIncidentRequest.class))).thenReturn(stubResponse());

        var principal = adminPrincipal();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "incident.assign"));

        mvc.perform(patch("/incidents/inc-1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignIncidentRequest("agent-1")))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident assigned successfully"));
    }
}
