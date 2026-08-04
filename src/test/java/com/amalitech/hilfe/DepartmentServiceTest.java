package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.CreateDepartmentRequest;
import com.amalitech.hilfe.dto.DepartmentRequest;
import com.amalitech.hilfe.dto.DepartmentResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.AgentGroup;
import com.amalitech.hilfe.models.Department;
import com.amalitech.hilfe.models.IncidentCategory;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.repositories.IncidentCategoryRepository;
import com.amalitech.hilfe.repositories.IncidentTypeRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.DepartmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock DepartmentRepository departmentRepository;
    @Mock AgentGroupRepository agentGroupRepository;
    @Mock IncidentCategoryRepository categoryRepository;
    @Mock IncidentTypeRepository typeRepository;
    @Mock UserRepository userRepository;
    @Mock ActivityLogService activityLogService;
    @Mock EntityManager entityManager;
    @InjectMocks DepartmentService departmentService;

    private User adminUser(String id, boolean status, String roleCode) {
        return User.builder().id(id).fullName("Test User").status(status).roleCode(RoleCode.valueOf(roleCode)).build();
    }

    private Department department(Boolean status) {
        return Department.builder()
                .id("dept-1")
                .name("Facilities")
                .description("Facilities dept")
                .status(status)
                .build();
    }

    @Test
    void updateDepartmentStatus_activate_inactiveDepartment_succeeds() {
        Department dept = department(false);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(java.util.List.of());

        DepartmentResponse response = departmentService.updateDepartmentStatus("dept-1", true);

        assertThat(response.status()).isTrue();
        verify(departmentRepository).save(dept);
    }

    @Test
    void updateDepartmentStatus_deactivate_noLinks_succeeds() {
        Department dept = department(true);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(java.util.List.of());

        DepartmentResponse response = departmentService.updateDepartmentStatus("dept-1", false);

        assertThat(response.status()).isFalse();
        verify(departmentRepository).save(dept);
    }

    @Test
    void updateDepartmentStatus_deactivate_withCategories_cascadesToCategoryAndTopics() {
        Department dept = department(true);
        IncidentCategory category = IncidentCategory.builder()
                .id("cat-1")
                .name("Networking")
                .departmentId("dept-1")
                .status(true)
                .build();
        IncidentType topic = IncidentType.builder()
                .id("topic-1")
                .name("Wifi")
                .categoryId("cat-1")
                .status(true)
                .build();
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(List.of(category));
        when(typeRepository.findByCategoryId("cat-1")).thenReturn(List.of(topic));
        when(agentGroupRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(List.of());

        DepartmentResponse response = departmentService.updateDepartmentStatus("dept-1", false);

        assertThat(response.status()).isFalse();
        assertThat(category.getStatus()).isFalse();
        assertThat(topic.getStatus()).isFalse();
        verify(departmentRepository).save(dept);
        verify(categoryRepository).saveAll(List.of(category));
        verify(typeRepository).saveAll(List.of(topic));
    }

    @Test
    void updateDepartmentStatus_deactivate_withActiveAgentGroups_cascadesToAgentGroups() {
        Department dept = department(true);
        AgentGroup agentGroup = AgentGroup.builder()
                .id("group-1")
                .name("IT Support")
                .departmentId("dept-1")
                .status(true)
                .build();
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(List.of());
        when(agentGroupRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(List.of(agentGroup));

        DepartmentResponse response = departmentService.updateDepartmentStatus("dept-1", false);

        assertThat(response.status()).isFalse();
        assertThat(agentGroup.getStatus()).isFalse();
        verify(departmentRepository).save(dept);
        verify(agentGroupRepository).saveAll(List.of(agentGroup));
    }

    @Test
    void updateDepartmentStatus_sameStatus_throws409() {
        Department dept = department(true);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));

        assertThatThrownBy(() -> departmentService.updateDepartmentStatus("dept-1", true))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Department is already active")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void updateDepartmentStatus_notFound_throws404() {
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.updateDepartmentStatus("dept-1", false))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Department not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void createDepartment_trimsNameAndDescription() {
        when(departmentRepository.existsByNameIgnoreCase("Facilities")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser("admin-1", true, "ADMIN")));

        DepartmentResponse response = departmentService.createDepartment(
                "actor-1", new CreateDepartmentRequest("  Facilities  ",  "  Facilities dept  ", "admin-1"));

        assertThat(response.name()).isEqualTo("Facilities");
        assertThat(response.description()).isEqualTo("Facilities dept");
        var captor = org.mockito.ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Facilities");
        assertThat(captor.getValue().getDescription()).isEqualTo("Facilities dept");
    }

    @Test
    void createDepartment_setsHeadAndLogsAssignment() {
        when(departmentRepository.existsByNameIgnoreCase("Facilities")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser("admin-1", true, "ADMIN")));

        DepartmentResponse response = departmentService.createDepartment(
                "actor-1", new CreateDepartmentRequest("Facilities", "Facilities dept", "admin-1"));

        assertThat(response.headUserId()).isEqualTo("admin-1");
        var captor = org.mockito.ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(captor.capture());
        assertThat(captor.getValue().getHeadUserId()).isEqualTo("admin-1");
        verify(activityLogService).logDepartmentHeadAssigned("actor-1", captor.getValue().getId(), null, "admin-1");
    }

    @Test
    void createDepartment_nonAdminRoleHead_throws400() {
        when(departmentRepository.existsByNameIgnoreCase("Facilities")).thenReturn(false);
        when(userRepository.findById("agent-user")).thenReturn(Optional.of(adminUser("agent-user", true, "AGENT")));
        CreateDepartmentRequest request = new CreateDepartmentRequest("Facilities", "Facilities dept", "agent-user");

        assertThatThrownBy(() -> departmentService.createDepartment("actor-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Department head must be an Admin or Admin-Agent")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void createDepartment_inactiveOrMissingHeadUser_throws404() {
        when(departmentRepository.existsByNameIgnoreCase("Facilities")).thenReturn(false);
        when(userRepository.findById("admin-1")).thenReturn(Optional.empty());
        CreateDepartmentRequest request = new CreateDepartmentRequest("Facilities", "Facilities dept", "admin-1");

        assertThatThrownBy(() -> departmentService.createDepartment("actor-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("User not found or inactive")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void updateDepartment_trimsNameAndDescription() {
        Department dept = department(false);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.existsByNameIgnoreCase("Facilities Updated")).thenReturn(false);
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(java.util.List.of());

        DepartmentResponse response = departmentService.updateDepartment(
                "dept-1", new DepartmentRequest("  Facilities Updated  ", "  Updated description  "));

        assertThat(response.name()).isEqualTo("Facilities Updated");
        assertThat(response.description()).isEqualTo("Updated description");
        verify(departmentRepository).save(dept);
    }

    @Test
    void getDepartment_inactiveDepartment_isReturned() {
        Department dept = department(false);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(java.util.List.of());

        DepartmentResponse response = departmentService.getDepartment("dept-1");

        assertThat(response.status()).isFalse();
        assertThat(response.id()).isEqualTo("dept-1");
    }

    @Test
    void setDepartmentHead_activeAdmin_succeedsAndLogs() {
        Department dept = department(true);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(List.of());
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser("admin-1", true, "ADMIN")));

        DepartmentResponse response = departmentService.setDepartmentHead("actor-1", "dept-1", "admin-1");

        assertThat(response.headUserId()).isEqualTo("admin-1");
        assertThat(dept.getHeadUserId()).isEqualTo("admin-1");
        verify(activityLogService).logDepartmentHeadAssigned("actor-1", "dept-1", null, "admin-1");
    }

    @Test
    void setDepartmentHead_adminAgent_succeeds() {
        Department dept = department(true);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(List.of());
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(adminUser("agent-1", true, "ADMIN_AGENT")));

        DepartmentResponse response = departmentService.setDepartmentHead("actor-1", "dept-1", "agent-1");

        assertThat(response.headUserId()).isEqualTo("agent-1");
    }

    @Test
    void setDepartmentHead_reassigningOverExistingHead_replacesSilently() {
        Department dept = department(true);
        dept.setHeadUserId("old-head");
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(List.of());
        when(userRepository.findById("new-head")).thenReturn(Optional.of(adminUser("new-head", true, "ADMIN")));

        DepartmentResponse response = departmentService.setDepartmentHead("actor-1", "dept-1", "new-head");

        assertThat(response.headUserId()).isEqualTo("new-head");
        verify(activityLogService).logDepartmentHeadAssigned("actor-1", "dept-1", "old-head", "new-head");
    }

    @Test
    void setDepartmentHead_nonAdminRole_throws400() {
        Department dept = department(true);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(userRepository.findById("agent-user")).thenReturn(Optional.of(adminUser("agent-user", true, "AGENT")));

        assertThatThrownBy(() -> departmentService.setDepartmentHead("actor-1", "dept-1", "agent-user"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Department head must be an Admin or Admin-Agent")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void setDepartmentHead_inactiveUser_throws404() {
        Department dept = department(true);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser("admin-1", false, "ADMIN")));

        assertThatThrownBy(() -> departmentService.setDepartmentHead("actor-1", "dept-1", "admin-1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("User not found or inactive")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void setDepartmentHead_departmentNotFound_throws404() {
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.setDepartmentHead("actor-1", "dept-1", "admin-1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Department not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void removeDepartmentHead_clearsFieldAndLogs() {
        Department dept = department(true);
        dept.setHeadUserId("admin-1");
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(List.of());

        DepartmentResponse response = departmentService.removeDepartmentHead("actor-1", "dept-1");

        assertThat(response.headUserId()).isNull();
        verify(activityLogService).logDepartmentHeadRemoved("actor-1", "dept-1", "admin-1");
    }

    @Test
    void removeDepartmentHead_noExistingHead_doesNotLog() {
        Department dept = department(true);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", true)).thenReturn(List.of());

        DepartmentResponse response = departmentService.removeDepartmentHead("actor-1", "dept-1");

        assertThat(response.headUserId()).isNull();
        verify(activityLogService, never()).logDepartmentHeadRemoved(any(), any(), any());
    }
}
