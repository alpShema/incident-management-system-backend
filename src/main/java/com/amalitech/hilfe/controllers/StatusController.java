package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.StatusLookupResponse;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.StatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Statuses", description = "Incident status lookup values")
@RestController
@RequestMapping("/statuses")
@RequiredArgsConstructor
public class StatusController {
    private final StatusService statusService;

    @Operation(summary = "List all statuses", description = "Returns all available incident statuses in lifecycle order: Open → Pending → Resolved → Closed. The Unassigned status is omitted for the AGENT role, since agents never encounter unassigned incidents.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Statuses retrieved")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StatusLookupResponse>>> listStatuses(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Statuses retrieved successfully",
                statusService.listStatuses(parseRoleCode(principal.roleCode()))));
    }

    private RoleCode parseRoleCode(String roleCode) {
        if (roleCode == null) return null;
        try {
            return RoleCode.valueOf(roleCode.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
