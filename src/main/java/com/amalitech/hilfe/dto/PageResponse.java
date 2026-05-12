package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Paginated response wrapper")
public record PageResponse<T>(
        @Schema(description = "Items on the current page") List<T> items,
        @Schema(description = "Current page number (0-based)", example = "0") int page,
        @Schema(description = "Number of items requested per page", example = "20") int size,
        @Schema(description = "Total number of items across all pages", example = "153") long totalElements,
        @Schema(description = "Total number of pages", example = "8") int totalPages,
        @Schema(description = "Whether there is a next page") boolean hasNext,
        @Schema(description = "Whether there is a previous page") boolean hasPrevious
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
