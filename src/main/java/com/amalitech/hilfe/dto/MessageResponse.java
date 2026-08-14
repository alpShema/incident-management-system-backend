package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Message;
import com.amalitech.hilfe.models.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "A message on an incident thread")
public record MessageResponse(
        @Schema(description = "Message ID") String id,
        @Schema(description = "Incident this message belongs to") String incidentId,
        @Schema(description = "Sender details") SenderInfo sender,
        @Schema(description = "Message content") String content,
        @Schema(description = "File attachments for this message", nullable = true) List<MediaResponse> attachments,
        @Schema(description = "When the message was sent") Instant createdAt,
        @Schema(description = "When the message was last edited") Instant updatedAt
) {
    @Schema(description = "Sender summary")
    public record SenderInfo(String userId, String fullName, String profileImg) {}

    public static MessageResponse from(Message m) {
        SenderInfo sender = m.getSender() != null
                ? new SenderInfo(m.getSenderId(), m.getSender().getFullName(), m.getSender().getProfileImg())
                : new SenderInfo(m.getSenderId(), null, null);
        return new MessageResponse(m.getId(), m.getIncidentId(), sender, m.getContent(), List.of(), m.getCreatedAt(), m.getUpdatedAt());
    }

    public static MessageResponse from(Message m, User sender) {
        return from(m, sender, m.getContent());
    }

    // Used right after a send, where m.getContent() is whatever was just written for
    // persistence (ciphertext, for a confidential incident) rather than the plaintext the
    // sender typed -- see MessageService#sendMessage. Everywhere else the entity is freshly
    // loaded from the DB (decrypted by EncryptedStringConverter on read), so m.getContent()
    // is already correct and the two-arg overload above is what's used.
    public static MessageResponse from(Message m, User sender, String content) {
        SenderInfo senderInfo = new SenderInfo(
                m.getSenderId(),
                sender != null ? sender.getFullName() : null,
                sender != null ? sender.getProfileImg() : null
        );
        return new MessageResponse(m.getId(), m.getIncidentId(), senderInfo, content, List.of(), m.getCreatedAt(), m.getUpdatedAt());
    }

    public static MessageResponse withAttachments(MessageResponse base, List<MediaResponse> attachments) {
        return new MessageResponse(
                base.id(),
                base.incidentId(),
                base.sender(),
                base.content(),
                attachments,
                base.createdAt(),
                base.updatedAt()
        );
    }
}
