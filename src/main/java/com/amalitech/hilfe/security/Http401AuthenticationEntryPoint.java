package com.amalitech.hilfe.security;
import com.amalitech.hilfe.constants.ApiMessages;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;
@Component
public class Http401AuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @Override    public void commence(HttpServletRequest request, HttpServletResponse response,
                                      AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MAPPER.writeValue(response.getWriter(),
                Map.of("error", ApiMessages.AUTHENTICATION_REQUIRED));
    }}