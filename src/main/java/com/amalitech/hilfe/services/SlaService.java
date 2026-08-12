package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.IncidentSlaResponse;
import com.amalitech.hilfe.dto.MediaResponse;
import com.amalitech.hilfe.dto.SlaConfigResponse;
import com.amalitech.hilfe.dto.UpdateSlaConfigRequest;
import com.amalitech.hilfe.dto.dashboard.SlaReportResponse;
import com.amalitech.hilfe.dto.dashboard.SlaSeverityBreakdown;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Department;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.IncidentCategory;
import com.amalitech.hilfe.models.IncidentSla;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.models.Location;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.Severity;
import com.amalitech.hilfe.models.SystemConfig;
import com.amalitech.hilfe.notifications.NotificationEventPublisher;
import com.amalitech.hilfe.notifications.events.IncidentSlaAtRiskEvent;
import com.amalitech.hilfe.notifications.events.IncidentSlaBreachedEvent;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentCategoryRepository;
import com.amalitech.hilfe.repositories.IncidentSlaRepository;
import com.amalitech.hilfe.repositories.LocationRepository;
import com.amalitech.hilfe.repositories.SeverityRepository;
import com.amalitech.hilfe.repositories.SystemConfigRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.utils.BusinessHoursCalculator;
import com.amalitech.hilfe.utils.BusinessHoursCalculator.BusinessHours;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlaService {

    public static final String SLA_AT_RISK_PCT_KEY = "sla_at_risk_pct";
    public static final int DEFAULT_AT_RISK_PCT = 80;

    private static final String STATUS_PENDING = "status-pending";
    private static final String STATUS_RESOLVED = "status-resolved";
    private static final String STATUS_CLOSED = "status-closed";
    private static final String SLA_TYPE_RESPONSE = "RESPONSE";
    private static final String SLA_TYPE_RESOLUTION = "RESOLUTION";
    private static final String STATUS_BREACHED = "BREACHED";

    private static final BusinessHours DEFAULT_BUSINESS_HOURS =
            new BusinessHours(ZoneId.of("UTC"), LocalTime.of(8, 0), LocalTime.of(17, 30));

    private final IncidentSlaRepository incidentSlaRepository;
    private final SeverityRepository severityRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final UserRepository userRepository;
    private final AgentRepository agentRepository;
    private final IncidentCategoryRepository incidentCategoryRepository;
    private final LocationRepository locationRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final ActivityLogService activityLogService;

    /**
     * Falls back to UTC 08:00-17:30 when the location, its timezone, or its hours are missing or
     * invalid -- this is what keeps every SLA calculation defined even for incidents whose
     * location record predates this feature or was deleted after the fact.
     */
    private BusinessHours resolveBusinessHours(Location location) {
        if (location == null || location.getTimezone() == null
                || location.getBusinessHoursStart() == null || location.getBusinessHoursEnd() == null) {
            return DEFAULT_BUSINESS_HOURS;
        }
        try {
            return new BusinessHours(ZoneId.of(location.getTimezone()),
                    location.getBusinessHoursStart(), location.getBusinessHoursEnd());
        } catch (Exception e) {
            return DEFAULT_BUSINESS_HOURS;
        }
    }

    private BusinessHours resolveBusinessHoursByLocationId(String locationId) {
        if (locationId == null) {
            return DEFAULT_BUSINESS_HOURS;
        }
        return locationRepository.findById(locationId)
                .map(this::resolveBusinessHours)
                .orElse(DEFAULT_BUSINESS_HOURS);
    }

    @Transactional
    public void onIncidentCreated(Incident incident) {
        if (incident == null || incident.getId() == null || incidentSlaRepository.existsById(incident.getId())) {
            return;
        }

        Severity severity = incident.getSeverity();
        if (severity == null && incident.getSeverityId() != null) {
            severity = severityRepository.findById(incident.getSeverityId()).orElse(null);
        }

        Integer responseThreshold = severity != null ? severity.getResponseTimeMinutes() : null;
        Integer resolutionThreshold = severity != null ? severity.getResolutionTimeMinutes() : null;
        Instant createdAt = incident.getCreatedAt() != null ? incident.getCreatedAt() : Instant.now();
        BusinessHours hours = resolveBusinessHoursByLocationId(incident.getLocationId());

        IncidentSla sla = IncidentSla.builder()
                .incidentId(incident.getId())
                .responseThresholdMinutes(responseThreshold)
                .resolutionThresholdMinutes(resolutionThreshold)
                .responseDueAt(addBusinessMinutes(createdAt, responseThreshold, hours))
                .resolutionDueAt(addBusinessMinutes(createdAt, resolutionThreshold, hours))
                .build();
        refreshMaterializedStatus(sla, Instant.now(), readAtRiskPercentage(), hours);
        incidentSlaRepository.save(sla);
    }

    /**
     * Returns true only when this call just recorded the assigned agent's very first response
     * (false for messages from anyone else, or subsequent messages from the assigned agent) --
     * callers use this to detect the exact moment that should drive an Open-&gt;In-Progress
     * incident status transition (see IncidentService#onFirstAgentResponse).
     */
    @Transactional
    public boolean onAgentMessageSent(Incident incident, String senderUserId) {
        if (incident == null || incident.getId() == null || senderUserId == null) {
            return false;
        }

        String assignedAgentUserId = resolveAssignedAgentUserId(incident);
        if (assignedAgentUserId == null || !assignedAgentUserId.equals(senderUserId)) {
            return false;
        }

        BusinessHours hours = resolveBusinessHoursByLocationId(incident.getLocationId());
        return incidentSlaRepository.findById(incident.getId()).map(sla -> {
            if (sla.getFirstResponseAt() != null) {
                return false;
            }

            Instant now = Instant.now();
            sla.setFirstResponseAt(now);
            sla.setResponseElapsedMs(computeElapsedMs(sla, incident.getCreatedAt(), now, hours));
            if (sla.getResponseDueAt() != null && now.isAfter(sla.getResponseDueAt()) && sla.getResponseBreachedAt() == null) {
                sla.setResponseBreachedAt(sla.getResponseDueAt());
            }
            refreshMaterializedStatus(sla, now, readAtRiskPercentage(), hours);
            incidentSlaRepository.save(sla);
            return true;
        }).orElse(false);
    }

    /**
     * Reassignment (or unassignment) hands the incident to someone who hasn't replied yet, so the
     * response timer needs to restart from now -- otherwise onAgentMessageSent's "already
     * responded" guard would permanently block the next Open-&gt;In-Progress transition, and any
     * prior breach/at-risk state would misleadingly carry over to the new agent.
     */
    @Transactional
    public void resetFirstResponse(String incidentId, String locationId) {
        BusinessHours hours = resolveBusinessHoursByLocationId(locationId);
        incidentSlaRepository.findById(incidentId).ifPresent(sla -> {
            sla.setFirstResponseAt(null);
            sla.setResponseElapsedMs(null);
            sla.setResponseBreachedAt(null);
            sla.setResponseAtRiskNotifiedAt(null);
            sla.setResponseBreachNotifiedAt(null);
            Instant now = Instant.now();
            if (sla.getResponseThresholdMinutes() != null) {
                sla.setResponseDueAt(addBusinessMinutes(now, sla.getResponseThresholdMinutes(), hours));
            }
            refreshMaterializedStatus(sla, now, readAtRiskPercentage(), hours);
            incidentSlaRepository.save(sla);
        });
    }

    @Transactional
    public void onStatusChanged(Incident incident, String previousStatusId, String newStatusId) {
        if (incident == null || incident.getId() == null) {
            return;
        }
        Optional<IncidentSla> optionalSla = incidentSlaRepository.findById(incident.getId());
        if (optionalSla.isEmpty()) {
            return;
        }
        IncidentSla sla = optionalSla.get();
        Instant now = Instant.now();
        BusinessHours hours = resolveBusinessHoursByLocationId(incident.getLocationId());
        boolean changed = false;

        boolean enteringPending = isTransitionTo(STATUS_PENDING, previousStatusId, newStatusId);
        boolean leavingPending = isTransitionFrom(STATUS_PENDING, previousStatusId, newStatusId);
        boolean resolving = isResolvingTransition(previousStatusId, newStatusId, sla);
        boolean reopening = STATUS_RESOLVED.equals(previousStatusId) && "status-reopened".equals(newStatusId);

        if (enteringPending && sla.getPauseStartedAt() == null) {
            sla.setPauseStartedAt(now);
            changed = true;
        }
        if (leavingPending && sla.getPauseStartedAt() != null) {
            applyPauseDelta(sla, now, hours);
            changed = true;
        }
        if (resolving) {
            applyResolution(sla, incident, now, hours);
            changed = true;
        }
        if (reopening) {
            applyReopening(sla, now, hours);
            changed = true;
        }
        if (changed) {
            refreshMaterializedStatus(sla, now, readAtRiskPercentage(), hours);
            incidentSlaRepository.save(sla);
        }
    }

    private boolean isTransitionTo(String status, String previousStatusId, String newStatusId) {
        return !status.equals(previousStatusId) && status.equals(newStatusId);
    }

    private boolean isTransitionFrom(String status, String previousStatusId, String newStatusId) {
        return status.equals(previousStatusId) && !status.equals(newStatusId);
    }

    private boolean isResolvingTransition(String previousStatusId, String newStatusId, IncidentSla sla) {
        boolean viaResolved = !STATUS_RESOLVED.equals(previousStatusId) && STATUS_RESOLVED.equals(newStatusId);
        boolean viaClosed = !STATUS_CLOSED.equals(previousStatusId) && STATUS_CLOSED.equals(newStatusId)
                && sla.getResolvedAtSnapshot() == null;
        return viaResolved || viaClosed;
    }

    private void applyPauseDelta(IncidentSla sla, Instant now, BusinessHours hours) {
        long pausedMinutes = BusinessHoursCalculator.businessMinutesBetween(sla.getPauseStartedAt(), now, hours);
        sla.setAccumulatedPauseMs(sla.getAccumulatedPauseMs() + pausedMinutes * 60_000L);
        if (sla.getResponseDueAt() != null && sla.getFirstResponseAt() == null && sla.getResponseBreachedAt() == null) {
            sla.setResponseDueAt(BusinessHoursCalculator.addBusinessMinutes(sla.getResponseDueAt(), pausedMinutes, hours));
        }
        if (sla.getResolutionDueAt() != null && sla.getResolvedAtSnapshot() == null) {
            sla.setResolutionDueAt(BusinessHoursCalculator.addBusinessMinutes(sla.getResolutionDueAt(), pausedMinutes, hours));
        }
        sla.setPauseStartedAt(null);
    }

    private void applyResolution(IncidentSla sla, Incident incident, Instant now, BusinessHours hours) {
        sla.setResolvedAtSnapshot(now);
        sla.setResolutionElapsedMs(computeElapsedMs(sla, incident.getCreatedAt(), now, hours));
        if (sla.getResolutionDueAt() != null) {
            long remainingMinutes = BusinessHoursCalculator.businessMinutesBetween(now, sla.getResolutionDueAt(), hours);
            sla.setResolutionRemainingMsOnResolve(remainingMinutes * 60_000L);
            if (now.isAfter(sla.getResolutionDueAt()) && sla.getResolutionBreachedAt() == null) {
                sla.setResolutionBreachedAt(sla.getResolutionDueAt());
            }
        }
        // A direct-to-closed transition (only reachable via force close, see VALID_TRANSITIONS
        // in IncidentService) can happen before an agent ever responded. Freeze the response
        // timer here too, the same snapshot pattern onAgentMessageSent uses for a real reply --
        // otherwise its status keeps recomputing against now() forever, and
        // findActiveResponseTimers() keeps returning this row on every SlaMonitorScheduler tick.
        if (sla.getFirstResponseAt() == null && sla.getResponseDueAt() != null) {
            sla.setResponseElapsedMs(computeElapsedMs(sla, incident.getCreatedAt(), now, hours));
            if (now.isAfter(sla.getResponseDueAt()) && sla.getResponseBreachedAt() == null) {
                sla.setResponseBreachedAt(sla.getResponseDueAt());
            }
        }
    }

    private void applyReopening(IncidentSla sla, Instant now, BusinessHours hours) {
        if (sla.getResolutionRemainingMsOnResolve() != null && sla.getResolutionDueAt() != null) {
            long remainingMinutes = sla.getResolutionRemainingMsOnResolve() / 60_000L;
            sla.setResolutionDueAt(BusinessHoursCalculator.addBusinessMinutes(now, remainingMinutes, hours));
        }
        sla.setResolvedAtSnapshot(null);
        sla.setPauseStartedAt(null);
    }

    @Transactional
    public void scanAndNotify() {
        int atRiskPct = readAtRiskPercentage();
        scanResponseTimers(atRiskPct);
        scanResolutionTimers(atRiskPct);
    }

    public SlaConfigResponse getConfig() {
        return new SlaConfigResponse(readAtRiskPercentage());
    }

    @Transactional
    public SlaConfigResponse updateConfig(UpdateSlaConfigRequest request) {
        SystemConfig config = systemConfigRepository.findById(SLA_AT_RISK_PCT_KEY)
                .orElse(SystemConfig.builder().key(SLA_AT_RISK_PCT_KEY).build());
        config.setValue(String.valueOf(request.atRiskPct()));
        systemConfigRepository.save(config);
        return new SlaConfigResponse(request.atRiskPct());
    }

    public IncidentSlaResponse getIncidentSlaResponse(String incidentId) {
        return getIncidentSlaResponse(incidentId, null);
    }

    public IncidentSlaResponse getIncidentSlaResponse(String incidentId, String locationId) {
        int atRiskPct = readAtRiskPercentage();
        BusinessHours hours = resolveBusinessHoursByLocationId(locationId);
        return incidentSlaRepository.findById(incidentId)
                .map(sla -> toResponse(sla, atRiskPct, hours))
                .orElse(null);
    }

    /**
     * Keyed by incidentId -&gt; locationId (not just a bare id collection) so each row's business
     * hours can be resolved without lazily loading incident.location per SLA row.
     */
    public Map<String, IncidentSlaResponse> getIncidentSlaResponses(Map<String, String> locationIdByIncidentId) {
        if (locationIdByIncidentId == null || locationIdByIncidentId.isEmpty()) {
            return Map.of();
        }

        int atRiskPct = readAtRiskPercentage();
        Map<String, BusinessHours> hoursByLocationId = new HashMap<>();
        Map<String, IncidentSlaResponse> responses = new HashMap<>();
        for (IncidentSla sla : incidentSlaRepository.findByIncidentIdIn(locationIdByIncidentId.keySet())) {
            String locationId = locationIdByIncidentId.get(sla.getIncidentId());
            BusinessHours hours = locationId == null
                    ? DEFAULT_BUSINESS_HOURS
                    : hoursByLocationId.computeIfAbsent(locationId, this::resolveBusinessHoursByLocationId);
            responses.put(sla.getIncidentId(), toResponse(sla, atRiskPct, hours));
        }
        return responses;
    }

    public IncidentResponse toIncidentResponse(Incident incident) {
        return IncidentResponse.from(incident, null, getIncidentSlaResponse(incident.getId(), incident.getLocationId()));
    }

    public IncidentResponse toIncidentResponse(Incident incident, List<MediaResponse> attachments) {
        return IncidentResponse.from(incident, attachments, getIncidentSlaResponse(incident.getId(), incident.getLocationId()));
    }

    public Page<IncidentResponse> toIncidentResponsePage(Page<Incident> incidents) {
        Map<String, String> locationIdByIncidentId = incidents.getContent().stream()
                .collect(Collectors.toMap(Incident::getId, Incident::getLocationId));
        Map<String, IncidentSlaResponse> byIncidentId = getIncidentSlaResponses(locationIdByIncidentId);
        List<IncidentResponse> content = incidents.getContent().stream()
                .map(incident -> IncidentResponse.from(incident, List.of(), byIncidentId.get(incident.getId())))
                .toList();
        return new PageImpl<>(content, incidents.getPageable(), incidents.getTotalElements());
    }

    public SlaReportResponse getReport(Instant from, Instant to, String severityId) {
        List<IncidentSla> rows = incidentSlaRepository.findForReport(
                from, from != null,
                to, to != null,
                severityId, severityId != null && !severityId.isBlank()
        );
        if (rows.isEmpty()) {
            return new SlaReportResponse(0, 0, 0, null, null, List.of());
        }

        long responseBreaches = rows.stream().filter(sla -> sla.getResponseBreachedAt() != null).count();
        long resolutionBreaches = rows.stream().filter(sla -> sla.getResolutionBreachedAt() != null).count();
        Double avgResponse = averageMinutes(rows.stream().map(IncidentSla::getResponseElapsedMs).filter(Objects::nonNull).toList());
        Double avgResolution = averageMinutes(rows.stream().map(IncidentSla::getResolutionElapsedMs).filter(Objects::nonNull).toList());
        List<SlaSeverityBreakdown> breakdown = buildSeverityBreakdown(rows);

        return new SlaReportResponse(rows.size(), responseBreaches, resolutionBreaches, avgResponse, avgResolution, breakdown);
    }

    private Map<String, List<IncidentSla>> groupBySeverity(List<IncidentSla> rows) {
        Map<String, List<IncidentSla>> grouped = new LinkedHashMap<>();
        for (IncidentSla row : rows) {
            String key = row.getIncident() != null && row.getIncident().getSeverity() != null
                    ? row.getIncident().getSeverity().getId()
                    : "unknown";
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private List<SlaSeverityBreakdown> buildSeverityBreakdown(List<IncidentSla> rows) {
        return groupBySeverity(rows).values().stream()
                .map(this::toSeverityBreakdown)
                .toList();
    }

    private SlaSeverityBreakdown toSeverityBreakdown(List<IncidentSla> group) {
        Incident sampleIncident = group.getFirst().getIncident();
        Severity severity = sampleIncident != null ? sampleIncident.getSeverity() : null;
        return new SlaSeverityBreakdown(
                severity != null ? severity.getId() : null,
                severity != null ? severity.getName() : "Unknown",
                group.size(),
                group.stream().filter(sla -> sla.getResponseBreachedAt() != null).count(),
                group.stream().filter(sla -> sla.getResolutionBreachedAt() != null).count(),
                averageMinutes(group.stream().map(IncidentSla::getResponseElapsedMs).filter(Objects::nonNull).toList()),
                averageMinutes(group.stream().map(IncidentSla::getResolutionElapsedMs).filter(Objects::nonNull).toList())
        );
    }

    private void scanResponseTimers(int atRiskPct) {
        Instant now = Instant.now();
        for (IncidentSla sla : incidentSlaRepository.findActiveResponseTimers()) {
            if (sla.getIncident() == null || sla.getResolvedAtSnapshot() != null) {
                continue; // no incident, or frozen on resolve/force-close -- never notify
            }
            BusinessHours hours = resolveBusinessHours(sla.getIncident().getLocation());
            String freshStatus = computeResponseStatus(sla, now, atRiskPct, hours);
            boolean statusChanged = !freshStatus.equals(sla.getResponseStatus());
            sla.setResponseStatus(freshStatus);

            boolean saved = isBreached(now, sla.getResponseDueAt())
                    ? handleResponseBreach(sla, now)
                    : checkResponseAtRisk(sla, now, atRiskPct, hours);
            if (statusChanged && !saved) {
                incidentSlaRepository.save(sla);
            }
        }
    }

    private boolean checkResponseAtRisk(IncidentSla sla, Instant now, int atRiskPct, BusinessHours hours) {
        if (sla.getResponseAtRiskNotifiedAt() == null
                && isAtRisk(now, sla.getResponseDueAt(), sla.getResponseThresholdMinutes(), atRiskPct, hours)) {
            publishAtRiskNotifications(sla.getIncident(), SLA_TYPE_RESPONSE, remainingMinutes(now, sla.getResponseDueAt(), hours));
            sla.setResponseAtRiskNotifiedAt(now);
            incidentSlaRepository.save(sla);
            return true;
        }
        return false;
    }

    private void scanResolutionTimers(int atRiskPct) {
        Instant now = Instant.now();
        for (IncidentSla sla : incidentSlaRepository.findActiveResolutionTimers()) {
            if (sla.getIncident() == null || sla.getResolvedAtSnapshot() != null) {
                continue; // no incident, or frozen on resolve/force-close -- never notify
            }
            BusinessHours hours = resolveBusinessHours(sla.getIncident().getLocation());
            String freshStatus = computeResolutionStatus(sla, now, atRiskPct, hours);
            boolean statusChanged = !freshStatus.equals(sla.getResolutionStatus());
            sla.setResolutionStatus(freshStatus);

            boolean saved = isBreached(now, sla.getResolutionDueAt())
                    ? handleResolutionBreach(sla, now)
                    : checkResolutionAtRisk(sla, now, atRiskPct, hours);
            if (statusChanged && !saved) {
                incidentSlaRepository.save(sla);
            }
        }
    }

    private boolean checkResolutionAtRisk(IncidentSla sla, Instant now, int atRiskPct, BusinessHours hours) {
        if (sla.getResolutionAtRiskNotifiedAt() == null
                && isAtRisk(now, sla.getResolutionDueAt(), sla.getResolutionThresholdMinutes(), atRiskPct, hours)) {
            publishAtRiskNotifications(sla.getIncident(), SLA_TYPE_RESOLUTION, remainingMinutes(now, sla.getResolutionDueAt(), hours));
            sla.setResolutionAtRiskNotifiedAt(now);
            incidentSlaRepository.save(sla);
            return true;
        }
        return false;
    }

    private boolean handleResponseBreach(IncidentSla sla, Instant now) {
        boolean changed = false;
        if (sla.getResponseBreachedAt() == null) {
            sla.setResponseBreachedAt(sla.getResponseDueAt());
            changed = true;
            logBreach(sla.getIncidentId(), SLA_TYPE_RESPONSE, overdueMinutes(now, sla.getResponseDueAt()));
        }
        if (sla.getResponseBreachNotifiedAt() == null) {
            publishBreachNotifications(sla.getIncident(), SLA_TYPE_RESPONSE, overdueMinutes(now, sla.getResponseDueAt()));
            sla.setResponseBreachNotifiedAt(now);
            changed = true;
        }
        if (changed) {
            incidentSlaRepository.save(sla);
        }
        return changed;
    }

    private boolean handleResolutionBreach(IncidentSla sla, Instant now) {
        boolean changed = false;
        if (sla.getResolutionBreachedAt() == null) {
            sla.setResolutionBreachedAt(sla.getResolutionDueAt());
            changed = true;
            logBreach(sla.getIncidentId(), SLA_TYPE_RESOLUTION, overdueMinutes(now, sla.getResolutionDueAt()));
        }
        if (sla.getResolutionBreachNotifiedAt() == null) {
            publishBreachNotifications(sla.getIncident(), SLA_TYPE_RESOLUTION, overdueMinutes(now, sla.getResolutionDueAt()));
            sla.setResolutionBreachNotifiedAt(now);
            changed = true;
        }
        if (changed) {
            incidentSlaRepository.save(sla);
        }
        return changed;
    }

    private void publishAtRiskNotifications(Incident incident, String slaType, long minutesRemaining) {
        int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;
        for (String recipient : resolveNotificationRecipients(incident)) {
            notificationEventPublisher.publish(new IncidentSlaAtRiskEvent(
                    recipient,
                    incident.getId(),
                    incidentNo,
                    slaType,
                    minutesRemaining
            ));
        }
    }

    private void publishBreachNotifications(Incident incident, String slaType, long minutesOverdue) {
        int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;
        for (String recipient : resolveNotificationRecipients(incident)) {
            notificationEventPublisher.publish(new IncidentSlaBreachedEvent(
                    recipient,
                    incident.getId(),
                    incidentNo,
                    slaType,
                    minutesOverdue
            ));
        }
    }

    private void logBreach(String incidentId, String slaType, long minutesOverdue) {
        activityLogService.logIncidentSlaBreached(incidentId, slaType, minutesOverdue);
    }

    /**
     * Admin/Admin-Agent recipients are suppressed once an available agent is assigned to the
     * incident, since their intervention is not needed in that case. The assigned agent is always
     * notified regardless of this rule — including when that agent's user account itself holds an
     * admin-capacity role, since they remain the person responsible for actioning the incident.
     */
    private List<String> resolveNotificationRecipients(Incident incident) {
        Agent assignedAgent = resolveAssignedAgent(incident);
        String agentUserId = assignedAgent != null ? assignedAgent.getUserId() : null;
        boolean agentAvailable = assignedAgent != null && Boolean.TRUE.equals(assignedAgent.getStatus());
        boolean suppressAdmins = agentUserId != null && agentAvailable;

        List<String> recipients = new ArrayList<>();
        if (agentUserId != null) {
            recipients.add(agentUserId);
        }
        if (!suppressAdmins) {
            for (String adminId : resolveEscalationRecipientUserIds(incident)) {
                if (!recipients.contains(adminId)) {
                    recipients.add(adminId);
                }
            }
        }
        return recipients;
    }

    // Escalate to the incident's department head instead of broadcasting to all admins. Falls
    // back to all admins when there's no category/department link, no head is set, or the
    // stored head is no longer an active admin-capable user (role/status can change after
    // assignment). Mirrors IncidentService#resolveEscalationRecipientUserIds.
    private List<String> resolveEscalationRecipientUserIds(Incident incident) {
        IncidentType incidentType = incident.getIncidentType();
        String categoryId = incidentType != null ? incidentType.getCategoryId() : null;
        if (categoryId != null) {
            String headUserId = incidentCategoryRepository.findByIdWithDepartment(categoryId)
                    .map(IncidentCategory::getDepartment)
                    .map(Department::getHeadUserId)
                    .orElse(null);
            if (headUserId != null && isActiveAdminCapableUser(headUserId)) {
                return List.of(headUserId);
            }
        }
        return userRepository.findActiveAdminUserIds();
    }

    private boolean isActiveAdminCapableUser(String userId) {
        return userRepository.findById(userId)
                .filter(u -> Boolean.TRUE.equals(u.getStatus()))
                .map(u -> {
                    RoleCode role = RoleCode.valueOf(u.getRoleCode().toUpperCase());
                    return role == RoleCode.ADMIN || role == RoleCode.ADMIN_AGENT || role == RoleCode.SUPER_ADMIN;
                })
                .orElse(false);
    }

    private IncidentSlaResponse toResponse(IncidentSla sla, int atRiskPct, BusinessHours hours) {
        Instant effectiveNow = sla.getPauseStartedAt() != null ? sla.getPauseStartedAt() : Instant.now();
        return new IncidentSlaResponse(
                sla.getResponseThresholdMinutes(),
                sla.getResolutionThresholdMinutes(),
                toSeconds(sla.getResponseThresholdMinutes()),
                toSeconds(sla.getResolutionThresholdMinutes()),
                sla.getResponseDueAt(),
                sla.getResolutionDueAt(),
                sla.getFirstResponseAt(),
                sla.getResponseBreachedAt(),
                sla.getResolutionBreachedAt(),
                computeResponseStatus(sla, effectiveNow, atRiskPct, hours),
                computeResolutionStatus(sla, effectiveNow, atRiskPct, hours),
                sla.getPauseStartedAt() != null,
                sla.getResponseElapsedMs(),
                sla.getResolutionElapsedMs()
        );
    }

    private Long toSeconds(Integer minutes) {
        return minutes != null ? minutes * 60L : null;
    }

    private String computeResponseStatus(IncidentSla sla, Instant now, int atRiskPct, BusinessHours hours) {
        if (sla.getResponseThresholdMinutes() == null || sla.getResponseDueAt() == null) {
            return "NOT_TRACKED";
        }
        if (sla.getFirstResponseAt() != null || sla.getResolvedAtSnapshot() != null) {
            return sla.getResponseBreachedAt() == null ? "MET" : STATUS_BREACHED;
        }
        if (sla.getResponseBreachedAt() != null || !now.isBefore(sla.getResponseDueAt())) {
            return STATUS_BREACHED;
        }
        if (isAtRisk(now, sla.getResponseDueAt(), sla.getResponseThresholdMinutes(), atRiskPct, hours)) {
            return "AT_RISK";
        }
        return "ON_TRACK";
    }

    private String computeResolutionStatus(IncidentSla sla, Instant now, int atRiskPct, BusinessHours hours) {
        if (sla.getResolutionThresholdMinutes() == null || sla.getResolutionDueAt() == null) {
            return "NOT_TRACKED";
        }
        if (sla.getResolvedAtSnapshot() != null) {
            return sla.getResolutionBreachedAt() == null ? "MET" : STATUS_BREACHED;
        }
        if (sla.getResolutionBreachedAt() != null || !now.isBefore(sla.getResolutionDueAt())) {
            return STATUS_BREACHED;
        }
        if (isAtRisk(now, sla.getResolutionDueAt(), sla.getResolutionThresholdMinutes(), atRiskPct, hours)) {
            return "AT_RISK";
        }
        return "ON_TRACK";
    }

    private void refreshMaterializedStatus(IncidentSla sla, Instant now, int atRiskPct, BusinessHours hours) {
        Instant effectiveNow = sla.getPauseStartedAt() != null ? sla.getPauseStartedAt() : now;
        sla.setResponseStatus(computeResponseStatus(sla, effectiveNow, atRiskPct, hours));
        sla.setResolutionStatus(computeResolutionStatus(sla, effectiveNow, atRiskPct, hours));
    }

    private int readAtRiskPercentage() {
        return systemConfigRepository.findById(SLA_AT_RISK_PCT_KEY)
                .map(SystemConfig::getValue)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        return DEFAULT_AT_RISK_PCT;
                    }
                })
                .orElse(DEFAULT_AT_RISK_PCT);
    }

    private String resolveAssignedAgentUserId(Incident incident) {
        Agent assignedAgent = resolveAssignedAgent(incident);
        return assignedAgent != null ? assignedAgent.getUserId() : null;
    }

    private Agent resolveAssignedAgent(Incident incident) {
        if (incident.getAssignedToId() == null) {
            return null;
        }
        Agent assignedAgent = incident.getAssignedTo();
        if (assignedAgent != null && assignedAgent.getUserId() != null) {
            return assignedAgent;
        }
        return agentRepository.findById(incident.getAssignedToId()).orElse(assignedAgent);
    }

    private long computeElapsedMs(IncidentSla sla, Instant createdAt, Instant now, BusinessHours hours) {
        if (createdAt == null) {
            return 0L;
        }
        long totalMs = BusinessHoursCalculator.businessMinutesBetween(createdAt, now, hours) * 60_000L;
        long pausedMs = sla.getAccumulatedPauseMs();
        if (sla.getPauseStartedAt() != null) {
            pausedMs += BusinessHoursCalculator.businessMinutesBetween(sla.getPauseStartedAt(), now, hours) * 60_000L;
        }
        return Math.max(0L, totalMs - pausedMs);
    }

    private Instant addBusinessMinutes(Instant base, Integer minutes, BusinessHours hours) {
        return base != null && minutes != null ? BusinessHoursCalculator.addBusinessMinutes(base, minutes, hours) : null;
    }

    private boolean isBreached(Instant now, Instant dueAt) {
        return dueAt != null && !now.isBefore(dueAt);
    }

    private boolean isAtRisk(Instant now, Instant dueAt, Integer thresholdMinutes, int atRiskPct, BusinessHours hours) {
        if (dueAt == null || thresholdMinutes == null) {
            return false;
        }
        long bufferMinutes = thresholdMinutes - ((long) thresholdMinutes * atRiskPct / 100);
        long remainingBusinessMinutes = BusinessHoursCalculator.businessMinutesBetween(now, dueAt, hours);
        return remainingBusinessMinutes <= bufferMinutes;
    }

    private long remainingMinutes(Instant now, Instant dueAt, BusinessHours hours) {
        if (dueAt == null) {
            return 0L;
        }
        return Math.max(1L, BusinessHoursCalculator.businessMinutesBetween(now, dueAt, hours));
    }

    private long overdueMinutes(Instant now, Instant dueAt) {
        if (dueAt == null) {
            return 0L;
        }
        long ms = Math.max(0L, Duration.between(dueAt, now).toMillis());
        return Math.max(1L, (long) Math.ceil(ms / 60000.0));
    }

    private Double averageMinutes(List<Long> elapsedMs) {
        if (elapsedMs.isEmpty()) {
            return null;
        }
        return elapsedMs.stream()
                .mapToDouble(value -> value / 60000.0)
                .average()
                .orElse(0.0);
    }
}
