package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.DepartmentRequest;
import com.amalitech.hilfe.dto.DepartmentResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Department;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.repositories.IncidentCategoryRepository;
import com.amalitech.hilfe.services.DepartmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock DepartmentRepository departmentRepository;
    @Mock AgentGroupRepository agentGroupRepository;
    @Mock IncidentCategoryRepository categoryRepository;
    @Mock EntityManager entityManager;
    @InjectMocks DepartmentService departmentService;

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
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", "active")).thenReturn(java.util.List.of());

        DepartmentResponse response = departmentService.updateDepartmentStatus("dept-1", true);

        assertThat(response.status()).isTrue();
        verify(departmentRepository).save(dept);
    }

    @Test
    void updateDepartmentStatus_deactivate_noLinks_succeeds() {
        Department dept = department(true);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", "active")).thenReturn(java.util.List.of());

        DepartmentResponse response = departmentService.updateDepartmentStatus("dept-1", false);

        assertThat(response.status()).isFalse();
        verify(departmentRepository).save(dept);
    }

    @Test
    void updateDepartmentStatus_deactivate_withCategories_succeeds() {
        Department dept = department(true);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", "active"))
                .thenReturn(java.util.List.of(mock(com.amalitech.hilfe.models.IncidentCategory.class)));

        DepartmentResponse response = departmentService.updateDepartmentStatus("dept-1", false);

        assertThat(response.status()).isFalse();
        assertThat(response.categoryCount()).isEqualTo(1L);
        verify(departmentRepository).save(dept);
    }

    @Test
    void updateDepartmentStatus_deactivate_withActiveAgentGroups_succeeds() {
        Department dept = department(true);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", "active")).thenReturn(java.util.List.of());

        DepartmentResponse response = departmentService.updateDepartmentStatus("dept-1", false);

        assertThat(response.status()).isFalse();
        verify(departmentRepository).save(dept);
        verifyNoInteractions(agentGroupRepository);
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

        DepartmentResponse response = departmentService.createDepartment(
                new DepartmentRequest("  Facilities  ",  "  Facilities dept  "));

        assertThat(response.name()).isEqualTo("Facilities");
        assertThat(response.description()).isEqualTo("Facilities dept");
        var captor = org.mockito.ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Facilities");
        assertThat(captor.getValue().getDescription()).isEqualTo("Facilities dept");
    }

    @Test
    void updateDepartment_trimsNameAndDescription() {
        Department dept = department(false);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(departmentRepository.existsByNameIgnoreCase("Facilities Updated")).thenReturn(false);
        when(departmentRepository.save(dept)).thenReturn(dept);
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", "active")).thenReturn(java.util.List.of());

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
        when(categoryRepository.findByDepartmentIdAndStatus("dept-1", "active")).thenReturn(java.util.List.of());

        DepartmentResponse response = departmentService.getDepartment("dept-1");

        assertThat(response.status()).isFalse();
        assertThat(response.id()).isEqualTo("dept-1");
    }
}
