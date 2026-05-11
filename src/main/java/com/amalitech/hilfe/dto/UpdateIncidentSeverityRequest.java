package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateIncidentSeverityRequest(@NotBlank String severityId) {}
