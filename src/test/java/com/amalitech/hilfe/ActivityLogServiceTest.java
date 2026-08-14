package com.amalitech.hilfe;

import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.ActivityLog;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Location;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.models.Role;
import com.amalitech.hilfe.repositories.ActivityLogRepository;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.LocationRepository;
import com.amalitech.hilfe.repositories.RoleRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.ConfidentialIncidentAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityLogServiceTest {

    @Mock ActivityLogRepository activityLogRepository;
    @Mock UserRepository userRepository;
    @Mock IncidentRepository incidentRepository;
    @Mock AgentRepository agentRepository;
    @Mock AgentGroupMemberRepository agentGroupMemberRepository;
    @Mock AgentGroupRepository agentGroupRepository;
    @Mock RoleRepository roleRepository;
    @Mock LocationRepository locationRepository;
    @Mock ConfidentialIncidentAccess confidentialIncidentAccess;
    @InjectMocks ActivityLogService activityLogService;

    @BeforeEach
    void stubConfidentialAccessDefault() {
        // Default: none of these incidents are confidential, and canAccess is itself always
        // true for those -- keeps the existing non-confidential tests unaffected.
        lenient().when(confidentialIncidentAccess.canAccess(any(), any())).thenReturn(true);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthority(String authority) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Incident incident(String id, String userId, String assignedToId) {
        return Incident.builder().id(id).userId(userId).assignedToId(assignedToId).build();
    }

    // ── getActivityLogs(Pageable) ────────────────────────────────────────────

    @Test
    void getActivityLogs_pageable_delegatesToRepository() {
        when(activityLogRepository.findActivityLogResponses(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        activityLogService.getActivityLogs(PageRequest.of(0, 10));

        verify(activityLogRepository).findActivityLogResponses(any(Pageable.class));
    }

    // ── getActivityLogs(incidentId=null) ─────────────────────────────────────

    @Test
    void getActivityLogs_nullIncidentId_withAuthority_returnsAll() {
        setAuthority("rbac.role.read");
        when(activityLogRepository.findActivityLogResponses(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        activityLogService.getActivityLogs(null, PageRequest.of(0, 10), "u1", "CLIENT");

        verify(activityLogRepository).findActivityLogResponses(any(Pageable.class));
    }

    @Test
    void getActivityLogs_nullIncidentId_withoutAuthority_throws403() {
        SecurityContextHolder.clearContext();
        var pageable = PageRequest.of(0, 10);
        assertThatThrownBy(() -> activityLogService.getActivityLogs(null, pageable, "u1", "CLIENT"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    // ── getActivityLogs(incidentId) access control ───────────────────────────

    @Test
    void getActivityLogs_incidentNotFound_throws404() {
        when(incidentRepository.findByIdWithDetails("missing")).thenReturn(Optional.empty());

        var pageable = PageRequest.of(0, 10);
        assertThatThrownBy(() -> activityLogService.getActivityLogs("missing", pageable, "u1", "ADMIN"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void getActivityLogs_adminRole_allowedForAnyIncident() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1", "owner", null)));
        when(activityLogRepository.findActivityLogResponsesByIncidentId(eq("inc-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        activityLogService.getActivityLogs("inc-1", PageRequest.of(0, 10), "admin-user", "ADMIN");

        verify(activityLogRepository).findActivityLogResponsesByIncidentId(eq("inc-1"), any(Pageable.class));
    }

    @Test
    void getActivityLogs_clientOwner_allowedForOwnIncident() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1", "u1", null)));
        when(activityLogRepository.findActivityLogResponsesByIncidentIdExcludingAction(eq("inc-1"), eq("INCIDENT_VIEWED"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        activityLogService.getActivityLogs("inc-1", PageRequest.of(0, 10), "u1", "CLIENT");

        verify(activityLogRepository).findActivityLogResponsesByIncidentIdExcludingAction(eq("inc-1"), eq("INCIDENT_VIEWED"), any(Pageable.class));
    }

    @Test
    void getActivityLogs_clientNotOwner_throws403() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1", "owner", null)));

        var pageable = PageRequest.of(0, 10);
        assertThatThrownBy(() -> activityLogService.getActivityLogs("inc-1", pageable, "other", "CLIENT"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void getActivityLogs_assignedAgent_allowedForAssignedIncident() {
        Agent agent = Agent.builder().id("agent-1").userId("u-agent").build();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1", "owner", "agent-1")));
        when(agentRepository.findByUserId("u-agent")).thenReturn(Optional.of(agent));
        when(activityLogRepository.findActivityLogResponsesByIncidentId(eq("inc-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        activityLogService.getActivityLogs("inc-1", PageRequest.of(0, 10), "u-agent", "AGENT");

        verify(activityLogRepository).findActivityLogResponsesByIncidentId(eq("inc-1"), any(Pageable.class));
    }

    @Test
    void getActivityLogs_sameDepartmentAgent_allowedForIncidentHistoryView() {
        Agent actor = Agent.builder().id("actor-agent").userId("u-actor").build();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1", "owner", "assigned-agent")));
        when(agentRepository.findByUserId("u-actor")).thenReturn(Optional.of(actor));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("actor-agent")).thenReturn(List.of("g1"));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("assigned-agent")).thenReturn(List.of("g2"));
        when(agentGroupRepository.findDepartmentIdsByGroupIds(List.of("g1"))).thenReturn(List.of("d1"));
        when(agentGroupRepository.findDepartmentIdsByGroupIds(List.of("g2"))).thenReturn(List.of("d1"));
        when(activityLogRepository.findActivityLogResponsesByIncidentId(eq("inc-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        activityLogService.getActivityLogs("inc-1", PageRequest.of(0, 10), "u-actor", "AGENT");

        verify(activityLogRepository).findActivityLogResponsesByIncidentId(eq("inc-1"), any(Pageable.class));
    }

    @Test
    void getActivityLogs_crossDepartmentAgent_throws403() {
        Agent actor = Agent.builder().id("actor-agent").userId("u-actor").build();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1", "owner", "assigned-agent")));
        when(agentRepository.findByUserId("u-actor")).thenReturn(Optional.of(actor));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("actor-agent")).thenReturn(List.of("g1"));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("assigned-agent")).thenReturn(List.of("g2"));
        when(agentGroupRepository.findDepartmentIdsByGroupIds(List.of("g1"))).thenReturn(List.of("d1"));
        when(agentGroupRepository.findDepartmentIdsByGroupIds(List.of("g2"))).thenReturn(List.of("d2"));

        var pageable = PageRequest.of(0, 10);
        assertThatThrownBy(() -> activityLogService.getActivityLogs("inc-1", pageable, "u-actor", "AGENT"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void getActivityLogs_agentWithNoGroupOrDepartment_throws403() {
        Agent actor = Agent.builder().id("actor-agent").userId("u-actor").build();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1", "owner", "assigned-agent")));
        when(agentRepository.findByUserId("u-actor")).thenReturn(Optional.of(actor));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("actor-agent")).thenReturn(List.of());

        var pageable = PageRequest.of(0, 10);
        assertThatThrownBy(() -> activityLogService.getActivityLogs("inc-1", pageable, "u-actor", "AGENT"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    // ── HV-1619: confidential incidents lock the history the same way as the detail view ────

    private Incident confidentialIncident(String id, String userId, String assignedToId) {
        IncidentType topic = IncidentType.builder()
                .id("type-1").name("Confidential Topic").agentGroupId("group-1").confidential(true).build();
        Incident incident = incident(id, userId, assignedToId);
        incident.setIncidentType(topic);
        return incident;
    }

    @Test
    void getActivityLogs_confidentialIncident_accessGranted_returnsHistory() {
        Incident incident = confidentialIncident("inc-1", "owner", "assigned-agent");
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(confidentialIncidentAccess.canAccess("group-user", incident)).thenReturn(true);
        when(activityLogRepository.findActivityLogResponsesByIncidentId(eq("inc-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        activityLogService.getActivityLogs("inc-1", PageRequest.of(0, 10), "group-user", "AGENT");

        verify(activityLogRepository).findActivityLogResponsesByIncidentId(eq("inc-1"), any(Pageable.class));
    }

    @Test
    void getActivityLogs_confidentialIncident_nonMemberAdmin_throws403() {
        // The admin/admin-agent/super-admin bypass a few lines down must not apply once the
        // incident is confidential -- only ConfidentialIncidentAccess#canAccess decides.
        Incident incident = confidentialIncident("inc-1", "owner", "assigned-agent");
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(confidentialIncidentAccess.canAccess("outside-admin", incident)).thenReturn(false);

        var pageable = PageRequest.of(0, 10);
        assertThatThrownBy(() -> activityLogService.getActivityLogs("inc-1", pageable, "outside-admin", "ADMIN"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
        verifyNoInteractions(activityLogRepository);
    }

    // ── logUserRoleChange ────────────────────────────────────────────────────

    @Test
    void logUserRoleChange_savesActivityLogWithCorrectAction() {
        when(userRepository.findById("actor")).thenReturn(Optional.of(User.builder().id("actor").fullName("Alice").build()));
        when(userRepository.findById("target")).thenReturn(Optional.of(User.builder().id("target").fullName("Bob").build()));
        when(roleRepository.findByCode("CLIENT")).thenReturn(Optional.of(Role.builder().id("r1").code("CLIENT").name("Client").build()));
        when(roleRepository.findByCode("AGENT")).thenReturn(Optional.of(Role.builder().id("r2").code("AGENT").name("Agent").build()));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(inv -> inv.getArgument(0));

        activityLogService.logUserRoleChange("actor", "target", "CLIENT", "AGENT");

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("ROLE_CHANGED");
        assertThat(captor.getValue().getDescription()).contains("Alice").contains("Bob").contains("Client").contains("Agent");
        assertThat(captor.getValue().getMetadata()).contains("CLIENT").contains("AGENT");
    }

    // ── logIncidentStatusChange ──────────────────────────────────────────────

    @Test
    void logIncidentStatusChange_savesActivityLogWithCorrectAction() {
        when(userRepository.findById("actor")).thenReturn(Optional.of(User.builder().id("actor").fullName("Alice").build()));
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(
                Incident.builder().id("inc-1").incidentNo(42).build()));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(inv -> inv.getArgument(0));

        activityLogService.logIncidentStatusChange("actor", "inc-1", "OPEN", "RESOLVED");

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("INCIDENT_STATUS_CHANGED");
        assertThat(captor.getValue().getSubjectId()).isEqualTo("inc-1");
    }

    @Test
    void logIncidentStatusChange_nullActor_attributesChangeToSystem() {
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(
                Incident.builder().id("inc-1").incidentNo(42).build()));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(inv -> inv.getArgument(0));

        activityLogService.logIncidentStatusChange(null, "inc-1", "Resolved", "Closed");

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActorUserId()).isNull();
        assertThat(captor.getValue().getDescription()).contains("by System").doesNotContain("Unknown");
        verifyNoInteractions(userRepository);
    }

    // ── logIncidentAssignment ────────────────────────────────────────────────

    @Test
    void logIncidentAssignment_savesActivityLogWithAgentId() {
        when(userRepository.findById("actor")).thenReturn(Optional.of(User.builder().id("actor").fullName("Alice").build()));
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(
                Incident.builder().id("inc-1").incidentNo(7).build()));
        when(agentRepository.findByIdWithUser("agent-1")).thenReturn(Optional.empty());
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(inv -> inv.getArgument(0));

        activityLogService.logIncidentAssignment("actor", "inc-1", "agent-1");

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("INCIDENT_ASSIGNED");
        assertThat(captor.getValue().getMetadata()).contains("agent-1");
    }

    // ── logIncidentViewed ────────────────────────────────────────────────────

    @Test
    void logIncidentViewed_savesActivityLogWithCorrectAction() {
        when(userRepository.findById("actor")).thenReturn(Optional.of(User.builder().id("actor").fullName("Alice").build()));
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(
                Incident.builder().id("inc-1").incidentNo(9).build()));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(inv -> inv.getArgument(0));

        activityLogService.logIncidentViewed("actor", "inc-1");

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("INCIDENT_VIEWED");
        assertThat(captor.getValue().getSubjectId()).isEqualTo("inc-1");
        assertThat(captor.getValue().getDescription()).contains("Alice");
    }

    // ── logUnauthorizedConfidentialUpdate ────────────────────────────────────

    @Test
    void logUnauthorizedConfidentialUpdate_savesActivityLogWithCorrectAction() {
        when(userRepository.findById("actor")).thenReturn(Optional.of(User.builder().id("actor").fullName("Alice").build()));
        when(incidentRepository.findById("inc-1")).thenReturn(Optional.of(
                Incident.builder().id("inc-1").incidentNo(11).build()));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(inv -> inv.getArgument(0));

        activityLogService.logUnauthorizedConfidentialUpdate("actor", "inc-1");

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("UNAUTHORIZED_CONFIDENTIAL_UPDATE");
        assertThat(captor.getValue().getSubjectId()).isEqualTo("inc-1");
        assertThat(captor.getValue().getDescription()).contains("Alice");
    }

    // ── logLocationTimezoneChanged ──────────────────────────────────────────

    @Test
    void logLocationTimezoneChanged_savesActivityLogWithOldAndNewValues() {
        when(userRepository.findById("actor")).thenReturn(Optional.of(User.builder().id("actor").fullName("Alice").build()));
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(Location.builder().id("loc-1").name("Accra").build()));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(inv -> inv.getArgument(0));

        activityLogService.logLocationTimezoneChanged("actor", "loc-1", "Africa/Accra", "Africa/Kigali");

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("LOCATION_TIMEZONE_CHANGED");
        assertThat(captor.getValue().getSubjectId()).isEqualTo("loc-1");
        assertThat(captor.getValue().getDescription()).contains("Alice", "Accra", "Africa/Accra", "Africa/Kigali");
        assertThat(captor.getValue().getMetadata()).contains("Africa/Accra", "Africa/Kigali");
    }

    // ── logLocationBusinessHoursChanged ─────────────────────────────────────

    @Test
    void logLocationBusinessHoursChanged_savesActivityLogWithOldAndNewValues() {
        when(userRepository.findById("actor")).thenReturn(Optional.of(User.builder().id("actor").fullName("Alice").build()));
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(Location.builder().id("loc-1").name("Accra").build()));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(inv -> inv.getArgument(0));

        activityLogService.logLocationBusinessHoursChanged(
                "actor", "loc-1", LocalTime.of(8, 0), LocalTime.of(17, 30), LocalTime.of(9, 0), LocalTime.of(18, 0));

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("LOCATION_BUSINESS_HOURS_CHANGED");
        assertThat(captor.getValue().getSubjectId()).isEqualTo("loc-1");
        assertThat(captor.getValue().getDescription()).contains("Alice", "Accra", "08:00", "17:30", "09:00", "18:00");
        assertThat(captor.getValue().getMetadata()).contains("08:00", "17:30", "09:00", "18:00");
    }
}
