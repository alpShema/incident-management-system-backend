package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock AgentRepository agentRepository;
    @Mock ActivityLogService activityLogService;
    @InjectMocks UserService userService;

    @Test
    void getUsers_returnsPaginatedProjection() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<UserRoleSummaryResponse> page = new PageImpl<>(List.of(
                new UserRoleSummaryResponse("u1", "john@test.com", "John Doe", "http://img.png", RoleCode.ADMIN, true, "Accra")
        ));
        when(userRepository.findUserRoleSummariesUnified(isNull(), isNull(), isNull(), isNull(), eq(pageable))).thenReturn(page);

        Page<UserRoleSummaryResponse> result = userService.getUsers(null, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().roleCode()).isEqualTo(RoleCode.ADMIN);
    }

    @Test
    void assignUserRole_updatesRoleAndReturnsSummary() {
        User user = User.builder()
                .id("u1")
                .email("john@test.com")
                .fullName("John Doe")
                .profileImg("http://img.png")
                .roleCode(RoleCode.CLIENT)
                .build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.ADMIN);

        assertThat(result.userId()).isEqualTo("u1");
        assertThat(result.roleCode()).isEqualTo(RoleCode.ADMIN);
        verify(agentRepository, never()).save(any(Agent.class));
        verify(activityLogService).logUserRoleChange("admin-1", "u1", RoleCode.CLIENT, RoleCode.ADMIN);
    }

    @Test
    void assignUserRole_toAgent_createsAgentRecordWhenMissing() {
        User user = User.builder()
                .id("u1")
                .email("john@test.com")
                .fullName("John Doe")
                .roleCode(RoleCode.CLIENT)
                .build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.AGENT);

        assertThat(result.roleCode()).isEqualTo(RoleCode.AGENT);
        var agentCaptor = forClass(Agent.class);
        verify(agentRepository).save(agentCaptor.capture());
        assertThat(agentCaptor.getValue().getUserId()).isEqualTo("u1");
        assertThat(agentCaptor.getValue().getStatus()).isTrue();
    }

    @Test
    void assignUserRole_toAgentWithExistingAgentRecord_doesNotCreateDuplicate() {
        User user = User.builder()
                .id("u1")
                .email("john@test.com")
                .fullName("John Doe")
                .roleCode(RoleCode.CLIENT)
                .build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(
                Agent.builder().id("agent-1").userId("u1").status(true).build()));

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.AGENT);

        assertThat(result.roleCode()).isEqualTo(RoleCode.AGENT);
        verify(agentRepository, never()).save(any(Agent.class));
    }

    @Test
    void assignUserRole_userNotFound_throwsNotFound() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.assignUserRole("admin-1", "missing", RoleCode.ADMIN))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("User not found");
    }

    @Test
    void assignUserRole_sameRole_doesNotLogActivity() {
        User user = User.builder()
                .id("u1")
                .email("john@test.com")
                .fullName("John Doe")
                .roleCode(RoleCode.ADMIN)
                .build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.ADMIN);

        assertThat(result.roleCode()).isEqualTo(RoleCode.ADMIN);
        verify(activityLogService, never())
                .logUserRoleChange(any(), any(), any(), any());
    }
}
