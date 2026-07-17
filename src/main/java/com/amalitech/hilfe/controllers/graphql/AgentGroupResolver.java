package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.AddAgentGroupMemberRequest;
import com.amalitech.hilfe.dto.AgentGroupMemberResponse;
import com.amalitech.hilfe.dto.AgentGroupRequest;
import com.amalitech.hilfe.dto.AgentGroupResponse;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.services.AgentGroupService;
import com.amalitech.hilfe.services.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AgentGroupResolver {

    private final AgentGroupService agentGroupService;

    @QueryMapping
    @PreAuthorize("hasAuthority('agent-group.read')")
    public PageResponse<AgentGroupResponse> agentGroups(
            @Argument String query,
            @Argument String departmentId,
            @Argument PageInput page) {
        return PageInput.toPageResponse(
                agentGroupService.listAgentGroups(query, departmentId, PageInput.toPageable(page))
        );
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('agent-group.read')")
    public PageResponse<AgentGroupResponse> allAgentGroups(
            @Argument Boolean status,
            @Argument String query,
            @Argument String departmentId,
            @Argument PageInput page) {
        return PageInput.toPageResponse(
                agentGroupService.listAllAgentGroups(status, query, departmentId, PageInput.toPageable(page))
        );
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('agent-group.read')")
    public AgentGroupResponse agentGroup(@Argument String id) {
        return agentGroupService.getAgentGroup(id);
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('agent-group.read')")
    public List<AgentGroupMemberResponse> agentGroupMembers(@Argument String id) {
        return agentGroupService.listMembers(id);
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('agent-group.read')")
    public List<LookupResponse> agentGroupsByCategory(@Argument String categoryId) {
        return agentGroupService.listAgentGroupsByCategory(categoryId);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('agent-group.create')")
    public AgentGroupResponse createAgentGroup(@Argument AgentGroupRequest input) {
        GraphQlResponseMessage.set("Agent group created successfully");
        return agentGroupService.createAgentGroup(input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('agent-group.update')")
    public AgentGroupResponse updateAgentGroup(@Argument String id, @Argument AgentGroupRequest input) {
        GraphQlResponseMessage.set("Agent group updated successfully");
        return agentGroupService.updateAgentGroup(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('agent-group.delete')")
    public AgentGroupResponse updateAgentGroupStatus(
            @Argument String id,
            @Argument UpdateAgentGroupStatusInput input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("Agent group status updated successfully");
        return agentGroupService.updateAgentGroupStatus(principal.userId(), id, input.status());
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('agent-group.update')")
    public AgentGroupMemberResponse addAgentGroupMember(@Argument String id, @Argument AddAgentGroupMemberRequest input) {
        GraphQlResponseMessage.set("Agent group member added successfully");
        return agentGroupService.addMember(id, input.agentId());
    }

    public record UpdateAgentGroupStatusInput(Boolean status) {}
}
