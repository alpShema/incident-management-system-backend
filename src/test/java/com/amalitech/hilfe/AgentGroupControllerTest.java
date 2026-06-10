package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.AgentGroupController;
import com.amalitech.hilfe.dto.AgentGroupResponse;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.dto.UpdateAgentGroupStatusRequest;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.AgentGroupService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentGroupController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class AgentGroupControllerTest {

    @Autowired MockMvc mvc;
    final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean AgentGroupService agentGroupService;
    @MockitoBean TokenService tokenService;

    private JwtTokenService.AuthPrincipal adminPrincipal() {
        return new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
    }

    private UsernamePasswordAuthenticationToken adminAuth(String authority) {
        return new UsernamePasswordAuthenticationToken(adminPrincipal(), null, List.of(() -> authority));
    }

    private AgentGroupResponse group(boolean status) {
        return new AgentGroupResponse(
                "group-1",
                "IT Support",
                "Handles IT incidents",
                LookupResponse.from("dept-1", "Facilities"),
                status,
                2,
                null,
                null
        );
    }

    @Test
    void updateAgentGroupStatus_deactivate_returns200() throws Exception {
        when(agentGroupService.updateAgentGroupStatus("admin-1", "group-1", false)).thenReturn(group(false));

        mvc.perform(patch("/agent-groups/group-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAgentGroupStatusRequest(false)))
                        .with(authentication(adminAuth("agent-group.delete"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Agent group status updated successfully"))
                .andExpect(jsonPath("$.data.status").value(false));
    }

    @Test
    void updateAgentGroupStatus_activate_returns200() throws Exception {
        when(agentGroupService.updateAgentGroupStatus("admin-1", "group-1", true)).thenReturn(group(true));

        mvc.perform(patch("/agent-groups/group-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAgentGroupStatusRequest(true)))
                        .with(authentication(adminAuth("agent-group.delete"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(true));
    }

    @Test
    void updateAgentGroupStatus_alreadyInactive_returns409() throws Exception {
        when(agentGroupService.updateAgentGroupStatus("admin-1", "group-1", false))
                .thenThrow(new ArmsAuthException("Agent group is already inactive", 409));

        mvc.perform(patch("/agent-groups/group-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAgentGroupStatusRequest(false)))
                        .with(authentication(adminAuth("agent-group.delete"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Agent group is already inactive"));
    }

    @Test
    void updateAgentGroupStatus_notFound_returns404() throws Exception {
        when(agentGroupService.updateAgentGroupStatus(eq("admin-1"), eq("group-1"), any()))
                .thenThrow(new ArmsAuthException("Agent group not found", 404));

        mvc.perform(patch("/agent-groups/group-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAgentGroupStatusRequest(true)))
                        .with(authentication(adminAuth("agent-group.delete"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAgentGroupStatus_missingStatus_returns400() throws Exception {
        mvc.perform(patch("/agent-groups/group-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authentication(adminAuth("agent-group.delete"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAgentGroupStatus_missingPermission_returns403() throws Exception {
        mvc.perform(patch("/agent-groups/group-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAgentGroupStatusRequest(true)))
                        .with(authentication(adminAuth("agent-group.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAllAgentGroups_noStatus_returnsAll() throws Exception {
        when(agentGroupService.listAllAgentGroups(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(group(true), group(false))));

        mvc.perform(get("/agent-groups/all")
                        .with(authentication(adminAuth("agent-group.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Agent groups retrieved successfully"))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void listAllAgentGroups_statusActive_returnsActiveOnly() throws Exception {
        when(agentGroupService.listAllAgentGroups(eq("active"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(group(true))));

        mvc.perform(get("/agent-groups/all?status=active")
                        .with(authentication(adminAuth("agent-group.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value(true));
    }

    @Test
    void listAllAgentGroups_statusDeactivated_returnsDeactivatedOnly() throws Exception {
        when(agentGroupService.listAllAgentGroups(eq("deactivated"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(group(false))));

        mvc.perform(get("/agent-groups/all?status=deactivated")
                        .with(authentication(adminAuth("agent-group.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value(false));
    }

    @Test
    void listAllAgentGroups_emptyResult_returns200WithEmptyList() throws Exception {
        when(agentGroupService.listAllAgentGroups(eq("deactivated"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/agent-groups/all?status=deactivated")
                        .with(authentication(adminAuth("agent-group.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void listAllAgentGroups_invalidStatus_returns400() throws Exception {
        when(agentGroupService.listAllAgentGroups(eq("unknown"), isNull(), isNull(), any(Pageable.class)))
                .thenThrow(new ArmsAuthException("Invalid status filter. Accepted values: active, deactivated, all", 400));

        mvc.perform(get("/agent-groups/all?status=unknown")
                        .with(authentication(adminAuth("agent-group.read"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid status filter. Accepted values: active, deactivated, all"));
    }

    @Test
    void listAllAgentGroups_missingPermission_returns403() throws Exception {
        mvc.perform(get("/agent-groups/all")
                        .with(authentication(adminAuth("agent-group.create"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAgentGroup_inactive_returns200() throws Exception {
        when(agentGroupService.getAgentGroup("group-1")).thenReturn(group(false));

        mvc.perform(get("/agent-groups/group-1")
                        .with(authentication(adminAuth("agent-group.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("group-1"))
                .andExpect(jsonPath("$.data.status").value(false))
                .andExpect(jsonPath("$.data.memberCount").value(2));
    }
}
