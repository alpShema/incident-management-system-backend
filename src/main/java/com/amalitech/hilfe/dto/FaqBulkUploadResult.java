package com.amalitech.hilfe.dto;

import java.util.List;

public record FaqBulkUploadResult(
        int created,
        int failed,
        List<RowError> errors
) {
    public record RowError(int row, String reason) {}
}
