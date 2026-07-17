package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.InternalNoteRequest;
import com.amalitech.hilfe.dto.InternalNoteResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.services.InternalNoteService;
import com.amalitech.hilfe.services.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class InternalNoteResolver {

    private final InternalNoteService noteService;

    @QueryMapping
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN', 'SUPER_ADMIN')")
    public PageResponse<InternalNoteResponse> listInternalNotes(
            @Argument String incidentId,
            @Argument PageInput page,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        return PageInput.toPageResponse(
                noteService.listNotes(principal.userId(), incidentId, PageInput.toPageable(page))
        );
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN', 'SUPER_ADMIN')")
    public InternalNoteResponse createInternalNote(
            @Argument String incidentId,
            @Argument InternalNoteRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        GraphQlResponseMessage.set("Note created successfully");
        return noteService.createNote(principal.userId(), incidentId, input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN', 'SUPER_ADMIN')")
    public InternalNoteResponse updateInternalNote(
            @Argument String incidentId,
            @Argument String noteId,
            @Argument InternalNoteRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        GraphQlResponseMessage.set("Note updated successfully");
        return noteService.updateNote(principal.userId(), incidentId, noteId, input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN', 'SUPER_ADMIN')")
    public boolean deleteInternalNote(
            @Argument String incidentId,
            @Argument String noteId,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        noteService.deleteNote(principal.userId(), principal.roleCode(), incidentId, noteId);
        GraphQlResponseMessage.set("Note deleted successfully");
        return true;
    }
}
