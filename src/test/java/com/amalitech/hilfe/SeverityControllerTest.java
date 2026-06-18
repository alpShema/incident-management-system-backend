package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.SeverityController;
import com.amalitech.hilfe.dto.SeverityResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.services.SeverityService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeverityController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class SeverityControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SeverityService severityService;
    @MockitoBean TokenService tokenService;

    @Test
    void listSeverities_returns200WithData() throws Exception {
        when(severityService.listSeverities()).thenReturn(List.of(
                new SeverityResponse("sev-1", "Low", "Low priority", true, 480, 4320, 28800L, 259200L, Instant.now(), Instant.now()),
                new SeverityResponse("sev-2", "High", "High priority", true, 60, 480, 3600L, 28800L, Instant.now(), Instant.now())
        ));

        mvc.perform(get("/severities")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "user", null, List.of(() -> "ROLE_CLIENT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Severities retrieved successfully"))
                .andExpect(jsonPath("$.data[0].name").value("Low"))
                .andExpect(jsonPath("$.data[0].description").value("Low priority"))
                .andExpect(jsonPath("$.data[0].status").value(true))
                .andExpect(jsonPath("$.data[0].responseTimeMinutes").value(480))
                .andExpect(jsonPath("$.data[1].name").value("High"));
    }

    @Test
    void updateSeveritySla_emptyBody_returns400() throws Exception {
        mvc.perform(patch("/severities/sev-1/sla")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "user", null, List.of(() -> "severity.update")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSeveritySla_belowMinimumValue_returns400() throws Exception {
        mvc.perform(patch("/severities/sev-1/sla")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseTimeMinutes\": 0}")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "user", null, List.of(() -> "severity.update")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSeveritySla_validPartialBody_returns200() throws Exception {
        when(severityService.updateSeveritySla(
                org.mockito.ArgumentMatchers.eq("sev-1"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SeverityResponse("sev-1", "Low", "Low priority", true, 30, 480, 1800L, 28800L, Instant.now(), Instant.now()));

        mvc.perform(patch("/severities/sev-1/sla")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseTimeMinutes\": 30}")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "user", null, List.of(() -> "severity.update")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Severity SLA updated"));
    }
}
