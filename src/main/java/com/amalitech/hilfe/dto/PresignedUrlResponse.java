package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Presigned URL details for uploading a file to S3")
public record PresignedUrlResponse(
        @Schema(description = "Presigned PUT URL — upload the file directly to this URL") String uploadUrl,
        @Schema(description = "S3 object key — include this in the incident creation request") String fileKey,
        @Schema(description = "URL expiration time in seconds") long expiresInSeconds
) {}
