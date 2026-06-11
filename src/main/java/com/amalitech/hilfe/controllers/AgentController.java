package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateAvailabilityRequest;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AgentService;
import com.amalitech.hilfe.services.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Agents", description = "View registered agents in the system")
@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService agentService;

    @Operation(
            summary = "List all agents",
            description = "Returns a paginated list of agents for operational use. "
                    + "Accepts an optional `query` keyword that searches across full name, email, and office location. "
                    + "When `departmentId` is omitted, returns active agents only. "
                    + "When `departmentId` is provided, filters by agent-group membership under that department and includes both active and inactive agents. "
                    + "Both `query` and `departmentId` can be supplied together to narrow results simultaneously. "
                    + "If the department is missing or inactive, returns an empty page. "
                    + "Requires `agent.read` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Agents retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @GetMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<AgentResponse>>> listAgents(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String departmentId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Agents retrieved successfully",
                PageResponse.from(agentService.listAgents(departmentId, query, pageable))));
    }

    @Operation(
            summary = "List all agents regardless of status",
            description = "Returns a paginated list of agents including both active and inactive records. "
                    + "Accepts an optional `query` keyword that searches across full name, email, and office location. "
                    + "When `departmentId` is omitted, all agents are returned regardless of status. "
                    + "When `departmentId` is provided, filters by agent-group membership under that department and includes both active and inactive agents. "
                    + "Both `query` and `departmentId` can be supplied together to narrow results simultaneously. "
                    + "If the department is missing or inactive, returns an empty page. "
                    + "Requires `agent.read` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Agents retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<AgentResponse>>> listAllAgents(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String departmentId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Agents retrieved successfully",
                PageResponse.from(agentService.listAllAgents(departmentId, query, pageable))));
    }

    @Operation(
            summary = "Get agent availability status",
            description = "Returns the current availability status of the logged-in agent."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Agent not found")
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_AVAILABILITY_UPDATE + "')")
    public ResponseEntity<ApiResponse<AgentResponse>> getStatus(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Status retrieved",
                agentService.getStatus(principal.userId())
        ));
    }

    @Operation(
            summary = "Update agent availability",
            description = "Allows an agent to toggle their availability status on or off."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Agent not found")
    @PatchMapping("/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_AVAILABILITY_UPDATE + "')")
    public ResponseEntity<ApiResponse<AgentResponse>> updateStatus(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Valid @RequestBody UpdateAvailabilityRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Status updated",
                agentService.updateAvailability(principal.userId(), request.available())
        ));
    }

    @Operation(
            summary = "Update agent availability (admin)",
            description = "Allows an admin to toggle any agent's availability status on or off."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Agent not found")
    @PatchMapping("/{agentId}/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_AVAILABILITY_UPDATE_ANY + "')")
    public ResponseEntity<ApiResponse<AgentResponse>> updateAgentStatus(
            @PathVariable String agentId,
            @Valid @RequestBody UpdateAvailabilityRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Status updated",
                agentService.updateAvailabilityById(agentId, request.available())
        ));
    }
}
