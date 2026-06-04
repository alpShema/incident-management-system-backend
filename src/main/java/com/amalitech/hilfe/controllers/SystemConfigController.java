package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.AutoCloseConfigResponse;
import com.amalitech.hilfe.dto.SlaConfigResponse;
import com.amalitech.hilfe.dto.UpdateAutoCloseConfigRequest;
import com.amalitech.hilfe.dto.UpdateSlaConfigRequest;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AutoCloseService;
import com.amalitech.hilfe.services.SlaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System Config", description = "Admin-managed global system settings")
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final AutoCloseService autoCloseService;
    private final SlaService slaService;

    @Operation(
        summary = "Get auto-close configuration",
        description = "Returns the current global auto-close duration. Requires `system.config.read` permission."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Config retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @GetMapping("/auto-close")
    @PreAuthorize("hasAuthority('" + RbacPermissions.SYSTEM_CONFIG_READ + "')")
    public ResponseEntity<ApiResponse<AutoCloseConfigResponse>> getAutoCloseConfig() {
        return ResponseEntity.ok(ApiResponse.success(
                "Auto-close configuration retrieved successfully",
                autoCloseService.getConfig()));
    }

    @Operation(
        summary = "Update auto-close configuration",
        description = "Sets the number of hours after resolution before the system auto-closes an incident. Applies globally. Requires `system.config.update` permission."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Config updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid duration value"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PatchMapping("/auto-close")
    @PreAuthorize("hasAuthority('" + RbacPermissions.SYSTEM_CONFIG_UPDATE + "')")
    public ResponseEntity<ApiResponse<AutoCloseConfigResponse>> updateAutoCloseConfig(
            @Valid @RequestBody UpdateAutoCloseConfigRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Auto-close configuration updated successfully",
                autoCloseService.updateConfig(request.durationHours())));
    }

    @Operation(
        summary = "Get SLA configuration",
        description = "Returns the global SLA at-risk percentage. Requires `system.config.read` permission."
    )
    @GetMapping("/sla")
    @PreAuthorize("hasAuthority('" + RbacPermissions.SYSTEM_CONFIG_READ + "')")
    public ResponseEntity<ApiResponse<SlaConfigResponse>> getSlaConfig() {
        return ResponseEntity.ok(ApiResponse.success(
                "SLA configuration retrieved successfully",
                slaService.getConfig()));
    }

    @Operation(
        summary = "Update SLA configuration",
        description = "Updates the global SLA at-risk percentage. Requires `system.config.update` permission."
    )
    @PatchMapping("/sla")
    @PreAuthorize("hasAuthority('" + RbacPermissions.SYSTEM_CONFIG_UPDATE + "')")
    public ResponseEntity<ApiResponse<SlaConfigResponse>> updateSlaConfig(
            @Valid @RequestBody UpdateSlaConfigRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "SLA configuration updated successfully",
                slaService.updateConfig(request)));
    }
}
