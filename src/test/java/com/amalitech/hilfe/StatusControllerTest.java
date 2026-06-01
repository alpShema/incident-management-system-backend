package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.StatusController;
import com.amalitech.hilfe.dto.StatusLookupResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.services.StatusService;
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

@WebMvcTest(StatusController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class StatusControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean StatusService statusService;
    @MockitoBean TokenService tokenService;

    @Test
    void listStatuses_returns200WithData() throws Exception {
        when(statusService.listStatuses()).thenReturn(List.of(
                new StatusLookupResponse("status-1", "Open", "Open status"),
                new StatusLookupResponse("status-2", "Pending", "Pending status"),
                new StatusLookupResponse("status-3", "Resolved", "Resolved status"),
                new StatusLookupResponse("status-4", "Closed", "Closed status")
        ));

        mvc.perform(get("/statuses")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "user", null, List.of(() -> "ROLE_CLIENT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Statuses retrieved successfully"))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].name").value("Open"))
                .andExpect(jsonPath("$.data[0].description").value("Open status"));
    }
}
