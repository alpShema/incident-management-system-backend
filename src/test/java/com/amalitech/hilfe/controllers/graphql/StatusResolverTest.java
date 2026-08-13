package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.dto.StatusLookupResponse;
import com.amalitech.hilfe.services.StatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@GraphQlTest(StatusResolver.class)
@Import(GraphQlConfig.class)
class StatusResolverTest {

    @Autowired GraphQlTester graphQlTester;
    @MockitoBean StatusService statusService;

    private static final String QUERY = """
            query {
              statuses {
                id
                name
                description
              }
            }
            """;

    // HV-1653: the statuses query must return the complete, role-agnostic list -- including
    // Unassigned -- with no authority required, matching the other reference-data lookups
    // (severities, timezones, ...).
    @Test
    void statuses_returnsListWithNoAuthorityRequired_includingUnassigned() {
        when(statusService.listStatuses()).thenReturn(List.of(
                new StatusLookupResponse("status-open", "Open", "Newly created"),
                new StatusLookupResponse("status-unassigned", "Unassigned", "Incident has not yet been assigned to an agent."),
                new StatusLookupResponse("status-closed", "Closed", "Resolved")
        ));

        List<StatusLookupResponse> result = graphQlTester.document(QUERY)
                .execute()
                .path("statuses")
                .entityList(StatusLookupResponse.class)
                .get();

        assertThat(result).extracting(StatusLookupResponse::id)
                .containsExactly("status-open", "status-unassigned", "status-closed");
        verify(statusService).listStatuses();
    }
}
