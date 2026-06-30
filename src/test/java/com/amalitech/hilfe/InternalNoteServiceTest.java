package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.InternalNoteRequest;
import com.amalitech.hilfe.dto.InternalNoteResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.InternalNote;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.InternalNoteRepository;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.InternalNoteService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalNoteServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock InternalNoteRepository noteRepository;
    @Mock IncidentRepository incidentRepository;
    @Mock ActivityLogService activityLogService;
    @Mock EntityManager entityManager;
    @InjectMocks InternalNoteService noteService;

    private User author(String userId) {
        User u = User.builder().id(userId).build();
        u.setFullName("Agent Name");
        return u;
    }

    private InternalNote note(String id, String incidentId, String authorId) {
        return InternalNote.builder()
                .id(id)
                .incidentId(incidentId)
                .authorId(authorId)
                .body("test body")
                .createdAt(FIXED_NOW)
                .updatedAt(FIXED_NOW)
                .author(author(authorId))
                .build();
    }

    // ── createNote ────────────────────────────────────────────────────────────

    @Test
    void createNote_success_persistsNoteAndLogsActivity() {
        when(incidentRepository.existsById("inc-1")).thenReturn(true);
        InternalNote saved = note("n1", "inc-1", "u1");
        when(noteRepository.save(any(InternalNote.class))).thenReturn(saved);
        when(noteRepository.findByIdWithAuthor(any())).thenReturn(Optional.of(saved));

        InternalNoteResponse result = noteService.createNote("u1", "inc-1", new InternalNoteRequest("test body"));

        assertThat(result.incidentId()).isEqualTo("inc-1");
        assertThat(result.body()).isEqualTo("test body");
        assertThat(result.isOwner()).isTrue();
        verify(activityLogService).logInternalNoteCreated(eq("u1"), eq("inc-1"), anyString());
    }

    @Test
    void createNote_incidentNotFound_throws404() {
        when(incidentRepository.existsById("bad-inc")).thenReturn(false);
        var request = new InternalNoteRequest("body");

        assertThatThrownBy(() -> noteService.createNote("u1", "bad-inc", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Incident not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);

        verify(noteRepository, never()).save(any());
    }

    // ── listNotes ─────────────────────────────────────────────────────────────

    @Test
    void listNotes_returnsPagedNotesForIncident() {
        when(incidentRepository.existsById("inc-1")).thenReturn(true);
        InternalNote n = note("n1", "inc-1", "u1");
        Page<InternalNote> page = new PageImpl<>(List.of(n));
        when(noteRepository.findByIncidentId(eq("inc-1"), any(Pageable.class))).thenReturn(page);

        Page<InternalNoteResponse> result = noteService.listNotes("u1", "inc-1", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo("n1");
    }

    @Test
    void listNotes_incidentNotFound_throws404() {
        when(incidentRepository.existsById("bad")).thenReturn(false);
        var pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> noteService.listNotes("u1", "bad", pageable))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void listNotes_isOwnerTrue_forAuthor() {
        when(incidentRepository.existsById("inc-1")).thenReturn(true);
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIncidentId(eq("inc-1"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(n)));

        Page<InternalNoteResponse> result = noteService.listNotes("u1", "inc-1", PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).isOwner()).isTrue();
    }

    @Test
    void listNotes_isOwnerFalse_forOtherUser() {
        when(incidentRepository.existsById("inc-1")).thenReturn(true);
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIncidentId(eq("inc-1"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(n)));

        Page<InternalNoteResponse> result = noteService.listNotes("u2", "inc-1", PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).isOwner()).isFalse();
    }

    // ── updateNote ────────────────────────────────────────────────────────────

    @Test
    void updateNote_byAuthor_updatesBodyAndLogs() {
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));
        when(noteRepository.save(n)).thenReturn(n);

        InternalNoteResponse result = noteService.updateNote("u1", "inc-1", "n1", new InternalNoteRequest("updated body"));

        assertThat(result.body()).isEqualTo("updated body");
        verify(activityLogService).logInternalNoteUpdated("u1", "inc-1", "n1");
    }

    @Test
    void updateNote_noteNotFound_throws404() {
        when(noteRepository.findByIdWithAuthor("bad")).thenReturn(Optional.empty());
        var request = new InternalNoteRequest("body");

        assertThatThrownBy(() -> noteService.updateNote("u1", "inc-1", "bad", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Note not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void updateNote_incidentMismatch_throws404() {
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));
        var request = new InternalNoteRequest("body");

        assertThatThrownBy(() -> noteService.updateNote("u1", "other-inc", "n1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Note not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void updateNote_notAuthor_throws403() {
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));
        var request = new InternalNoteRequest("body");

        assertThatThrownBy(() -> noteService.updateNote("u2", "inc-1", "n1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("You can only edit your own notes")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    // ── deleteNote ────────────────────────────────────────────────────────────

    @Test
    void deleteNote_byAuthor_deletesAndLogs() {
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));

        noteService.deleteNote("u1", "AGENT", "inc-1", "n1");

        verify(noteRepository).delete(n);
        verify(activityLogService).logInternalNoteDeleted("u1", "inc-1", "n1");
    }

    @Test
    void deleteNote_byAdmin_deletesAnyNote() {
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));

        noteService.deleteNote("admin-user", "ADMIN", "inc-1", "n1");

        verify(noteRepository).delete(n);
        verify(activityLogService).logInternalNoteDeleted("admin-user", "inc-1", "n1");
    }

    @Test
    void deleteNote_bySuperAdmin_deletesAnyNote() {
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));

        noteService.deleteNote("super-admin-user", "SUPER_ADMIN", "inc-1", "n1");

        verify(noteRepository).delete(n);
    }

    @Test
    void deleteNote_noteNotFound_throws404() {
        when(noteRepository.findByIdWithAuthor("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.deleteNote("u1", "AGENT", "inc-1", "bad"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Note not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void deleteNote_incidentMismatch_throws404() {
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> noteService.deleteNote("u1", "AGENT", "other-inc", "n1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Note not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void deleteNote_notAuthorAndNotAdmin_throws403() {
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> noteService.deleteNote("u2", "AGENT", "inc-1", "n1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("You can only delete your own notes")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }
}
