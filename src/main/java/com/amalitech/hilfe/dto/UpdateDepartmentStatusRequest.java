package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body for activating or deactivating a department")
public record UpdateDepartmentStatusRequest(
        @Schema(description = "Target department status. true = active, false = inactive", example = "false")
        @NotNull(message = "Please specify whether the department should be active or inactive.")
        Boolean status
) {}
