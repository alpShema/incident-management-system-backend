package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.StatusLookupResponse;
import com.amalitech.hilfe.models.Status;
import com.amalitech.hilfe.repositories.StatusRepository;
import com.amalitech.hilfe.services.StatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

        List<StatusLookupResponse> result = statusService.listStatuses();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("status-open");
        assertThat(result.get(0).name()).isEqualTo("Open");
        assertThat(result.get(1).id()).isEqualTo("status-closed");
        assertThat(result.get(1).name()).isEqualTo("Closed");
    }

    // HV-1653: the statuses lookup must always include Unassigned, with no role-based
    // special-casing -- it is sourced straight from StatusRepository like every other status.
    @Test
    void listStatuses_includesUnassignedStatus() {
        Status open = Status.builder().id("status-open").name("Open").build();
        Status unassigned = Status.builder().id("status-unassigned").name("Unassigned").description("Incident has not yet been assigned to an agent.").build();
        Status closed = Status.builder().id("status-closed").name("Closed").build();
        when(statusRepository.findAll()).thenReturn(List.of(open, unassigned, closed));

        List<StatusLookupResponse> result = statusService.listStatuses();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(StatusLookupResponse::id)
                .containsExactly("status-open", "status-unassigned", "status-closed");
        assertThat(result).filteredOn(s -> s.id().equals("status-unassigned"))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.name()).isEqualTo("Unassigned");
                    assertThat(s.description()).isEqualTo("Incident has not yet been assigned to an agent.");
                });
    }

    @Test
    void listStatuses_repositoryEmpty_returnsEmptyList() {
        when(statusRepository.findAll()).thenReturn(List.of());

        assertThat(statusService.listStatuses()).isEmpty();
    }
}
