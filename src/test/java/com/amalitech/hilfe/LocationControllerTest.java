package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.LocationController;
import com.amalitech.hilfe.dto.LocationResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.services.LocationService;
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

@WebMvcTest(LocationController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class LocationControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean LocationService locationService;
    @MockitoBean TokenService tokenService;

    @Test
    void listLocations_returns200WithData() throws Exception {
        when(locationService.listLocations()).thenReturn(List.of(
                new LocationResponse("loc-1", "Accra")
        ));

        mvc.perform(get("/locations")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "user", null, List.of(() -> "ROLE_CLIENT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Locations retrieved successfully"))
                .andExpect(jsonPath("$.data[0].name").value("Accra"));
    }
}
