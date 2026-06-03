package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.MessageResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.PresignedUrlRequest;
import com.amalitech.hilfe.dto.PresignedUrlResponse;
import com.amalitech.hilfe.dto.SendMessageRequest;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.MessageService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Messages", description = "Incident thread messaging")
@RestController
@RequestMapping("/incidents/{incidentId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(
            summary = "Generate chat attachment presigned upload URL",
            description = "Generates a presigned S3 PUT URL for uploading a message attachment. "
                    + "Access to the incident is validated before the URL is issued. "
                    + "Use the returned fileKey in `POST /incidents/{incidentId}/messages` attachments[]."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Presigned URL generated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file type or size"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "No access to this incident"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    @PostMapping("/presigned-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> generateMessagePresignedUrl(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String incidentId,
            @Valid @RequestBody PresignedUrlRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Presigned URL generated successfully",
                messageService.generateMessagePresignedUrl(
                        principal.userId(), principal.roleCode(), incidentId, request)
        ));
    }

    @Operation(
            summary = "Send a message",
            description = "Send a message on an incident thread. Requires access to the incident. "
                    + "Supports text-only, attachments-only, or text + attachments. "
                    + "At least one of content or attachments is required. "
                    + "After a successful create, the backend broadcasts the same MessageResponse payload to "
                    + "`/topic/incidents/{incidentId}/messages` over WebSocket. "
                    + "See docs/REALTIME_MESSAGING_CONTRACT.md for the full HTTP + WebSocket contract."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Message sent"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "No access to this incident"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String incidentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "Text only",
                                            value = """
                                                    {
                                                      "content": "Can you provide more details?"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Attachments only",
                                            value = """
                                                    {
                                                      "content": null,
                                                      "attachments": [
                                                        {
                                                          "fileKey": "messages/ef86f0f0-5f4a-4d72-91fb-7b2399c53f0c/screenshot.png",
                                                          "originalName": "screenshot.png",
                                                          "contentType": "image/png",
                                                          "fileSize": 2048576
                                                        }
                                                      ]
                                                    }
                                                    """
                                    )
                            }
                    )
            )
            @Valid @RequestBody SendMessageRequest request
    ) {
        MessageResponse response = messageService.sendMessage(
                principal.userId(), principal.roleCode(), incidentId, request.content(), request.attachments());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent", response));
    }

    @Operation(
            summary = "List messages",
            description = "Returns paginated message history for an incident. "
                    + "Default sort is newest first (page 0 = most recent messages). "
                    + "Within each page, messages are ordered oldest→newest for display. "
                    + "Use this endpoint for initial chat load and re-sync after WebSocket reconnect. "
                    + "See docs/REALTIME_MESSAGING_CONTRACT.md for the full HTTP + WebSocket contract."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Messages retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "No access to this incident"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> listMessages(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String incidentId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "Sort field and direction", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        return ResponseEntity.ok(ApiResponse.success("Messages retrieved",
                PageResponse.from(messageService.listMessages(
                        principal.userId(), principal.roleCode(), incidentId,
                        PageRequest.of(page, size, Sort.by(direction, sortField))))));
    }

    @Operation(
            summary = "Delete a message",
            description = "Only the message author can delete their own message. "
                    + "See docs/REALTIME_MESSAGING_CONTRACT.md for the full HTTP + WebSocket contract."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Message deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Cannot delete another user's message"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Message not found")
    })
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String incidentId,
            @Parameter(description = "Message ID") @PathVariable String messageId
    ) {
        messageService.deleteMessage(principal.userId(), principal.roleCode(), messageId);
        return ResponseEntity.noContent().build();
    }
}
