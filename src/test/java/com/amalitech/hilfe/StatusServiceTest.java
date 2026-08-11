package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.StatusLookupResponse;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.Status;
import com.amalitech.hilfe.repositories.StatusRepository;
import com.amalitech.hilfe.services.StatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusServiceTest {

    @Mock StatusRepository statusRepository;
    @InjectMocks StatusService statusService;

    @Test
    void listStatuses_mapsToLookupResponses() {
        Status open = Status.builder().id("status-open").name("Open").description("Newly created").build();
        Status closed = Status.builder().id("status-closed").name("Closed").description("Resolved").build();
        when(statusRepository.findAll()).thenReturn(List.of(open, closed));

        List<StatusLookupResponse> result = statusService.listStatuses(RoleCode.CLIENT);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("status-open");
        assertThat(result.get(0).name()).isEqualTo("Open");
        assertThat(result.get(1).id()).isEqualTo("status-closed");
        assertThat(result.get(1).name()).isEqualTo("Closed");
    }

    @Test
    void listStatuses_agentRole_excludesUnassignedStatus() {
        Status open = Status.builder().id("status-open").name("Open").build();
        Status unassigned = Status.builder().id("status-unassigned").name("Unassigned").build();
        Status closed = Status.builder().id("status-closed").name("Closed").build();
        when(statusRepository.findAll()).thenReturn(List.of(open, unassigned, closed));

        List<StatusLookupResponse> result = statusService.listStatuses(RoleCode.AGENT);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(StatusLookupResponse::id).doesNotContain("status-unassigned");
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"CLIENT", "ADMIN", "ADMIN_AGENT", "SUPER_ADMIN"})
    void listStatuses_nonAgentRoles_includeUnassignedStatus(RoleCode role) {
        Status open = Status.builder().id("status-open").name("Open").build();
        Status unassigned = Status.builder().id("status-unassigned").name("Unassigned").build();
        Status closed = Status.builder().id("status-closed").name("Closed").build();
        when(statusRepository.findAll()).thenReturn(List.of(open, unassigned, closed));

        List<StatusLookupResponse> result = statusService.listStatuses(role);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(StatusLookupResponse::id).contains("status-unassigned");
    }

    @Test
    void listStatuses_nullRole_includesUnassignedStatus() {
        Status unassigned = Status.builder().id("status-unassigned").name("Unassigned").build();
        when(statusRepository.findAll()).thenReturn(List.of(unassigned));

        List<StatusLookupResponse> result = statusService.listStatuses(null);

        assertThat(result).extracting(StatusLookupResponse::id).contains("status-unassigned");
    }
}
