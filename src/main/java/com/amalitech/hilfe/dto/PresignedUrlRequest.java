package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request body for generating an S3 presigned upload URL")
public record PresignedUrlRequest(
        @Schema(description = "Original file name", example = "screenshot.png")
        @NotBlank(message = "File name is required and cannot be blank.")
        String fileName,

        @Schema(description = "MIME content type of the file", example = "image/png")
        @NotBlank(message = "File type is required and cannot be blank.")
        String contentType,

        @Schema(description = "File size in bytes", example = "2048576")
        @NotNull(message = "File size is required.")
        @Positive(message = "File size must be greater than zero.")
        Long fileSize
) {}
