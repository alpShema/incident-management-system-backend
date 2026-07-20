package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateAvailabilityRequest;
import com.amalitech.hilfe.services.AgentService;
import com.amalitech.hilfe.services.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class AgentResolver {

    private final AgentService agentService;

    @QueryMapping
    @PreAuthorize("hasAuthority('agent.read')")
    public PageResponse<AgentResponse> agents(
            @Argument String departmentId,
            @Argument String query,
            @Argument("status") Boolean available,
            @Argument String locationId,
            @Argument PageInput page) {
        return PageInput.toPageResponse(
                agentService.listAgents(departmentId, query, available, locationId, PageInput.toPageable(page))
        );
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('agent.read')")
    public PageResponse<AgentResponse> allAgents(
            @Argument String departmentId,
            @Argument String query,
            @Argument("status") Boolean available,
            @Argument String locationId,
            @Argument PageInput page) {
        return PageInput.toPageResponse(
                agentService.listAllAgents(departmentId, query, available, locationId, PageInput.toPageable(page))
        );
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('agent.availability.update')")
    public AgentResponse myAgentStatus(@AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return agentService.getStatus(principal.userId());
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('agent.availability.update')")
    public AgentResponse updateMyAgentStatus(
            @Argument UpdateAvailabilityRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("Agent status updated successfully");
        return agentService.updateAvailability(principal.userId(), input.available());
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('agent.availability.update.any')")
    public AgentResponse updateAgentStatus(@Argument String agentId, @Argument UpdateAvailabilityRequest input) {
        GraphQlResponseMessage.set("Agent status updated successfully");
        return agentService.updateAvailabilityById(agentId, input.available());
    }
}
