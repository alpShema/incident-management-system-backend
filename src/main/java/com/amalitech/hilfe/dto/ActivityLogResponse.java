package com.amalitech.hilfe.dto;

import java.time.Instant;

public record ActivityLogResponse(
        Long id,
        String actorUserId,
        String targetUserId,
        String action,
        String subjectType,
        String subjectId,
        String description,
        String metadata,
        Instant createdAt
) {
}
