package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.InternalNoteRequest;
import com.amalitech.hilfe.dto.InternalNoteResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.services.InternalNoteService;
import com.amalitech.hilfe.services.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Internal Notes", description = "Staff-only annotations on incidents (agents and admins only)")
@RestController
@RequestMapping("/incidents/{incidentId}/notes")
@RequiredArgsConstructor
public class InternalNoteController {

    private final InternalNoteService noteService;

    @Operation(summary = "Create internal note", description = "Adds a staff-only note to an incident. Restricted to agents and admins.")
    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<InternalNoteResponse>> createNote(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String incidentId,
            @Valid @RequestBody InternalNoteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Note created successfully",
                        noteService.createNote(principal.userId(), incidentId, request)));
    }

    @Operation(summary = "List internal notes", description = "Returns paginated internal notes for an incident, oldest first. Restricted to agents and admins.")
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<InternalNoteResponse>>> listNotes(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String incidentId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field and direction", example = "createdAt,asc")
            @RequestParam(defaultValue = "createdAt,asc") String sort
    ) {
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        return ResponseEntity.ok(ApiResponse.success("Notes retrieved successfully",
                PageResponse.from(noteService.listNotes(
                        principal.userId(), incidentId,
                        PageRequest.of(page, size, Sort.by(direction, sortField))))));
    }

    @Operation(summary = "Update internal note", description = "Updates the body of an existing note. Only the note's author may update it.")
    @PatchMapping("/{noteId}")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<InternalNoteResponse>> updateNote(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String incidentId,
            @Parameter(description = "Note ID") @PathVariable String noteId,
            @Valid @RequestBody InternalNoteRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Note updated successfully",
                noteService.updateNote(principal.userId(), incidentId, noteId, request)));
    }

    @Operation(summary = "Delete internal note", description = "Deletes a note. The note's author or an admin may delete it.")
    @DeleteMapping("/{noteId}")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteNote(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String incidentId,
            @Parameter(description = "Note ID") @PathVariable String noteId
    ) {
        noteService.deleteNote(principal.userId(), principal.roleCode(), incidentId, noteId);
        return ResponseEntity.noContent().build();
    }
}
