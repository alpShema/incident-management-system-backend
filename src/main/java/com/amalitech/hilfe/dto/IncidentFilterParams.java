package com.amalitech.hilfe.dto;

public record IncidentFilterParams(
        String statusId,
        String severityId,
        String incidentTypeId,
        String categoryId,
        String locationId,
        SlaStatus slaStatus,
        Boolean read
) {
    public IncidentFilterParams(String statusId, String severityId, String incidentTypeId, String categoryId, String locationId, SlaStatus slaStatus) {
        this(statusId, severityId, incidentTypeId, categoryId, locationId, slaStatus, null);
    }

    public IncidentFilterParams(String statusId, String severityId, String incidentTypeId, String categoryId, String locationId) {
        this(statusId, severityId, incidentTypeId, categoryId, locationId, null, null);
    }

    public String slaStatusName() {
        return slaStatus != null ? slaStatus.name() : null;
    }
}
