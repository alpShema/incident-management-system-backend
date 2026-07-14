package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aggregate counts across every row in an inspected FAQ bulk-upload CSV")
public record FaqInspectionSummary(
        @Schema(description = "Total number of data rows parsed from the CSV") int totalRows,
        @Schema(description = "Rows with both a question and an answer, ready to import") int readyCount,
        @Schema(description = "Rows missing a question and/or answer, skipped on import") int needsAttentionCount
) {
}
