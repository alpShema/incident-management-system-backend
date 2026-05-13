package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Media;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Media attachment details with a presigned download URL")
public record MediaResponse(
        @Schema(description = "Media UUID") String id,
        @Schema(description = "Original file name") String originalName,
        @Schema(description = "MIME content type") String contentType,
        @Schema(description = "File size in bytes") Long fileSize,
        @Schema(description = "Presigned GET URL for downloading/viewing the file") String url
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
