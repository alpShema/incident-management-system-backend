package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.LocationResponse;
import com.amalitech.hilfe.services.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Locations", description = "Location lookup values used when reporting incidents")
@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {
    private final LocationService locationService;

    @Operation(summary = "List all locations", description = "Returns all available locations that can be associated with an incident.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Locations retrieved")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<LocationResponse>>> listLocations() {
        return ResponseEntity.ok(ApiResponse.success("Locations retrieved successfully", locationService.listLocations()));
    }
}
