package com.amalitech.hilfe.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Http401AuthenticationEntryPointTest {

    private final Http401AuthenticationEntryPoint entryPoint = new Http401AuthenticationEntryPoint();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void commence_returns401WithJsonErrorBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");

        @SuppressWarnings("unchecked")
        Map<String, String> body = mapper.readValue(response.getContentAsString(), Map.class);
        assertThat(body.get("error"))
                .isEqualTo("Authentication required. Please log in to access this resource.");
    }
}
