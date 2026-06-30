package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class AdminResolver {

    private final AdminService adminService;

    @QueryMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_READ + "')")
    public AgentResponse adminAgentAccess(@Argument String userId) {
        return adminService.getAgentAccess(userId);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_CREATE + "')")
    public AgentResponse grantAdminAgentAccess(@Argument String userId) {
        return adminService.grantAgentAccess(userId);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_CREATE + "')")
    public AgentResponse revokeAdminAgentAccess(@Argument String userId) {
        return adminService.revokeAgentAccess(userId);
    }
}
