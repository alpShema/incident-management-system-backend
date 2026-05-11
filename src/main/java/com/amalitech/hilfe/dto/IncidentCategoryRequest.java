package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.NotBlank;

public record IncidentCategoryRequest(
        @NotBlank String name,
        String description
) {}
