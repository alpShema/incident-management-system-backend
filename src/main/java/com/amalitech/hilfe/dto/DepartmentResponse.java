package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Department;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Internal department definition used to group incident categories")
public record DepartmentResponse(
        @Schema(description = "Department ID") String id,
        @Schema(description = "Department name") String name,
        @Schema(description = "Department description", nullable = true) String description,
        @Schema(description = "Whether the department is active") Boolean status,
        @Schema(description = "Number of incident categories linked to this department") long categoryCount,
        @Schema(description = "User ID of the department's head, if assigned", nullable = true) String headUserId
) {
    public static DepartmentResponse from(Department department, long categoryCount) {
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getDescription(),
                department.getStatus(),
                categoryCount,
                department.getHeadUserId()
        );
    }
}
