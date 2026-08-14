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
import com.amalitech.hilfe.utils.BusinessHoursCalculator;
import com.amalitech.hilfe.utils.BusinessHoursCalculator.BusinessHours;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    private final BusinessHoursResolver businessHoursResolver;
    private final Clock clock;

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

    /**
     * HV-1674: the reopen window (this same configured duration) counts down in business hours,
     * the same as SLA timers -- resolving an incident late Friday must not let it auto-close over
     * the weekend before the client had a full work-hours window to reopen it.
     * <p>
     * Business-hours-elapsed time can never exceed calendar-elapsed time, so the plain calendar
     * {@code cutoff} below is still a safe (if loose) DB-side pre-filter: every incident that's
     * genuinely overdue by business hours is guaranteed to also satisfy it. The per-incident
     * {@link #isReopenWindowElapsed} check is what actually decides closure.
     */
    @Transactional
    public void autoCloseResolvedIncidents() {
        int seconds = readDurationSeconds();
        Instant now = clock.instant();
        Instant cutoff = now.minus(seconds, ChronoUnit.SECONDS);

        List<Incident> candidates = incidentRepository.findOverdueResolved(cutoff);
        if (candidates.isEmpty()) return;

        long windowMinutes = Math.ceilDiv(seconds, 60);
        List<Incident> overdue = candidates.stream()
                .filter(incident -> isReopenWindowElapsed(incident, now, windowMinutes))
                .toList();
        if (overdue.isEmpty()) return;

        String closedStatusId = statusRepository.findByNameIgnoreCase("Closed")
                .orElseThrow(() -> new ArmsAuthException("Default 'Closed' status not configured", 500))
                .getId();

        for (Incident incident : overdue) {
            incident.setStatusId(closedStatusId);
            incident.setResolvedAt(null);
            incident.setClosedAt(now);
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

        log.info("Auto-closed {} resolved incident(s) after a {}-second business-hours reopen window", overdue.size(), seconds);
    }

    private boolean isReopenWindowElapsed(Incident incident, Instant now, long windowMinutes) {
        if (incident.getResolvedAt() == null) {
            return false;
        }
        BusinessHours hours = businessHoursResolver.resolveByLocationId(incident.getLocationId());
        Instant deadline = BusinessHoursCalculator.addBusinessMinutes(incident.getResolvedAt(), windowMinutes, hours);
        return !now.isBefore(deadline);
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
