package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Media;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Media attachment details with a presigned download URL")
public record MediaResponse(
        @Schema(description = "Media attachment ID", example = "ff4ce3b0-9fb3-4ec6-b7ab-3e2d163bdd58") String id,
        @Schema(description = "Original file name", example = "screenshot.png") String originalName,
        @Schema(description = "MIME content type", example = "image/png") String contentType,
        @Schema(description = "File size in bytes", example = "2048576") Long fileSize,
        @Schema(description = "Presigned GET URL for downloading/viewing the file", example = "https://hilfe-v2-media-testing.s3.eu-west-1.amazonaws.com/media/933631a6-75bf-4d5a-b237-aa986ad2dbe6/screenshot.png?X-Amz-Algorithm=AWS4-HMAC-SHA256") String url
) {
    public static MediaResponse from(Media media, String presignedGetUrl) {
        return new MediaResponse(
                media.getId(),
                media.getOriginalName(),
                media.getContentType(),
                media.getFileSize(),
                presignedGetUrl
        );
    }
}
