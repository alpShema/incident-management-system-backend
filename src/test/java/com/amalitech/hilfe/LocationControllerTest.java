package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.LocationController;
import com.amalitech.hilfe.dto.LocationResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.services.LocationService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocationController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class LocationControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean LocationService locationService;
    @MockitoBean TokenService tokenService;

    private static final UsernamePasswordAuthenticationToken ADMIN_AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of(() -> "location.update"));

    @Test
    void listLocations_returns200WithData() throws Exception {
        LocationResponse loc = new LocationResponse("loc-1", "Accra", null, true, null);
        PageResponse<LocationResponse> page = new PageResponse<>(List.of(loc), 0, 10, 1L, 1, false, false);
        when(locationService.listLocations(isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        mvc.perform(get("/locations")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "user", null, List.of(() -> "ROLE_CLIENT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Locations retrieved successfully"))
                .andExpect(jsonPath("$.data.items[0].name").value("Accra"));
    }

    @Test
    void updateStatus_deactivate_returns200() throws Exception {
        LocationResponse updated = new LocationResponse("loc-1", "Accra", null, false, null);
        when(locationService.updateStatus("loc-1", false)).thenReturn(updated);

        mvc.perform(patch("/locations/loc-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": false}")
                        .with(authentication(ADMIN_AUTH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Location deactivated successfully"))
                .andExpect(jsonPath("$.data.status").value(false));
    }

    @Test
    void updateStatus_activate_returns200() throws Exception {
        LocationResponse updated = new LocationResponse("loc-1", "Accra", null, true, null);
        when(locationService.updateStatus("loc-1", true)).thenReturn(updated);

        mvc.perform(patch("/locations/loc-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": true}")
                        .with(authentication(ADMIN_AUTH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Location activated successfully"))
                .andExpect(jsonPath("$.data.status").value(true));
    }

    @Test
    void updateStatus_returns409WhenAlreadySameStatus() throws Exception {
        when(locationService.updateStatus("loc-1", true))
                .thenThrow(new ArmsAuthException("Location is already active", 409));

        mvc.perform(patch("/locations/loc-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": true}")
                        .with(authentication(ADMIN_AUTH)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatus_returns404WhenNotFound() throws Exception {
        when(locationService.updateStatus("loc-999", false))
                .thenThrow(new ArmsAuthException("Location not found", 404));

        mvc.perform(patch("/locations/loc-999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": false}")
                        .with(authentication(ADMIN_AUTH)))
                .andExpect(status().isNotFound());
    }
}
