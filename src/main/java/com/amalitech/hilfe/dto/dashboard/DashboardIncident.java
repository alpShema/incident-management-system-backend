package com.amalitech.hilfe.dto.dashboard;

import com.amalitech.hilfe.models.Incident;

public record DashboardIncident(
        String id,
        int incidentNo,
        String title,
        String status,
        String severity,
        String updatedAt
) {
    public static DashboardIncident from(Incident i) {
        return new DashboardIncident(
                i.getId(),
                i.getIncidentNo(),
                i.getTitle(),
                i.getStatus() != null ? i.getStatus().getName() : null,
                i.getSeverity() != null ? i.getSeverity().getName() : null,
                i.getUpdatedAt().toString()
        );
    }
}
