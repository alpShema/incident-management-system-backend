package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.SystemConfigController;
import com.amalitech.hilfe.dto.SlaConfigResponse;
import com.amalitech.hilfe.dto.TimezoneOptionResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.services.AutoCloseService;
import com.amalitech.hilfe.services.SlaService;
import com.amalitech.hilfe.services.TimezoneService;
import com.amalitech.hilfe.services.TokenService;
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

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemConfigController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class SystemConfigControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AutoCloseService autoCloseService;
    @MockitoBean SlaService slaService;
    @MockitoBean TimezoneService timezoneService;
    @MockitoBean TokenService tokenService;

    @Test
    void getSlaConfig_returns200() throws Exception {
        when(slaService.getConfig()).thenReturn(new SlaConfigResponse(20));

        mvc.perform(get("/config/sla")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "admin", null, List.of(() -> "system.config.read")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.atRiskPct").value(20));
    }

    @Test
    void updateSlaConfig_returns200() throws Exception {
        when(slaService.updateConfig(new com.amalitech.hilfe.dto.UpdateSlaConfigRequest(25)))
                .thenReturn(new SlaConfigResponse(25));

        mvc.perform(patch("/config/sla")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"atRiskPct\":25}")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "admin", null, List.of(() -> "system.config.update")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.atRiskPct").value(25));
    }

    @Test
    void listTimezones_returns200WithData_noPermissionRequired() throws Exception {
        when(timezoneService.listTimezones(null)).thenReturn(List.of(
                new TimezoneOptionResponse("Africa/Kigali", "Kigali", "+02:00")));

        mvc.perform(get("/config/timezones")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "user", null, List.of(() -> "ROLE_CLIENT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Timezones retrieved successfully"))
                .andExpect(jsonPath("$.data[0].id").value("Africa/Kigali"))
                .andExpect(jsonPath("$.data[0].label").value("Kigali"))
                .andExpect(jsonPath("$.data[0].offsetNow").value("+02:00"));
    }

    @Test
    void listTimezones_withQueryParam_passesSearchTermToService() throws Exception {
        when(timezoneService.listTimezones("kigali")).thenReturn(List.of(
                new TimezoneOptionResponse("Africa/Kigali", "Kigali", "+02:00")));

        mvc.perform(get("/config/timezones").param("q", "kigali")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "user", null, List.of(() -> "ROLE_CLIENT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("Africa/Kigali"));
    }
}
