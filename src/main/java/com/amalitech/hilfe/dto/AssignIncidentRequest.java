package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignIncidentRequest(@NotBlank String agentId) {}
