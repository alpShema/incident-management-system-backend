package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.models.ActivityLog;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.repositories.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {
    private final ActivityLogRepository activityLogRepository;

    public Page<ActivityLogResponse> getActivityLogs(Pageable pageable) {
        Pageable sortedPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt")
                );
        return activityLogRepository.findActivityLogResponses(sortedPageable);
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUserRoleChange(
            String actorUserId,
            String targetUserId,
            RoleCode previousRoleCode,
            RoleCode newRoleCode
    ) {
        try {
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .targetUserId(targetUserId)
                    .action("ROLE_CHANGED")
                    .subjectType("USER")
                    .subjectId(targetUserId)
                    .description(buildRoleChangeDescription(targetUserId, previousRoleCode, newRoleCode))
                    .metadata(buildRoleChangeMetadata(previousRoleCode, newRoleCode))
                    .build());
        } catch (RuntimeException exception) {
            log.error("Failed to persist activity log for role change on user {}", targetUserId, exception);
        }
    }

    private String buildRoleChangeDescription(String targetUserId, RoleCode previousRoleCode, RoleCode newRoleCode) {
        return "Changed role for user " + targetUserId + " from " + formatRole(previousRoleCode)
                + " to " + formatRole(newRoleCode);
    }

    private String buildRoleChangeMetadata(RoleCode previousRoleCode, RoleCode newRoleCode) {
        return "{\"previousRoleCode\":\"" + formatRole(previousRoleCode)
                + "\",\"newRoleCode\":\"" + formatRole(newRoleCode) + "\"}";
    }

    private String formatRole(RoleCode roleCode) {
        return roleCode == null ? "null" : roleCode.name();
    }
}
