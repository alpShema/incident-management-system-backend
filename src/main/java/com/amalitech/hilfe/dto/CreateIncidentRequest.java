package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateIncidentRequest(
        @NotBlank String title,
        @NotBlank String incidentTypeId,
        @NotBlank String locationId,
        String severityId
) {}
