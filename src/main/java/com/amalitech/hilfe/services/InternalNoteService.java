package com.amalitech.hilfe.services;

import com.amalitech.hilfe.crypto.FieldEncryptionService;
import com.amalitech.hilfe.dto.InternalNoteRequest;
import com.amalitech.hilfe.dto.InternalNoteResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.InternalNote;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.InternalNoteRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalNoteService {

    private static final String NOTE_NOT_FOUND = "Note not found";

    private final InternalNoteRepository noteRepository;
    private final IncidentRepository incidentRepository;
    private final ActivityLogService activityLogService;
    private final ConfidentialIncidentAccess confidentialIncidentAccess;
    private final FieldEncryptionService fieldEncryptionService;
    private final EntityManager entityManager;

    @Transactional
    public InternalNoteResponse createNote(String userId, String incidentId, InternalNoteRequest request) {
        Incident incident = requireConfidentialAccess(userId, incidentId);
        String noteId = UUID.randomUUID().toString();
        String body = request.body().trim();
        InternalNote saved = noteRepository.save(InternalNote.builder()
                .id(noteId)
                .incidentId(incidentId)

                .authorId(userId)
                .body(isConfidential(incident) ? fieldEncryptionService.encrypt(body) : body)
                .build());
        // flush + detach so the subsequent JOIN FETCH query hits the DB instead of returning
        // the 1st-level cached entity (which has author=null on a freshly built object)
        entityManager.flush();
        entityManager.detach(saved);
        InternalNote note = noteRepository.findByIdWithAuthor(noteId)
                .orElseThrow(() -> new ArmsAuthException(NOTE_NOT_FOUND, 404));
        activityLogService.logInternalNoteCreated(userId, incidentId, noteId);
        return toResponse(note, userId);
    }

    @Transactional
    public Page<InternalNoteResponse> listNotes(String userId, String incidentId, Pageable pageable) {
        requireConfidentialAccess(userId, incidentId);
        return noteRepository.findByIncidentId(incidentId, pageable).map(n -> toResponse(n, userId));
    }

    @Transactional
    public InternalNoteResponse updateNote(String userId, String incidentId, String noteId, InternalNoteRequest request) {
        Incident incident = requireConfidentialAccess(userId, incidentId);
        InternalNote note = noteRepository.findByIdWithAuthor(noteId)
                .orElseThrow(() -> new ArmsAuthException(NOTE_NOT_FOUND, 404));
        if (!note.getIncidentId().equals(incidentId)) {
            throw new ArmsAuthException(NOTE_NOT_FOUND, 404);
        }
        if (!note.getAuthorId().equals(userId)) {
            throw new ArmsAuthException("You can only edit your own notes", 403);
        }
        String body = request.body().trim();
        note.setBody(isConfidential(incident) ? fieldEncryptionService.encrypt(body) : body);
        noteRepository.save(note);
        activityLogService.logInternalNoteUpdated(userId, incidentId, noteId);
        // note.getBody() now holds whatever was just set above -- ciphertext on a confidential
        // incident, since the entity is never re-fetched after the write. Use the plaintext we
        // already have instead of reading it back off the entity.
        return toResponse(note, userId, body);
    }

    @Transactional
    public void deleteNote(String userId, String role, String incidentId, String noteId) {
        requireConfidentialAccess(userId, incidentId);
        InternalNote note = noteRepository.findByIdWithAuthor(noteId)
                .orElseThrow(() -> new ArmsAuthException(NOTE_NOT_FOUND, 404));
        if (!note.getIncidentId().equals(incidentId)) {
            throw new ArmsAuthException(NOTE_NOT_FOUND, 404);
        }
        boolean isAuthor = note.getAuthorId().equals(userId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role) || "ADMIN_AGENT".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role);
        if (!isAuthor && !isAdmin) {
            throw new ArmsAuthException("You can only delete your own notes", 403);
        }
        noteRepository.delete(note);
        activityLogService.logInternalNoteDeleted(userId, incidentId, noteId);
    }

    // HV-1619: internal notes are otherwise only role-gated (any AGENT/ADMIN/SUPER_ADMIN can
    // read/write notes on any incident by ID, department or assignment notwithstanding) --
    // confidential incidents are the one place that must still be restricted to the topic's
    // linked owner, since notes are exactly the kind of detail this feature hides elsewhere.
    private Incident requireConfidentialAccess(String userId, String incidentId) {
        Incident incident = incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));
        if (!confidentialIncidentAccess.canAccess(userId, incident)) {
            throw new ArmsAuthException("You do not have permission to access this incident.", 403);
        }
        return incident;
    }

    // Notes on a confidential incident are exactly the kind of detail HV-1619 hides elsewhere
    // (see requireConfidentialAccess above), so they're encrypted at rest the same way the
    // incident's own title/description are.
    private boolean isConfidential(Incident incident) {
        return incident.getIncidentType() != null && incident.getIncidentType().isConfidential();
    }

    private InternalNoteResponse toResponse(InternalNote note, String currentUserId) {
        return toResponse(note, currentUserId, note.getBody());
    }

    private InternalNoteResponse toResponse(InternalNote note, String currentUserId, String body) {
        User author = note.getAuthor();
        // author is never null: "author_id" has ON DELETE RESTRICT on the DB FK.
        // If that constraint is ever relaxed, add a null guard here before accessing author fields.
        return new InternalNoteResponse(
                note.getId(),
                note.getIncidentId(),
                body,
                new InternalNoteResponse.AuthorInfo(author.getId(), author.getFullName(), author.getProfileImg()),
                note.getAuthorId().equals(currentUserId),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
