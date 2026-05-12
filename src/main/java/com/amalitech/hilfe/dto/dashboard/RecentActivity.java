package com.amalitech.hilfe.dto.dashboard;

public record RecentActivity(
        String actorUserId,
        String action,
        String subjectType,
        String subjectId,
        String description,
        String createdAt
) {}
