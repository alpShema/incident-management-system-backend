package com.amalitech.hilfe.dto;

import java.time.Instant;

public record InternalNoteResponse(
        String id,
        String incidentId,
        String body,
        AuthorInfo author,
        boolean isOwner,
        Instant createdAt,
        Instant updatedAt
) {
    public record AuthorInfo(String userId, String fullName, String profileImg) {}
}
