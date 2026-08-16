package com.amalitech.hilfe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GraphQlResponseFilter extends OncePerRequestFilter {

    private static final String GRAPHQL_PATH = "/graphql";
    // The multipart-upload entry point (GraphQlMultipartUploadController) lives at its own
    // path rather than /graphql itself -- see that class for why -- but its response must
    // still get the same {message, data, errors} envelope as the standard JSON endpoint.
    private static final String GRAPHQL_UPLOAD_PATH = "/graphql/upload";
    private static final String MESSAGE_KEY = "message";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.endsWith(GRAPHQL_PATH) && !uri.endsWith(GRAPHQL_UPLOAD_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, responseWrapper);

        byte[] body = responseWrapper.getContentAsByteArray();
        if (body.length == 0) {
            responseWrapper.copyBodyToResponse();
            return;
        }

        String contentType = response.getContentType();
        if (contentType == null || !contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            responseWrapper.copyBodyToResponse();
            return;
        }

        try {
            String raw = new String(body, StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> gqlResponse = OBJECT_MAPPER.readValue(raw, Map.class);

            Object data = gqlResponse.get("data");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors = (List<Map<String, Object>>) gqlResponse.get("errors");

            if (errors != null) {
                for (Map<String, Object> error : errors) {
                    patchDefaultExtensions(error);
                }
            }

            Map<String, Object> envelope = buildEnvelope(data, errors, request);
            byte[] wrapped = OBJECT_MAPPER.writeValueAsBytes(envelope);
            responseWrapper.resetBuffer();
            response.setContentLength(wrapped.length);
            response.getOutputStream().write(wrapped);
        } catch (Exception e) {
            log.warn("Failed to wrap GraphQL response: {}", e.getMessage());
            responseWrapper.copyBodyToResponse();
        }
    }

    @SuppressWarnings("unchecked")
    private void patchDefaultExtensions(Map<String, Object> error) {
        Map<String, Object> extensions = (Map<String, Object>) error.get("extensions");
        if (extensions == null || !extensions.containsKey("status")) {
            if (extensions == null) {
                extensions = new LinkedHashMap<>();
                error.put("extensions", extensions);
            }
            extensions.putIfAbsent("status", 400);
            extensions.putIfAbsent("error", "Bad Request");
            extensions.putIfAbsent("timestamp", Instant.now().toString());
            extensions.putIfAbsent("path", GRAPHQL_PATH);
        }
    }

    private Map<String, Object> buildEnvelope(Object data, List<Map<String, Object>> errors, HttpServletRequest request) {
        Map<String, Object> envelope = new LinkedHashMap<>();

        if (errors != null && !errors.isEmpty()) {
            String firstMessage = (String) errors.getFirst().get(MESSAGE_KEY);
            envelope.put(MESSAGE_KEY, firstMessage != null ? firstMessage : "An error occurred");
            envelope.put("data", data);
            envelope.put("errors", errors);
        } else {
            Object resolverMessage = request.getAttribute(GraphQlResponseMessage.ATTRIBUTE_NAME);
            envelope.put(MESSAGE_KEY, resolverMessage instanceof String message ? message : "Success");
            envelope.put("data", data);
        }

        return envelope;
    }
}
