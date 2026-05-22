package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agents", description = "View registered agents in the system")
@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService agentService;

    @Operation(
            summary = "List all agents",
            description = "Returns a paginated list of all active agents with their user details. Requires `agent.read` permission."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Agents retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<AgentResponse>>> listAgents(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Agents retrieved successfully", PageResponse.from(agentService.listAgents(pageable))));
    }
}
