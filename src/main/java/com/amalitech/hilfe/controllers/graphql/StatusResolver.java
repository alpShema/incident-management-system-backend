package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.dto.StatusLookupResponse;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.StatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class StatusResolver {

    private final StatusService statusService;

    @QueryMapping
    public List<StatusLookupResponse> statuses(@AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return statusService.listStatuses(parseRoleCode(principal.roleCode()));
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
