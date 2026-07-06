package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.InternalNoteRequest;
import com.amalitech.hilfe.dto.InternalNoteResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
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
    private final EntityManager entityManager;

    @Transactional
    public InternalNoteResponse createNote(String userId, String incidentId, InternalNoteRequest request) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new ArmsAuthException("Incident not found", 404);
        }
        String noteId = UUID.randomUUID().toString();
        InternalNote saved = noteRepository.save(InternalNote.builder()
                .id(noteId)
                .incidentId(incidentId)

                .authorId(userId)
                .body(request.body().trim())
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
        if (!incidentRepository.existsById(incidentId)) {
            throw new ArmsAuthException("Incident not found", 404);
        }
        return noteRepository.findByIncidentId(incidentId, pageable).map(n -> toResponse(n, userId));
    }

    @Transactional
    public InternalNoteResponse updateNote(String userId, String incidentId, String noteId, InternalNoteRequest request) {
        InternalNote note = noteRepository.findByIdWithAuthor(noteId)
                .orElseThrow(() -> new ArmsAuthException(NOTE_NOT_FOUND, 404));
        if (!note.getIncidentId().equals(incidentId)) {
            throw new ArmsAuthException(NOTE_NOT_FOUND, 404);
        }
        if (!note.getAuthorId().equals(userId)) {
            throw new ArmsAuthException("You can only edit your own notes", 403);
        }
        note.setBody(request.body().trim());
        noteRepository.save(note);
        activityLogService.logInternalNoteUpdated(userId, incidentId, noteId);
        return toResponse(note, userId);
    }

    @Transactional
    public void deleteNote(String userId, String role, String incidentId, String noteId) {
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

    private InternalNoteResponse toResponse(InternalNote note, String currentUserId) {
        User author = note.getAuthor();
        // author is never null: "author_id" has ON DELETE RESTRICT on the DB FK.
        // If that constraint is ever relaxed, add a null guard here before accessing author fields.
        return new InternalNoteResponse(
                note.getId(),
                note.getIncidentId(),
                note.getBody(),
                new InternalNoteResponse.AuthorInfo(author.getId(), author.getFullName(), author.getProfileImg()),
                note.getAuthorId().equals(currentUserId),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
