package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.IncidentType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An incident topic (type) with its parent category")
public record IncidentTopicResponse(
        @Schema(description = "Stable topic ID", example = "type-account-issues") String id,
        @Schema(description = "Topic display name", example = "Account Issues") String name,
        @Schema(description = "Topic description", example = "Login problems, password resets, account access") String description,
        @Schema(description = "Parent category (id + name)", nullable = true) LookupResponse category
) {
    public static IncidentTopicResponse from(IncidentType type) {
        LookupResponse category = type.getCategory() != null
                ? LookupResponse.from(type.getCategory().getId(), type.getCategory().getName())
                : null;
        return new IncidentTopicResponse(type.getId(), type.getName(), type.getDescription(), category);
    }
}
