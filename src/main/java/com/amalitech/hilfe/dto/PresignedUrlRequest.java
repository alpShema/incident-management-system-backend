package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request body for generating an S3 presigned upload URL")
public record PresignedUrlRequest(
        @Schema(description = "Original file name", example = "screenshot.png")
        @NotBlank String fileName,

        @Schema(description = "MIME content type of the file", example = "image/png")
        @NotBlank String contentType,

        @Schema(description = "File size in bytes", example = "2048576")
        @NotNull @Positive Long fileSize
) {}
