package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateIncidentStatusRequest(@NotBlank String statusId) {}
