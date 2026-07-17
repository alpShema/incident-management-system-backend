package com.amalitech.hilfe.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GraphQlResponseFilterTest {

    private final GraphQlResponseFilter filter = new GraphQlResponseFilter();

    private void writeGraphQlBody(MockHttpServletRequest request, MockHttpServletResponse response, String body) throws Exception {
        FilterChain chain = (req, res) -> {
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        };
        filter.doFilter(request, response, chain);
    }

    @Test
    void successResponse_withResolverMessage_usesResolverMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/graphql");
        request.setAttribute(GraphQlResponseMessage.ATTRIBUTE_NAME, "Agent status updated successfully");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writeGraphQlBody(request, response, "{\"data\":{\"updateAgentStatus\":{\"id\":\"a1\"}}}");

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"message\":\"Agent status updated successfully\"");
    }

    @Test
    void successResponse_withoutResolverMessage_fallsBackToGenericSuccess() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/graphql");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writeGraphQlBody(request, response, "{\"data\":{\"someQuery\":{\"id\":\"a1\"}}}");

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"message\":\"Success\"");
    }

    @Test
    void errorResponse_ignoresResolverMessage_usesErrorMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/graphql");
        request.setAttribute(GraphQlResponseMessage.ATTRIBUTE_NAME, "Agent status updated successfully");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writeGraphQlBody(request, response,
                "{\"data\":{\"updateAgentStatus\":null},\"errors\":[{\"message\":\"Agent not found\",\"path\":[\"updateAgentStatus\"]}]}");

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"message\":\"Agent not found\"");
        assertThat(body).doesNotContain("Agent status updated successfully");
    }
}
