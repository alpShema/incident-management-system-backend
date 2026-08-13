package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.dto.TimezoneOptionResponse;
import com.amalitech.hilfe.services.TimezoneService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@GraphQlTest(TimezoneResolver.class)
@Import(GraphQlConfig.class)
class TimezoneResolverTest {

    @Autowired GraphQlTester graphQlTester;
    @MockitoBean TimezoneService timezoneService;

    private static final String QUERY = """
            query {
              timezones {
                id
                label
                offsetNow
              }
            }
            """;

    private static final String QUERY_WITH_SEARCH = """
            query {
              timezones(query: "kigali") {
                id
                label
                offsetNow
              }
            }
            """;

    @Test
    void timezones_returnsListWithNoAuthorityRequired() {
        when(timezoneService.listTimezones(null)).thenReturn(List.of(
                new TimezoneOptionResponse("Africa/Kigali", "Kigali", "+02:00")));

        TimezoneOptionResponse first = graphQlTester.document(QUERY)
                .execute()
                .path("timezones[0]").entity(TimezoneOptionResponse.class).get();

        assertThat(first).isEqualTo(new TimezoneOptionResponse("Africa/Kigali", "Kigali", "+02:00"));
    }

    @Test
    void timezones_withQueryArgument_passesSearchTermToService() {
        when(timezoneService.listTimezones("kigali")).thenReturn(List.of(
                new TimezoneOptionResponse("Africa/Kigali", "Kigali", "+02:00")));

        TimezoneOptionResponse first = graphQlTester.document(QUERY_WITH_SEARCH)
                .execute()
                .path("timezones[0]").entity(TimezoneOptionResponse.class).get();

        assertThat(first).isEqualTo(new TimezoneOptionResponse("Africa/Kigali", "Kigali", "+02:00"));
    }
}
