package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Reference to a previously uploaded file attachment")
public record AttachmentRef(
        @Schema(description = "S3 object key returned from the presigned URL endpoint")
        @NotBlank String fileKey,

        @Schema(description = "Original file name", example = "screenshot.png")
        @NotBlank String originalName,

        @Schema(description = "MIME content type", example = "image/png")
        @NotBlank String contentType,

        @Schema(description = "File size in bytes", example = "2048576")
        @NotNull @Positive Long fileSize
) {}
