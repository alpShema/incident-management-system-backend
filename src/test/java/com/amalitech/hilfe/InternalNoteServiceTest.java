package com.amalitech.hilfe;

import com.amalitech.hilfe.crypto.FieldEncryptionService;
import com.amalitech.hilfe.dto.InternalNoteRequest;
import com.amalitech.hilfe.dto.InternalNoteResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.models.InternalNote;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.InternalNoteRepository;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.ConfidentialIncidentAccess;
import com.amalitech.hilfe.services.InternalNoteService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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
    @Mock ConfidentialIncidentAccess confidentialIncidentAccess;
    @Mock FieldEncryptionService fieldEncryptionService;
    @Mock EntityManager entityManager;
    @InjectMocks InternalNoteService noteService;

    @BeforeEach
    void stubConfidentialAccessDefault() {
        // Default: none of these incidents are confidential, and canAccess is itself always
        // true for those -- keeps the existing non-confidential tests unaffected.
        lenient().when(confidentialIncidentAccess.canAccess(any(), any())).thenReturn(true);
        lenient().when(fieldEncryptionService.encrypt(any())).thenAnswer(inv -> "v1:" + inv.getArgument(0));
    }

    private Incident incident(String id) {
        return Incident.builder().id(id).build();
    }

    private Incident confidentialIncident(String id) {
        Incident inc = Incident.builder().id(id).build();
        inc.setIncidentType(IncidentType.builder().id("type-1").name("Topic").confidential(true).build());
        return inc;
    }

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
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1")));
        InternalNote saved = note("n1", "inc-1", "u1");
        when(noteRepository.save(any(InternalNote.class))).thenReturn(saved);
        when(noteRepository.findByIdWithAuthor(any())).thenReturn(Optional.of(saved));

        InternalNoteResponse result = noteService.createNote("u1", "inc-1", new InternalNoteRequest("test body"));

        assertThat(result.incidentId()).isEqualTo("inc-1");
        assertThat(result.body()).isEqualTo("test body");
        assertThat(result.isOwner()).isTrue();
        verify(activityLogService).logInternalNoteCreated(eq("u1"), eq("inc-1"), anyString());
        // Non-confidential incident -- body is stored in plaintext, encryption never touched.
        verifyNoInteractions(fieldEncryptionService);
    }

    // Notes on a confidential incident are exactly the kind of detail HV-1619 hides elsewhere,
    // so they're encrypted at rest the same way the incident's own title/description are.
    @Test
    void createNote_confidentialIncident_encryptsBody() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(confidentialIncident("inc-1")));
        InternalNote saved = note("n1", "inc-1", "u1");
        when(noteRepository.save(any(InternalNote.class))).thenReturn(saved);
        when(noteRepository.findByIdWithAuthor(any())).thenReturn(Optional.of(saved));

        noteService.createNote("u1", "inc-1", new InternalNoteRequest("test body"));

        var noteCaptor = org.mockito.ArgumentCaptor.forClass(InternalNote.class);
        verify(noteRepository).save(noteCaptor.capture());
        assertThat(noteCaptor.getValue().getBody()).isEqualTo("v1:test body");
        verify(fieldEncryptionService).encrypt("test body");
    }

    @Test
    void createNote_incidentNotFound_throws404() {
        when(incidentRepository.findByIdWithDetails("bad-inc")).thenReturn(Optional.empty());
        var request = new InternalNoteRequest("body");

        assertThatThrownBy(() -> noteService.createNote("u1", "bad-inc", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Incident not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);

        verify(noteRepository, never()).save(any());
    }

    @Test
    void createNote_confidentialIncident_nonMember_throws403() {
        // HV-1619: notes are otherwise only role-gated (any AGENT/ADMIN/SUPER_ADMIN can write on
        // any incident) -- confidential incidents are the one place still restricted to the
        // topic's linked owner.
        Incident inc = incident("inc-1");
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(inc));
        when(confidentialIncidentAccess.canAccess("outsider", inc)).thenReturn(false);

        InternalNoteRequest request = new InternalNoteRequest("body");
        assertThatThrownBy(() -> noteService.createNote("outsider", "inc-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);

        verify(noteRepository, never()).save(any());
    }

    // ── listNotes ─────────────────────────────────────────────────────────────

    @Test
    void listNotes_returnsPagedNotesForIncident() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1")));
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
        when(incidentRepository.findByIdWithDetails("bad")).thenReturn(Optional.empty());
        var pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> noteService.listNotes("u1", "bad", pageable))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void listNotes_isOwnerTrue_forAuthor() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1")));
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIncidentId(eq("inc-1"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(n)));

        Page<InternalNoteResponse> result = noteService.listNotes("u1", "inc-1", PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).isOwner()).isTrue();
    }

    @Test
    void listNotes_isOwnerFalse_forOtherUser() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1")));
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIncidentId(eq("inc-1"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(n)));

        Page<InternalNoteResponse> result = noteService.listNotes("u2", "inc-1", PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).isOwner()).isFalse();
    }

    @Test
    void listNotes_confidentialIncident_nonMember_throws403() {
        Incident inc = incident("inc-1");
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(inc));
        when(confidentialIncidentAccess.canAccess("outsider", inc)).thenReturn(false);
        var pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> noteService.listNotes("outsider", "inc-1", pageable))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);

        verifyNoInteractions(noteRepository);
    }

    // ── updateNote ────────────────────────────────────────────────────────────

    @Test
    void updateNote_byAuthor_updatesBodyAndLogs() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1")));
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));
        when(noteRepository.save(n)).thenReturn(n);

        InternalNoteResponse result = noteService.updateNote("u1", "inc-1", "n1", new InternalNoteRequest("updated body"));

        assertThat(result.body()).isEqualTo("updated body");
        verify(activityLogService).logInternalNoteUpdated("u1", "inc-1", "n1");
        verifyNoInteractions(fieldEncryptionService);
    }

    @Test
    void updateNote_confidentialIncident_encryptsBody() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(confidentialIncident("inc-1")));
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));
        when(noteRepository.save(n)).thenReturn(n);

        InternalNoteResponse result = noteService.updateNote("u1", "inc-1", "n1", new InternalNoteRequest("updated body"));

        assertThat(n.getBody()).isEqualTo("v1:updated body");
        verify(fieldEncryptionService).encrypt("updated body");
        // The entity's body is ciphertext (never re-fetched after the write above), but the
        // response must carry the plaintext the author actually typed -- not "v1:updated body".
        assertThat(result.body()).isEqualTo("updated body");
    }

    @Test
    void updateNote_noteNotFound_throws404() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1")));
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
        when(incidentRepository.findByIdWithDetails("other-inc")).thenReturn(Optional.of(incident("other-inc")));
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
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1")));
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));
        var request = new InternalNoteRequest("body");

        assertThatThrownBy(() -> noteService.updateNote("u2", "inc-1", "n1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("You can only edit your own notes")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void updateNote_confidentialIncident_nonMember_throws403() {
        Incident inc = incident("inc-1");
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(inc));
        when(confidentialIncidentAccess.canAccess("u1", inc)).thenReturn(false);
        var request = new InternalNoteRequest("body");

        assertThatThrownBy(() -> noteService.updateNote("u1", "inc-1", "n1", request))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);

        verifyNoInteractions(noteRepository);
    }

    // ── deleteNote ────────────────────────────────────────────────────────────

    @Test
    void deleteNote_byAuthor_deletesAndLogs() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1")));
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));

        noteService.deleteNote("u1", "AGENT", "inc-1", "n1");

        verify(noteRepository).delete(n);
        verify(activityLogService).logInternalNoteDeleted("u1", "inc-1", "n1");
    }

    @Test
    void deleteNote_byAdmin_deletesAnyNote() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1")));
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));

        noteService.deleteNote("admin-user", "ADMIN", "inc-1", "n1");

        verify(noteRepository).delete(n);
        verify(activityLogService).logInternalNoteDeleted("admin-user", "inc-1", "n1");
    }

    @Test
    void deleteNote_bySuperAdmin_deletesAnyNote() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1")));
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));

        noteService.deleteNote("super-admin-user", "SUPER_ADMIN", "inc-1", "n1");

        verify(noteRepository).delete(n);
    }

    @Test
    void deleteNote_noteNotFound_throws404() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1")));
        when(noteRepository.findByIdWithAuthor("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.deleteNote("u1", "AGENT", "inc-1", "bad"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Note not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void deleteNote_incidentMismatch_throws404() {
        when(incidentRepository.findByIdWithDetails("other-inc")).thenReturn(Optional.of(incident("other-inc")));
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
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident("inc-1")));
        InternalNote n = note("n1", "inc-1", "u1");
        when(noteRepository.findByIdWithAuthor("n1")).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> noteService.deleteNote("u2", "AGENT", "inc-1", "n1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("You can only delete your own notes")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void deleteNote_confidentialIncident_nonMember_throws403() {
        Incident inc = incident("inc-1");
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(inc));
        when(confidentialIncidentAccess.canAccess("outsider-admin", inc)).thenReturn(false);

        assertThatThrownBy(() -> noteService.deleteNote("outsider-admin", "ADMIN", "inc-1", "n1"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);

        verifyNoInteractions(noteRepository);
    }
}
