package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.CreateLocationRequest;
import com.amalitech.hilfe.dto.LocationResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateLocationRequest;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Locations retrieved")
    })
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
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Location found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Location not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LocationResponse>> getLocation(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Location retrieved successfully", locationService.getLocation(id)));
    }

    @Operation(summary = "Create location")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Location created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Name already exists")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.LOCATION_CREATE + "')")
    public ResponseEntity<ApiResponse<LocationResponse>> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Location created successfully", locationService.createLocation(request)));
    }

    @Operation(summary = "Update location")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Location updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Location not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Name already exists")
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.LOCATION_UPDATE + "')")
    public ResponseEntity<ApiResponse<LocationResponse>> updateLocation(
            @PathVariable String id,
            @Valid @RequestBody UpdateLocationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Location updated successfully", locationService.updateLocation(id, request)));
    }

    @Operation(summary = "Deactivate location", description = "Marks a location as inactive without deleting it.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Location deactivated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Location not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Already inactive")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.LOCATION_DELETE + "')")
    public ResponseEntity<ApiResponse<LocationResponse>> deactivateLocation(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Location deactivated successfully", locationService.deactivateLocation(id)));
    }
}
