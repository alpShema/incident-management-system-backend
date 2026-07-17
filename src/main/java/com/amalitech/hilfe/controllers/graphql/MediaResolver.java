package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.PresignedUrlRequest;
import com.amalitech.hilfe.dto.PresignedUrlResponse;
import com.amalitech.hilfe.services.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class MediaResolver {

    private final MediaService mediaService;

    @MutationMapping
    @PreAuthorize("hasAuthority('incident.create')")
    public PresignedUrlResponse generateMediaPresignedUrl(@Argument PresignedUrlRequest input) {
        GraphQlResponseMessage.set("Presigned URL generated successfully");
        return mediaService.generatePresignedUploadUrl(input);
    }
}
