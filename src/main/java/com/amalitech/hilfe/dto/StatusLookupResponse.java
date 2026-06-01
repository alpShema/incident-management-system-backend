package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Incident status lookup item")
public record StatusLookupResponse(
        @Schema(description = "Stable status ID", example = "status-open") String id,
        @Schema(description = "Display name of the status", example = "Open") String name,
        @Schema(description = "Description of what the status means", example = "Incident is newly created and awaiting assignment", nullable = true) String description
) {
    public static StatusLookupResponse from(String id, String name, String description) {
        return new StatusLookupResponse(id, name, description);
    }
}
