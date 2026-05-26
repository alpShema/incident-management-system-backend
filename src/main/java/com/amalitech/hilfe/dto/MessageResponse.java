package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Message;
import com.amalitech.hilfe.models.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A message on an incident thread")
public record MessageResponse(
        @Schema(description = "Message ID") String id,
        @Schema(description = "Incident this message belongs to") String incidentId,
        @Schema(description = "Sender details") SenderInfo sender,
        @Schema(description = "Message content") String content,
        @Schema(description = "When the message was sent") Instant createdAt,
        @Schema(description = "When the message was last edited") Instant updatedAt
) {
    @Schema(description = "Sender summary")
    public record SenderInfo(String userId, String fullName, String profileImg) {}

    public static MessageResponse from(Message m) {
        SenderInfo sender = m.getSender() != null
                ? new SenderInfo(m.getSenderId(), m.getSender().getFullName(), m.getSender().getProfileImg())
                : new SenderInfo(m.getSenderId(), null, null);
        return new MessageResponse(m.getId(), m.getIncidentId(), sender, m.getContent(), m.getCreatedAt(), m.getUpdatedAt());
    }

    public static MessageResponse from(Message m, User sender) {
        SenderInfo senderInfo = new SenderInfo(
                m.getSenderId(),
                sender != null ? sender.getFullName() : null,
                sender != null ? sender.getProfileImg() : null
        );
        return new MessageResponse(m.getId(), m.getIncidentId(), senderInfo, m.getContent(), m.getCreatedAt(), m.getUpdatedAt());
    }
}
