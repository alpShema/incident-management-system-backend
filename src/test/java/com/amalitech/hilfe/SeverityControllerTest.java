package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.SeverityController;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.services.SeverityService;
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

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                new LookupResponse("sev-1", "Low"),
                new LookupResponse("sev-2", "High")
        ));

        mvc.perform(get("/severities")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "user", null, List.of(() -> "ROLE_CLIENT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Severities retrieved successfully"))
                .andExpect(jsonPath("$.data[0].name").value("Low"))
                .andExpect(jsonPath("$.data[1].name").value("High"));
    }
}
