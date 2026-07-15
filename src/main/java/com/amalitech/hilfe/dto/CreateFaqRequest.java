package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.constants.ApiMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for creating a new FAQ entry")
public record CreateFaqRequest(
        @Schema(description = "The question as it would be phrased by a user", example = "How do I reset my password?")
        @NotBlank(message = "Question is required and cannot be blank.")
        @Size(max = 500, message = ApiMessages.QUESTION_MAX_LENGTH)
        String question,

        @Schema(description = "The answer to display to the user")
        @NotBlank(message = "Answer is required and cannot be blank.")
        String answer
) {}
