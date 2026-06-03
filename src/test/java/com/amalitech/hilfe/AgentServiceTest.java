package com.amalitech.hilfe;

import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Department;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.services.AgentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock AgentRepository agentRepository;
    @Mock DepartmentRepository departmentRepository;
    @InjectMocks AgentService agentService;

    private Agent agent(String id, boolean status) {
        Agent a = Agent.builder()
                .id(id)
                .userId("user-" + id)
                .status(status)
                .build();
        a.setUser(User.builder().id("user-" + id).fullName("Agent " + id).email(id + "@test.com").build());
        return a;
    }

    @Test
    void listAgents_withoutDepartment_usesActiveOnlyQuery() {
        var pageable = PageRequest.of(0, 10);
        when(agentRepository.findAllActiveWithUser(pageable))
                .thenReturn(new PageImpl<>(List.of(agent("a1", true)), pageable, 1));

        var result = agentService.listAgents(null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(agentRepository).findAllActiveWithUser(pageable);
        verify(agentRepository, never()).findByDepartmentIdWithUser("dept-1", pageable);
    }

    @Test
    void listAllAgents_withoutDepartment_usesAllStatusesQuery() {
        var pageable = PageRequest.of(0, 10);
        when(agentRepository.findAllWithUser(pageable))
                .thenReturn(new PageImpl<>(List.of(agent("a1", true), agent("a2", false)), pageable, 2));

        var result = agentService.listAllAgents(null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(agentRepository).findAllWithUser(pageable);
        verify(agentRepository, never()).findAllActiveWithUser(pageable);
    }

    @Test
    void listAgents_withActiveDepartment_usesDepartmentQuery() {
        var pageable = PageRequest.of(0, 10);
        when(departmentRepository.findById("dept-1"))
                .thenReturn(Optional.of(Department.builder().id("dept-1").status(true).build()));
        when(agentRepository.findByDepartmentIdWithUser("dept-1", pageable))
                .thenReturn(new PageImpl<>(List.of(agent("a1", true), agent("a2", false)), pageable, 2));

        var result = agentService.listAgents("dept-1", pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(agentRepository).findByDepartmentIdWithUser("dept-1", pageable);
    }

    @Test
    void listAgents_withInactiveDepartment_returnsEmptyPage() {
        var pageable = PageRequest.of(0, 10);
        when(departmentRepository.findById("dept-1"))
                .thenReturn(Optional.of(Department.builder().id("dept-1").status(false).build()));

        var result = agentService.listAgents("dept-1", pageable);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        verify(agentRepository, never()).findByDepartmentIdWithUser("dept-1", pageable);
    }

    @Test
    void listAgents_withMissingDepartment_returnsEmptyPage() {
        var pageable = PageRequest.of(0, 10);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.empty());

        var result = agentService.listAgents("dept-1", pageable);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        verify(agentRepository, never()).findByDepartmentIdWithUser("dept-1", pageable);
    }

    @Test
    void listAllAgents_withActiveDepartment_usesDepartmentQuery() {
        var pageable = PageRequest.of(0, 10);
        when(departmentRepository.findById("dept-1"))
                .thenReturn(Optional.of(Department.builder().id("dept-1").status(true).build()));
        when(agentRepository.findByDepartmentIdWithUser("dept-1", pageable))
                .thenReturn(new PageImpl<>(List.of(agent("a1", true), agent("a2", false)), pageable, 2));

        var result = agentService.listAllAgents("dept-1", pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(agentRepository).findByDepartmentIdWithUser("dept-1", pageable);
    }

    @Test
    void listAllAgents_withInactiveDepartment_returnsEmptyPage() {
        var pageable = PageRequest.of(0, 10);
        when(departmentRepository.findById("dept-1"))
                .thenReturn(Optional.of(Department.builder().id("dept-1").status(false).build()));

        var result = agentService.listAllAgents("dept-1", pageable);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        verify(agentRepository, never()).findByDepartmentIdWithUser("dept-1", pageable);
    }
}
