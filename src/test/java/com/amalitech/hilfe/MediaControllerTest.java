package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.MediaController;
import com.amalitech.hilfe.dto.PresignedUrlRequest;
import com.amalitech.hilfe.dto.PresignedUrlResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.MediaService;
import com.amalitech.hilfe.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class MediaControllerTest {

    @Autowired MockMvc mvc;
    final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean MediaService mediaService;
    @MockitoBean TokenService tokenService;

    private JwtTokenService.AuthPrincipal clientPrincipal() {
        return new JwtTokenService.AuthPrincipal("user-1", "user@test.com", RoleCode.CLIENT);
    }

    // ── POST /media/presigned-url ────────────────────────────────────────────

    @Test
    void generatePresignedUrl_validRequest_returns200() throws Exception {
        when(mediaService.generatePresignedUploadUrl(any(PresignedUrlRequest.class)))
                .thenReturn(new PresignedUrlResponse("https://s3.example.com/put", "media/uuid/file.png", 900));

        var principal = clientPrincipal();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "incident.create"));

        mvc.perform(post("/media/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PresignedUrlRequest("file.png", "image/png", 1024L)))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Presigned URL generated successfully"))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://s3.example.com/put"))
                .andExpect(jsonPath("$.data.fileKey").value("media/uuid/file.png"));
    }

    @Test
    void generatePresignedUrl_missingFileName_returns400() throws Exception {
        var principal = clientPrincipal();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "incident.create"));

        mvc.perform(post("/media/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PresignedUrlRequest("", "image/png", 1024L)))
                        .with(authentication(auth)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generatePresignedUrl_noAuth_returns401() throws Exception {
        mvc.perform(post("/media/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PresignedUrlRequest("file.png", "image/png", 1024L))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generatePresignedUrl_wrongPermission_returns403() throws Exception {
        var principal = clientPrincipal();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "some.other.permission"));

        mvc.perform(post("/media/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PresignedUrlRequest("file.png", "image/png", 1024L)))
                        .with(authentication(auth)))
                .andExpect(status().isForbidden());
    }
}
