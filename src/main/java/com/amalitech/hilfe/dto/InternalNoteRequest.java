package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InternalNoteRequest(
        @NotBlank(message = "Note text is required and cannot be blank.")
        @Size(max = 5000, message = "Note text must not exceed 5000 characters.")
        String body
) {}
