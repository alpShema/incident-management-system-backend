package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.MessageController;
import com.amalitech.hilfe.dto.MediaResponse;
import com.amalitech.hilfe.dto.MessageResponse;
import com.amalitech.hilfe.dto.PresignedUrlRequest;
import com.amalitech.hilfe.dto.PresignedUrlResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.MessageService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class MessageControllerTest {

    @Autowired MockMvc mvc;
    final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean MessageService messageService;
    @MockitoBean TokenService tokenService;

    private UsernamePasswordAuthenticationToken auth() {
        var principal = new JwtTokenService.AuthPrincipal("u1", "u1@test.com", RoleCode.CLIENT);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_CLIENT"));
    }

    @Test
    void generateMessagePresignedUrl_returns200() throws Exception {
        when(messageService.generateMessagePresignedUrl(anyString(), anyString(), eq("inc-1"), any(PresignedUrlRequest.class)))
                .thenReturn(new PresignedUrlResponse("https://upload", "messages/abc/file.png", 900));

        mvc.perform(post("/incidents/inc-1/messages/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PresignedUrlRequest("file.png", "image/png", 1024L)))
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Presigned URL generated successfully"))
                .andExpect(jsonPath("$.data.fileKey").value("messages/abc/file.png"));
    }

    @Test
    void generateMessagePresignedUrl_unauthorisedAgent_returns403() throws Exception {
        var agentPrincipal = new JwtTokenService.AuthPrincipal("u-teammate", "teammate@test.com", RoleCode.AGENT);
        var agentAuth = new UsernamePasswordAuthenticationToken(agentPrincipal, null, List.of(() -> "ROLE_AGENT"));

        when(messageService.generateMessagePresignedUrl(anyString(), anyString(), eq("inc-1"), any(PresignedUrlRequest.class)))
                .thenThrow(new ArmsAuthException("You do not have access to this incident", 403));

        mvc.perform(post("/incidents/inc-1/messages/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PresignedUrlRequest("file.png", "image/png", 1024L)))
                        .with(authentication(agentAuth)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this incident"));
    }

    @Test
    void sendMessage_withAttachments_returns201AndAttachments() throws Exception {
        MessageResponse response = new MessageResponse(
                "msg-1",
                "inc-1",
                new MessageResponse.SenderInfo("u1", "Jane Doe", null),
                null,
                List.of(new MediaResponse("mm-1", "img.png", "image/png", 1024L, "https://download")),
                Instant.now(),
                Instant.now()
        );
        when(messageService.sendMessage(anyString(), anyString(), eq("inc-1"), isNull(), anyList()))
                .thenReturn(response);

        mvc.perform(post("/incidents/inc-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": null,
                                  "attachments": [
                                    {
                                      "fileKey": "messages/abc/img.png",
                                      "originalName": "img.png",
                                      "contentType": "image/png",
                                      "fileSize": 1024
                                    }
                                  ]
                                }
                                """)
                        .with(authentication(auth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Message sent"))
                .andExpect(jsonPath("$.data.id").value("msg-1"))
                .andExpect(jsonPath("$.data.attachments[0].originalName").value("img.png"));
    }

    @Test
    void sendMessage_emptyPayload_returns400FromServiceValidation() throws Exception {
        when(messageService.sendMessage(anyString(), anyString(), eq("inc-1"), any(), any()))
                .thenThrow(new ArmsAuthException("Either content or attachments must be provided", 400));

        mvc.perform(post("/incidents/inc-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \",\"attachments\":[]}")
                        .with(authentication(auth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Either content or attachments must be provided"));
    }
}
