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
    void reactivateLocation_returns200WhenInactive() throws Exception {
        LocationResponse reactivated = new LocationResponse("loc-1", "Accra", null, true, null);
        when(locationService.reactivateLocation(eq("loc-1"))).thenReturn(reactivated);

        mvc.perform(patch("/locations/loc-1/reactivate")
                        .with(authentication(ADMIN_AUTH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Location reactivated successfully"))
                .andExpect(jsonPath("$.data.status").value(true));
    }

    @Test
    void reactivateLocation_returns409WhenAlreadyActive() throws Exception {
        when(locationService.reactivateLocation(eq("loc-1")))
                .thenThrow(new ArmsAuthException("Location is already active", 409));

        mvc.perform(patch("/locations/loc-1/reactivate")
                        .with(authentication(ADMIN_AUTH)))
                .andExpect(status().isConflict());
    }

    @Test
    void reactivateLocation_returns404WhenNotFound() throws Exception {
        when(locationService.reactivateLocation(eq("loc-999")))
                .thenThrow(new ArmsAuthException("Location not found", 404));

        mvc.perform(patch("/locations/loc-999/reactivate")
                        .with(authentication(ADMIN_AUTH)))
                .andExpect(status().isNotFound());
    }
}
