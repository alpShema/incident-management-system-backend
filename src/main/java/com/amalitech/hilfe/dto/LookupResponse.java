package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Generic id/name pair used for status, severity, and other lookup values")
public record LookupResponse(
        @Schema(description = "Stable ID of the lookup item", example = "status-open") String id,
        @Schema(description = "Display name of the lookup item", example = "Open") String name
) {
    public static LookupResponse from(String id, String name) {
        return new LookupResponse(id, name);
    }
}
