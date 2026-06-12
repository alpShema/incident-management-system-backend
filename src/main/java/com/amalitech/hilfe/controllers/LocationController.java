package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.CreateLocationRequest;
import com.amalitech.hilfe.dto.LocationResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateLocationRequest;
import com.amalitech.hilfe.dto.UpdateLocationStatusRequest;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Locations", description = "Location lifecycle management")
@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private static final String RETRIEVED_MSG = "Location retrieved successfully";
    private static final String CREATED_MSG = "Location created successfully";
    private static final String UPDATED_MSG = "Location updated successfully";
    private static final String ACTIVATED_MSG = "Location activated successfully";
    private static final String DEACTIVATED_MSG = "Location deactivated successfully";

    private final LocationService locationService;

    @Operation(summary = "List locations", description = "Returns a paginated list of locations. Optionally filter by query (searches name and description) or status.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<LocationResponse>>> listLocations(
            @Parameter(description = "Keyword search across name and description") @RequestParam(required = false) String query,
            @Parameter(description = "Filter by active status (true = active, false = inactive)") @RequestParam(required = false) Boolean status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Locations retrieved successfully",
                locationService.listLocations(query, status, pageable)));
    }

    @Operation(summary = "Get location by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LocationResponse>> getLocation(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(RETRIEVED_MSG, locationService.getLocation(id)));
    }

    @Operation(summary = "Create location")
    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.LOCATION_CREATE + "')")
    public ResponseEntity<ApiResponse<LocationResponse>> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(CREATED_MSG, locationService.createLocation(request)));
    }

    @Operation(summary = "Update location")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.LOCATION_UPDATE + "')")
    public ResponseEntity<ApiResponse<LocationResponse>> updateLocation(
            @PathVariable String id,
            @Valid @RequestBody UpdateLocationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(UPDATED_MSG, locationService.updateLocation(id, request)));
    }

    @Operation(summary = "Update location status", description = "Set status to true to activate or false to deactivate.")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.LOCATION_UPDATE + "')")
    public ResponseEntity<ApiResponse<LocationResponse>> updateLocationStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateLocationStatusRequest request
    ) {
        String message = Boolean.TRUE.equals(request.status()) ? ACTIVATED_MSG : DEACTIVATED_MSG;
        return ResponseEntity.ok(ApiResponse.success(message, locationService.updateStatus(id, request.status())));
    }
}
