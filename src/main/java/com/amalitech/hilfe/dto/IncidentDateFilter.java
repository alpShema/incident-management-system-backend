package com.amalitech.hilfe.dto;

import java.time.Instant;

public record IncidentDateFilter(Instant fromDate, boolean filterFrom, Instant toDate, boolean filterTo) {
    public IncidentDateFilter(Instant fromDate, Instant toDate) {
        this(fromDate, fromDate != null, toDate, toDate != null);
    }
}
