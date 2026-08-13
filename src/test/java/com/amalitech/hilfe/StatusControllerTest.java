package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.StatusController;
import com.amalitech.hilfe.dto.StatusLookupResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.StatusService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class StatusControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean StatusService statusService;
    @MockitoBean TokenService tokenService;

    private static final List<StatusLookupResponse> FULL_STATUS_LIST = List.of(
            new StatusLookupResponse("status-open", "Open", "Open status"),
            new StatusLookupResponse("status-pending", "Pending", "Pending status"),
            new StatusLookupResponse("status-resolved", "Resolved", "Resolved status"),
            new StatusLookupResponse("status-closed", "Closed", "Closed status"),
            new StatusLookupResponse("status-unassigned", "Unassigned", "Unassigned status")
    );

    private JwtTokenService.AuthPrincipal principalFor(RoleCode role) {
        return new JwtTokenService.AuthPrincipal("user-1", "user@test.com", role);
    }

    @Test
    void listStatuses_returns200WithData() throws Exception {
        when(statusService.listStatuses()).thenReturn(FULL_STATUS_LIST);

        mvc.perform(get("/statuses")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principalFor(RoleCode.CLIENT), null, List.of(() -> "ROLE_CLIENT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Statuses retrieved successfully"))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].name").value("Open"))
                .andExpect(jsonPath("$.data[0].description").value("Open status"));

        verify(statusService).listStatuses();
    }

    // HV-1653: Unassigned must come back for every role -- there is no role-based filtering
    // left in this endpoint.
    @ParameterizedTest
    @EnumSource(RoleCode.class)
    void listStatuses_includesUnassignedRegardlessOfRole(RoleCode role) throws Exception {
        when(statusService.listStatuses()).thenReturn(FULL_STATUS_LIST);

        mvc.perform(get("/statuses")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principalFor(role), null, List.of(() -> "ROLE_" + role)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='status-unassigned')]").exists());
    }
}
