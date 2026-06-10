package com.amalitech.hilfe.dto;

public record IncidentFilterParams(
        String statusId,
        String severityId,
        String incidentTypeId,
        String categoryId,
        String locationId
) {}
