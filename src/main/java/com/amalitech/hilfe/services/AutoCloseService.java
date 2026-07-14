package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AutoCloseConfigResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.SystemConfig;
import com.amalitech.hilfe.notifications.NotificationEventPublisher;
import com.amalitech.hilfe.notifications.events.IncidentAutoClosedAgentEvent;
import com.amalitech.hilfe.notifications.events.IncidentAutoClosedClientEvent;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.StatusRepository;
import com.amalitech.hilfe.repositories.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoCloseService {

    public static final String AUTO_CLOSE_SECONDS_KEY     = "auto_close_seconds";
    public static final int    DEFAULT_AUTO_CLOSE_SECONDS = 72 * 3600;

    private final SystemConfigRepository systemConfigRepository;
    private final IncidentRepository incidentRepository;
    private final StatusRepository statusRepository;
    private final AgentRepository agentRepository;
    private final ActivityLogService activityLogService;
    private final NotificationEventPublisher notificationEventPublisher;

    public AutoCloseConfigResponse getConfig() {
        return new AutoCloseConfigResponse(readDurationSeconds());
    }

    @Transactional
    public AutoCloseConfigResponse updateConfig(int durationSeconds) {
        SystemConfig config = systemConfigRepository.findById(AUTO_CLOSE_SECONDS_KEY)
                .orElse(SystemConfig.builder().key(AUTO_CLOSE_SECONDS_KEY).build());
        config.setValue(String.valueOf(durationSeconds));
        systemConfigRepository.save(config);
        return new AutoCloseConfigResponse(durationSeconds);
    }

    @Transactional
    public void autoCloseResolvedIncidents() {
        int seconds = readDurationSeconds();
        Instant cutoff = Instant.now().minus(seconds, ChronoUnit.SECONDS);

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

            int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;

            // Notify the assigned agent that their incident was auto-closed
            if (incident.getAssignedToId() != null) {
                String agentUserId = agentRepository.findById(incident.getAssignedToId())
                        .map(Agent::getUserId)
                        .orElse(null);
                notificationEventPublisher.publish(new IncidentAutoClosedAgentEvent(agentUserId, incident.getId(), incidentNo));
            }

            // Notify the client (incident author) that their incident was auto-closed
            if (incident.getUserId() != null) {
                notificationEventPublisher.publish(new IncidentAutoClosedClientEvent(incident.getUserId(), incident.getId(), incidentNo));
            }
        }

        log.info("Auto-closed {} resolved incident(s) after {} seconds", overdue.size(), seconds);
    }

    public int readDurationSeconds() {
        return systemConfigRepository.findById(AUTO_CLOSE_SECONDS_KEY)
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getValue());
                    } catch (NumberFormatException e) {
                        return DEFAULT_AUTO_CLOSE_SECONDS;
                    }
                })
                .orElse(DEFAULT_AUTO_CLOSE_SECONDS);
    }
}
