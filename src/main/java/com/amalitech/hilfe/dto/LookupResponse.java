package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Generic id/name pair used for status, severity, and other lookup values")
public record LookupResponse(
        @Schema(description = "UUID of the lookup item") String id,
        @Schema(description = "Display name of the lookup item") String name
) {
    public static LookupResponse from(String id, String name) {
        return new LookupResponse(id, name);
    }
}
