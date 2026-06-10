package com.amalitech.hilfe.dto;

import java.time.Instant;

public record IncidentDateFilter(Instant fromDate, Instant toDate) {}
