package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for partially updating an internal department. Only provided fields are "
        + "updated; fields omitted from the request are left at their current stored value. At least one "
        + "field must be supplied.")
public record DepartmentRequest(
        @Schema(description = "Updated department name. Omit to leave the current name unchanged.",
                nullable = true, example = "Facilities")
        @Size(max = 100, message = "Department name must not exceed 100 characters.")
        String name,

        @Schema(description = "Updated department description. Omit to leave the current description unchanged.",
                nullable = true)
        @Size(max = 1000, message = "Department description must not exceed 1000 characters.")
        String description,

        @Schema(description = "User ID of the department's head. Must be an active Admin or Admin-Agent. "
                + "Omit to leave the current head unchanged.", nullable = true)
        String headUserId
) {
    @AssertTrue(message = "Department name cannot be blank.")
    private boolean isNameValidIfProvided() {
        return name == null || !name.isBlank();
    }

    @AssertTrue(message = "At least one field (name, description, or headUserId) must be provided to update the department.")
    private boolean isAtLeastOneFieldPresent() {
        return name != null || description != null || headUserId != null;
    }
}
