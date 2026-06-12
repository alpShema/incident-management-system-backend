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

import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(agentRepository.findAllActiveWithUserAndQuery(null, pageable))
                .thenReturn(new PageImpl<>(List.of(agent("a1", true)), pageable, 1));

        var result = agentService.listAgents(null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(agentRepository).findAllActiveWithUserAndQuery(null, pageable);
        verify(agentRepository, never()).findByDepartmentIdWithUserAndQuery("dept-1", null, pageable);
    }

    @Test
    void listAllAgents_withoutDepartment_usesAllStatusesQuery() {
        var pageable = PageRequest.of(0, 10);
        when(agentRepository.findAllWithUserAndQuery(null, pageable))
                .thenReturn(new PageImpl<>(List.of(agent("a1", true), agent("a2", false)), pageable, 2));

        var result = agentService.listAllAgents(null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(agentRepository).findAllWithUserAndQuery(null, pageable);
        verify(agentRepository, never()).findAllActiveWithUserAndQuery(null, pageable);
    }

    @Test
    void listAgents_withActiveDepartment_usesDepartmentQuery() {
        var pageable = PageRequest.of(0, 10);
        when(departmentRepository.findById("dept-1"))
                .thenReturn(Optional.of(Department.builder().id("dept-1").status(true).build()));
        when(agentRepository.findByDepartmentIdWithUserAndQuery("dept-1", null, pageable))
                .thenReturn(new PageImpl<>(List.of(agent("a1", true), agent("a2", false)), pageable, 2));

        var result = agentService.listAgents("dept-1", null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(agentRepository).findByDepartmentIdWithUserAndQuery("dept-1", null, pageable);
    }

    @Test
    void listAgents_withInactiveDepartment_returnsEmptyPage() {
        var pageable = PageRequest.of(0, 10);
        when(departmentRepository.findById("dept-1"))
                .thenReturn(Optional.of(Department.builder().id("dept-1").status(false).build()));

        var result = agentService.listAgents("dept-1", null, pageable);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        verify(agentRepository, never()).findByDepartmentIdWithUserAndQuery("dept-1", null, pageable);
    }

    @Test
    void listAgents_withMissingDepartment_returnsEmptyPage() {
        var pageable = PageRequest.of(0, 10);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.empty());

        var result = agentService.listAgents("dept-1", null, pageable);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        verify(agentRepository, never()).findByDepartmentIdWithUserAndQuery("dept-1", null, pageable);
    }

    @Test
    void listAllAgents_withActiveDepartment_usesDepartmentQuery() {
        var pageable = PageRequest.of(0, 10);
        when(departmentRepository.findById("dept-1"))
                .thenReturn(Optional.of(Department.builder().id("dept-1").status(true).build()));
        when(agentRepository.findByDepartmentIdWithUserAndQuery("dept-1", null, pageable))
                .thenReturn(new PageImpl<>(List.of(agent("a1", true), agent("a2", false)), pageable, 2));

        var result = agentService.listAllAgents("dept-1", null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(agentRepository).findByDepartmentIdWithUserAndQuery("dept-1", null, pageable);
    }

    @Test
    void listAllAgents_withInactiveDepartment_returnsEmptyPage() {
        var pageable = PageRequest.of(0, 10);
        when(departmentRepository.findById("dept-1"))
                .thenReturn(Optional.of(Department.builder().id("dept-1").status(false).build()));

        var result = agentService.listAllAgents("dept-1", null, pageable);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        verify(agentRepository, never()).findByDepartmentIdWithUserAndQuery("dept-1", null, pageable);
    }

    @Test
    void listAgents_withQuery_buildsEscapedPatternForActiveSearch() {
        var pageable = PageRequest.of(0, 10);
        when(agentRepository.findAllActiveWithUserAndQuery("%john!_doe!%%", pageable))
                .thenReturn(new PageImpl<>(List.of(agent("a1", true)), pageable, 1));

        var result = agentService.listAgents(null, "john_doe%", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(agentRepository).findAllActiveWithUserAndQuery("%john!_doe!%%", pageable);
    }

    @Test
    void listAgents_withDepartmentAndQuery_usesDepartmentSearch() {
        var pageable = PageRequest.of(0, 10);
        when(departmentRepository.findById("dept-1"))
                .thenReturn(Optional.of(Department.builder().id("dept-1").status(true).build()));
        when(agentRepository.findByDepartmentIdWithUserAndQuery("dept-1", "%takoradi%", pageable))
                .thenReturn(new PageImpl<>(List.of(agent("a1", true)), pageable, 1));

        var result = agentService.listAgents("dept-1", "Takoradi", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(agentRepository).findByDepartmentIdWithUserAndQuery("dept-1", "%takoradi%", pageable);
    }

    @Test
    void listAllAgents_withQuery_usesAllStatusesSearch() {
        var pageable = PageRequest.of(0, 10);
        when(agentRepository.findAllWithUserAndQuery("%farida%", pageable))
                .thenReturn(new PageImpl<>(List.of(agent("a1", true), agent("a2", false)), pageable, 2));

        var result = agentService.listAllAgents(null, "Farida", pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(agentRepository).findAllWithUserAndQuery("%farida%", pageable);
    }

    // ── getStatus ─────────────────────────────────────────────────────────────

    @Test
    void getStatus_returnsAgentResponse() {
        when(agentRepository.findByUserIdWithUser("user-1"))
                .thenReturn(Optional.of(agent("a1", true)));

        AgentResponse result = agentService.getStatus("user-1");

        assertThat(result.status()).isTrue();
        assertThat(result.userId()).isEqualTo("user-a1");
    }

    @Test
    void getStatus_agentNotFound_throws404() {
        when(agentRepository.findByUserIdWithUser("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentService.getStatus("user-1"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void listAgents_blankQuery_behavesLikeNull() {
        var pageable = PageRequest.of(0, 10);
        when(agentRepository.findAllActiveWithUserAndQuery(null, pageable))
                .thenReturn(new PageImpl<>(List.of(agent("a1", true)), pageable, 1));

        var result = agentService.listAgents(null, "   ", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(agentRepository).findAllActiveWithUserAndQuery(null, pageable);
    }
}
