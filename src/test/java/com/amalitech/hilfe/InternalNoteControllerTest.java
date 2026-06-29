package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.InternalNoteController;
import com.amalitech.hilfe.dto.InternalNoteRequest;
import com.amalitech.hilfe.dto.InternalNoteResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.InternalNoteService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalNoteController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class InternalNoteControllerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-29T14:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean InternalNoteService noteService;
    @MockitoBean TokenService tokenService;

    final ObjectMapper objectMapper = new ObjectMapper();

    private UsernamePasswordAuthenticationToken agentAuth() {
        var principal = new JwtTokenService.AuthPrincipal("u1", "agent@test.com", RoleCode.AGENT);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_AGENT"));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        var principal = new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_ADMIN"));
    }

    private UsernamePasswordAuthenticationToken clientAuth() {
        var principal = new JwtTokenService.AuthPrincipal("client-1", "client@test.com", RoleCode.CLIENT);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_CLIENT"));
    }

    private InternalNoteResponse stubNote() {
        return new InternalNoteResponse(
                "n1", "inc-1", "test body",
                new InternalNoteResponse.AuthorInfo("u1", "Agent Name", null),
                true, FIXED_NOW, FIXED_NOW
        );
    }

    // ── POST /incidents/{incidentId}/notes ────────────────────────────────────

    @Test
    void createNote_agentAuth_returns201() throws Exception {
        when(noteService.createNote(anyString(), eq("inc-1"), any(InternalNoteRequest.class)))
                .thenReturn(stubNote());

        mvc.perform(post("/incidents/inc-1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InternalNoteRequest("test body")))
                        .with(authentication(agentAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Note created successfully"))
                .andExpect(jsonPath("$.data.id").value("n1"))
                .andExpect(jsonPath("$.data.body").value("test body"))
                .andExpect(jsonPath("$.data.isOwner").value(true));
    }

    @Test
    void createNote_adminAuth_returns201() throws Exception {
        when(noteService.createNote(anyString(), eq("inc-1"), any(InternalNoteRequest.class)))
                .thenReturn(stubNote());

        mvc.perform(post("/incidents/inc-1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InternalNoteRequest("test body")))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated());
    }

    @Test
    void createNote_clientAuth_returns403() throws Exception {
        mvc.perform(post("/incidents/inc-1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InternalNoteRequest("test body")))
                        .with(authentication(clientAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createNote_blankBody_returns400() throws Exception {
        mvc.perform(post("/incidents/inc-1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"   \"}")
                        .with(authentication(agentAuth())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createNote_serviceThrows404_returns404() throws Exception {
        when(noteService.createNote(anyString(), eq("bad-inc"), any(InternalNoteRequest.class)))
                .thenThrow(new ArmsAuthException("Incident not found", 404));

        mvc.perform(post("/incidents/bad-inc/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InternalNoteRequest("test body")))
                        .with(authentication(agentAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Incident not found"));
    }

    // ── GET /incidents/{incidentId}/notes ─────────────────────────────────────

    @Test
    void listNotes_agentAuth_returns200WithPagination() throws Exception {
        PageResponse<InternalNoteResponse> pageResponse = new PageResponse<>(
                List.of(stubNote()), 0, 20, 1L, 1, false, false
        );
        when(noteService.listNotes(anyString(), eq("inc-1"), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(stubNote())));

        mvc.perform(get("/incidents/inc-1/notes")
                        .with(authentication(agentAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notes retrieved successfully"));
    }

    @Test
    void listNotes_clientAuth_returns403() throws Exception {
        mvc.perform(get("/incidents/inc-1/notes")
                        .with(authentication(clientAuth())))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /incidents/{incidentId}/notes/{noteId} ──────────────────────────

    @Test
    void updateNote_agentAuth_returns200() throws Exception {
        when(noteService.updateNote(anyString(), eq("inc-1"), eq("n1"), any(InternalNoteRequest.class)))
                .thenReturn(stubNote());

        mvc.perform(patch("/incidents/inc-1/notes/n1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InternalNoteRequest("updated body")))
                        .with(authentication(agentAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Note updated successfully"))
                .andExpect(jsonPath("$.data.id").value("n1"));
    }

    @Test
    void updateNote_clientAuth_returns403() throws Exception {
        mvc.perform(patch("/incidents/inc-1/notes/n1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InternalNoteRequest("updated body")))
                        .with(authentication(clientAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateNote_serviceThrows403_returns403() throws Exception {
        when(noteService.updateNote(anyString(), eq("inc-1"), eq("n1"), any(InternalNoteRequest.class)))
                .thenThrow(new ArmsAuthException("You can only edit your own notes", 403));

        mvc.perform(patch("/incidents/inc-1/notes/n1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InternalNoteRequest("updated body")))
                        .with(authentication(agentAuth())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only edit your own notes"));
    }

    // ── DELETE /incidents/{incidentId}/notes/{noteId} ─────────────────────────

    @Test
    void deleteNote_agentAuth_returns204() throws Exception {
        mvc.perform(delete("/incidents/inc-1/notes/n1")
                        .with(authentication(agentAuth())))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNote_clientAuth_returns403() throws Exception {
        mvc.perform(delete("/incidents/inc-1/notes/n1")
                        .with(authentication(clientAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteNote_serviceThrows403_returns403() throws Exception {
        org.mockito.Mockito.doThrow(new ArmsAuthException("You can only delete your own notes", 403))
                .when(noteService).deleteNote(anyString(), anyString(), eq("inc-1"), eq("n1"));

        mvc.perform(delete("/incidents/inc-1/notes/n1")
                        .with(authentication(agentAuth())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only delete your own notes"));
    }
}
