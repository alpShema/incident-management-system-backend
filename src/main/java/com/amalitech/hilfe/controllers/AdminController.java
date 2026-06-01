package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.AdminResponse;
import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.UpdateAvailabilityRequest;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AdminService;
import com.amalitech.hilfe.services.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admins", description = "Admin management")
@RestController
@RequestMapping("/admins")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @Operation(
            summary = "Update admin availability",
            description = "Allows an admin to toggle their own availability status on or off."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Admin not found")
    })
    @PatchMapping("/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.ADMIN_AVAILABILITY_UPDATE + "')")
    public ResponseEntity<ApiResponse<AdminResponse>> updateStatus(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Valid @RequestBody UpdateAvailabilityRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Status updated",
                adminService.updateAvailability(principal.userId(), request.available())
        ));
    }
}
