package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A single parsed row from an inspected FAQ bulk-upload CSV")
public record FaqInspectionRow(
        @Schema(description = "1-based row number in the CSV, including the header row") int row,
        @Schema(description = "Question text, null if missing", nullable = true) String question,
        @Schema(description = "Answer text, null if missing", nullable = true) String answer,
        @Schema(description = "Whether the question column is blank") boolean questionMissing,
        @Schema(description = "Whether the answer column is blank") boolean answerMissing,
        @Schema(description = "READY if the row can be imported as-is, NEEDS_ATTENTION otherwise") FaqRowStatus status
) {
}
