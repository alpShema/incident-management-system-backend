package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.config.GraphQlCoercionErrorInstrumentation;
import com.amalitech.hilfe.services.LocationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

// HV-1648: a malformed LocalTime literal must never leak the raw graphql-java validation
// message (WrongType classification, AST StringValue dump, java.time parser text) to the client.
@GraphQlTest(LocationResolver.class)
@Import({
        GraphQlCoercionErrorInstrumentationTest.MethodSecurityTestConfig.class,
        GraphQlConfig.class,
        GraphQlCoercionErrorInstrumentation.class
})
class GraphQlCoercionErrorInstrumentationTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired GraphQlTester graphQlTester;
    @MockitoBean LocationService locationService;

    private static final String MUTATION = """
            mutation {
              updateLocation(id: "loc-1", input: { businessHoursStart: "09:00:00", businessHoursEnd: "not:a:time" }) {
                id
              }
            }
            """;

    @BeforeEach
    void bindRequestContext() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void setAuthority(String authority) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void updateLocation_malformedBusinessHoursEnd_returnsSanitizedMessage() {
        setAuthority("location.update");

        graphQlTester.document(MUTATION)
                .execute()
                .errors()
                .expect(error -> {
                    String message = error.getMessage();
                    return message != null
                            && message.contains("businessHoursEnd")
                            && !message.contains("WrongType")
                            && !message.contains("StringValue")
                            && !message.contains("could not be parsed");
                })
                .verify();
    }
}
