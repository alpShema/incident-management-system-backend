package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.NotBlank;

public record SeverityRequest(
        @NotBlank(message = "Name is required") String name,
        String description
) {}
