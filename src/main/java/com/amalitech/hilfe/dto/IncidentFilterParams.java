package com.amalitech.hilfe.dto;

public record IncidentFilterParams(
        String statusId,
        String severityId,
        String incidentTypeId,
        String categoryId,
        String locationId,
        SlaStatus slaStatus
) {
    public IncidentFilterParams(String statusId, String severityId, String incidentTypeId, String categoryId, String locationId) {
        this(statusId, severityId, incidentTypeId, categoryId, locationId, null);
    }

    public String slaStatusName() {
        return slaStatus != null ? slaStatus.name() : null;
    }
}
