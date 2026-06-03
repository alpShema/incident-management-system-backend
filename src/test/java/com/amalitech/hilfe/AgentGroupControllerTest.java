package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.AgentGroupController;
import com.amalitech.hilfe.dto.AgentGroupResponse;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.AgentGroupService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentGroupController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class AgentGroupControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AgentGroupService agentGroupService;
    @MockitoBean TokenService tokenService;

    private UsernamePasswordAuthenticationToken adminAuth(String authority) {
        var principal = new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> authority));
    }

    @Test
    void deleteAgentGroup_withMembers_returns204() throws Exception {
        mvc.perform(delete("/agent-groups/group-1")
                        .with(authentication(adminAuth("agent-group.delete"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAgentGroup_alreadyInactive_returns409() throws Exception {
        doThrow(new ArmsAuthException("Agent group is already inactive", 409))
                .when(agentGroupService).deleteAgentGroup("group-1");

        mvc.perform(delete("/agent-groups/group-1")
                        .with(authentication(adminAuth("agent-group.delete"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Agent group is already inactive"));
    }

    @Test
    void getAgentGroup_inactive_returns200() throws Exception {
        when(agentGroupService.getAgentGroup("group-1")).thenReturn(new AgentGroupResponse(
                "group-1",
                "IT Support",
                "Handles IT incidents",
                LookupResponse.from("dept-1", "Facilities"),
                false,
                2,
                null
        ));

        mvc.perform(get("/agent-groups/group-1")
                        .with(authentication(adminAuth("agent-group.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("group-1"))
                .andExpect(jsonPath("$.data.status").value(false))
                .andExpect(jsonPath("$.data.memberCount").value(2));
    }
}
