package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Reference to a previously uploaded file attachment")
public record AttachmentRef(
        @Schema(description = "S3 object key returned from the presigned URL endpoint", example = "media/933631a6-75bf-4d5a-b237-aa986ad2dbe6/screenshot.png")
        @NotBlank(message = "Each attachment must include a valid uploaded file reference.")
        String fileKey,

        @Schema(description = "Original file name", example = "screenshot.png")
        @NotBlank(message = "Each attachment must include the original file name.")
        String originalName,

        @Schema(description = "MIME content type", example = "image/png")
        @NotBlank(message = "Each attachment must include a file type.")
        String contentType,

        @Schema(description = "File size in bytes", example = "2048576")
        @NotNull(message = "Each attachment must include a file size.")
        @Positive(message = "Each attachment's file size must be greater than zero.")
        Long fileSize
) {}
