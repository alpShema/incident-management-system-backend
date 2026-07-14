package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ActivityResolver {

    private final ActivityLogService activityLogService;

    @QueryMapping
    public PageResponse<ActivityLogResponse> activities(
            @Argument String incidentId,
            @Argument PageInput page,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return PageInput.toPageResponse(
                activityLogService.getActivityLogs(incidentId, PageInput.toPageable(page),
                        principal.userId(), principal.roleCode())
        );
    }
}
