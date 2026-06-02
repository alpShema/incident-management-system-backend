package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AutoCloseConfigResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.SystemConfig;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.StatusRepository;
import com.amalitech.hilfe.repositories.SystemConfigRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoCloseService {

    public static final String AUTO_CLOSE_HOURS_KEY     = "auto_close_hours";
    public static final int    DEFAULT_AUTO_CLOSE_HOURS = 72;

    private final SystemConfigRepository systemConfigRepository;
    private final IncidentRepository incidentRepository;
    private final StatusRepository statusRepository;
    private final AgentRepository agentRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    public AutoCloseConfigResponse getConfig() {
        return new AutoCloseConfigResponse(readDurationHours());
    }

    @Transactional
    public AutoCloseConfigResponse updateConfig(int durationHours) {
        SystemConfig config = systemConfigRepository.findById(AUTO_CLOSE_HOURS_KEY)
                .orElse(SystemConfig.builder().key(AUTO_CLOSE_HOURS_KEY).build());
        config.setValue(String.valueOf(durationHours));
        systemConfigRepository.save(config);
        return new AutoCloseConfigResponse(durationHours);
    }

    @Transactional
    public void autoCloseResolvedIncidents() {
        int hours = readDurationHours();
        Instant cutoff = Instant.now().minus(hours, ChronoUnit.HOURS);

        List<Incident> overdue = incidentRepository.findOverdueResolved(cutoff);
        if (overdue.isEmpty()) return;

        String closedStatusId = statusRepository.findByNameIgnoreCase("Closed")
                .orElseThrow(() -> new ArmsAuthException("Default 'Closed' status not configured", 500))
                .getId();

        for (Incident incident : overdue) {
            incident.setStatusId(closedStatusId);
            incident.setResolvedAt(null);
            incident.setClosedAt(Instant.now());
            incidentRepository.save(incident);
            activityLogService.logIncidentStatusChange(
                    null, incident.getId(), "Resolved", "Closed");

            // Notify the assigned agent that their incident was auto-closed
            if (incident.getAssignedToId() != null) {
                String agentUserId = agentRepository.findById(incident.getAssignedToId())
                        .map(a -> a.getUserId())
                        .orElse(null);
                int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;
                notificationService.sendAutoClosedNotification(agentUserId, incident.getId(), incidentNo);
            }
        }

        log.info("Auto-closed {} resolved incident(s) after {} hours", overdue.size(), hours);
    }

    public int readDurationHours() {
        return systemConfigRepository.findById(AUTO_CLOSE_HOURS_KEY)
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getValue());
                    } catch (NumberFormatException e) {
                        return DEFAULT_AUTO_CLOSE_HOURS;
                    }
                })
                .orElse(DEFAULT_AUTO_CLOSE_HOURS);
    }
}
