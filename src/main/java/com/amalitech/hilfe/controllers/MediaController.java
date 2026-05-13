package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.PresignedUrlRequest;
import com.amalitech.hilfe.dto.PresignedUrlResponse;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Media", description = "File attachment management — presigned URL generation for S3 uploads")
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {
    private final MediaService mediaService;

    @Operation(
        summary = "Generate a presigned upload URL",
        description = "Returns a presigned S3 PUT URL for direct file upload. "
                    + "Client uploads the file directly to S3 using this URL, "
                    + "then includes the returned fileKey in the incident creation request."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Presigned URL generated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file type or size"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PostMapping("/presigned-url")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_CREATE + "')")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> generatePresignedUrl(
            @Valid @RequestBody PresignedUrlRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Presigned URL generated successfully",
                mediaService.generatePresignedUploadUrl(request)));
    }
}
