package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request to send a message on an incident thread. Provide text, attachments, or both.")
public record SendMessageRequest(
        @Schema(description = "Message content. Optional when attachments are provided.", example = "Can you provide more details?")
        @Size(max = 5000, message = "Your message must not exceed 5000 characters.")
        String content,

        @Schema(description = "Optional file attachments uploaded through /messages/presigned-url")
        @Valid
        List<AttachmentRef> attachments
) {}
