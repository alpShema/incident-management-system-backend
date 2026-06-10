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

    private final LocationService locationService;

    @Operation(summary = "List locations", description = "Returns a paginated list of locations. Optionally filter by name or status.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<LocationResponse>>> listLocations(
            @Parameter(description = "Filter by name (partial match)") @RequestParam(required = false) String name,
            @Parameter(description = "Filter by active status (true = active, false = inactive)") @RequestParam(required = false) Boolean status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Locations retrieved successfully",
                locationService.listLocations(name, status, pageable)
        ));
    }

    @Operation(summary = "Get location by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LocationResponse>> getLocation(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Location retrieved successfully", locationService.getLocation(id)));
    }

    @Operation(summary = "Create location")
    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.LOCATION_CREATE + "')")
    public ResponseEntity<ApiResponse<LocationResponse>> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Location created successfully", locationService.createLocation(request)));
    }

    @Operation(summary = "Update location")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.LOCATION_UPDATE + "')")
    public ResponseEntity<ApiResponse<LocationResponse>> updateLocation(
            @PathVariable String id,
            @Valid @RequestBody UpdateLocationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Location updated successfully", locationService.updateLocation(id, request)));
    }

    @Operation(summary = "Update location status", description = "Set status to true to activate or false to deactivate.")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.LOCATION_UPDATE + "')")
    public ResponseEntity<ApiResponse<LocationResponse>> updateLocationStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateLocationStatusRequest request
    ) {
        String message = Boolean.TRUE.equals(request.status()) ? "Location activated successfully" : "Location deactivated successfully";
        return ResponseEntity.ok(ApiResponse.success(message, locationService.updateStatus(id, request.status())));
    }
}
