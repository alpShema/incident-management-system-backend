package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Preview of a FAQ bulk-upload CSV, computed without creating or modifying any FAQs")
public record FaqInspectionResult(
        FaqInspectionSummary summary,
        PageResponse<FaqInspectionRow> rows
) {
}
