package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.constants.ApiMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for updating an existing FAQ entry. All fields are optional.")
public record UpdateFaqRequest(
        @Schema(description = "Updated question text", nullable = true)
        @Size(max = 500, message = ApiMessages.QUESTION_MAX_LENGTH)
        String question,

        @Schema(description = "Updated answer text", nullable = true)
        String answer
) {}
