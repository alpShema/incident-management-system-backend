package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to send a message on an incident thread")
public record SendMessageRequest(
        @Schema(description = "Message content", example = "Can you provide more details?")
        @NotBlank(message = "content must not be blank")
        @Size(max = 5000, message = "content must not exceed 5000 characters")
        String content
) {}
