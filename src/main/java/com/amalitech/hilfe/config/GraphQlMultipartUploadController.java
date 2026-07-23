package com.amalitech.hilfe.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Spring for GraphQL has no built-in support for the graphql-multipart-request-spec
// (the protocol Apollo Upload Client and similar tools use to send real file uploads
// over GraphQL). This controller is a minimal, spec-compatible entry point for it.
//
// It lives at its own path ("/graphql/upload") rather than the standard JSON endpoint's
// "/graphql" path. Mapping it to the same path as the JSON endpoint, differentiated only
// by `consumes = multipart/form-data`, was tried and does NOT work in practice: the JSON
// endpoint is registered as a RouterFunction, and Spring MVC's RouterFunctionMapping is
// consulted before RequestMappingHandlerMapping — when it finds a path match with no
// content-type match, it short-circuits straight to an HTTP 415 response instead of
// falling through to let this annotated controller handle the request. A distinct path
// sidesteps that HandlerMapping precedence entirely. (See SecurityConfig, which permits
// this path the same way it permits "/graphql", and GraphQlResponseFilter, which wraps
// this path's responses the same way it wraps the JSON endpoint's.)
//
// It reconstructs a WebGraphQlRequest from the multipart parts and hands it to the same
// WebGraphQlHandler bean the JSON endpoint uses, so every existing behavior — @PreAuthorize
// method security, GraphQlExceptionResolver's error mapping, the registered scalars — keeps
// working unchanged. It deliberately only supports what our two upload operations need: one
// file per multipart field, mapped to a single top-level "variables.<name>" path — not the
// full generality of the multipart spec (nested paths, multiple files per variable, batching).
@RestController
@RequiredArgsConstructor
public class GraphQlMultipartUploadController {

    private final WebGraphQlHandler webGraphQlHandler;
    private final ObjectMapper objectMapper;

    @PostMapping(path = "${spring.graphql.path:/graphql}/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handle(
            @RequestParam("operations") String operationsJson,
            @RequestParam("map") String mapJson,
            MultipartHttpServletRequest multipartRequest,
            HttpServletRequest servletRequest) {

        Map<String, Object> operations;
        Map<String, List<String>> fileMap;
        try {
            operations = objectMapper.readValue(operationsJson, new TypeReference<Map<String, Object>>() {});
            fileMap = objectMapper.readValue(mapJson, new TypeReference<Map<String, List<String>>>() {});
        } catch (IOException e) {
            return ResponseEntity.ok(errorEnvelope("The 'operations' or 'map' part is not valid JSON."));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> variables =
                (Map<String, Object>) operations.computeIfAbsent("variables", k -> new LinkedHashMap<>());

        for (Map.Entry<String, List<String>> entry : fileMap.entrySet()) {
            MultipartFile file = multipartRequest.getFile(entry.getKey());
            if (file == null) {
                return ResponseEntity.ok(errorEnvelope("No file part found for multipart field '" + entry.getKey() + "'."));
            }
            List<String> paths = entry.getValue();
            if (paths == null || paths.isEmpty()) {
                return ResponseEntity.ok(errorEnvelope("Multipart 'map' entry for '" + entry.getKey() + "' has no target path."));
            }
            for (String path : paths) {
                String variableName = topLevelVariableName(path);
                if (variableName == null) {
                    return ResponseEntity.ok(errorEnvelope("Unsupported upload path '" + path
                            + "'; only top-level 'variables.<name>' paths are supported."));
                }
                variables.put(variableName, file);
            }
        }

        WebGraphQlRequest request = new WebGraphQlRequest(
                URI.create(servletRequest.getRequestURL().toString()),
                new ServletServerHttpRequest(servletRequest).getHeaders(),
                readCookies(servletRequest),
                Map.of(),
                operations,
                UUID.randomUUID().toString(),
                servletRequest.getLocale());

        WebGraphQlResponse response = webGraphQlHandler.handleRequest(request).block();
        return ResponseEntity.ok(response.toMap());
    }

    private String topLevelVariableName(String path) {
        String[] parts = path.split("\\.");
        return (parts.length == 2 && "variables".equals(parts[0])) ? parts[1] : null;
    }

    private Map<String, Object> errorEnvelope(String message) {
        // Map.of(...) rejects null values, and "data" must be null here, so build this by hand.
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", null);
        envelope.put("errors", List.of(Map.of(
                "message", message,
                "extensions", Map.of(
                        "status", 400,
                        "error", "Bad Request",
                        "timestamp", Instant.now().toString()))));
        return envelope;
    }

    private MultiValueMap<String, HttpCookie> readCookies(HttpServletRequest request) {
        MultiValueMap<String, HttpCookie> cookies = new LinkedMultiValueMap<>();
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                cookies.add(cookie.getName(), new HttpCookie(cookie.getName(), cookie.getValue()));
            }
        }
        return cookies;
    }
}
