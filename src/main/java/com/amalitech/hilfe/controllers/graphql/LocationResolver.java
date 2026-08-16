package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.CreateLocationRequest;
import com.amalitech.hilfe.dto.LocationResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateLocationRequest;
import com.amalitech.hilfe.services.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class LocationResolver {

    private final LocationService locationService;

    @QueryMapping
    public PageResponse<LocationResponse> locations(
            @Argument String query,
            @Argument Boolean status,
            @Argument PageInput page) {
        return locationService.listLocations(query, status, PageInput.toPageable(page));
    }

    @QueryMapping
    public LocationResponse location(@Argument String id) {
        return locationService.getLocation(id);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('location.create')")
    public LocationResponse createLocation(@Valid @Argument CreateLocationRequest input) {
        GraphQlResponseMessage.set("Location created successfully");
        return locationService.createLocation(input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('location.update')")
    public LocationResponse updateLocation(@Argument String id, @Valid @Argument UpdateLocationRequest input) {
        GraphQlResponseMessage.set("Location updated successfully");
        return locationService.updateLocation(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('location.update')")
    public LocationResponse updateLocationStatus(@Argument String id, @Valid @Argument UpdateLocationStatusInput input) {
        GraphQlResponseMessage.set(Boolean.TRUE.equals(input.status())
                ? "Location activated successfully"
                : "Location deactivated successfully");
        return locationService.updateStatus(id, input.status());
    }

    public record UpdateLocationStatusInput(Boolean status) {}
}
