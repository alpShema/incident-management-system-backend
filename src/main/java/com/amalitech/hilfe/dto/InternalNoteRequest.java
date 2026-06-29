package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InternalNoteRequest(
        @NotBlank @Size(max = 5000) String body
) {}
