package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.MediaProperties;
import com.amalitech.hilfe.config.S3Properties;
import com.amalitech.hilfe.dto.AttachmentRef;
import com.amalitech.hilfe.dto.MediaResponse;
import com.amalitech.hilfe.dto.PresignedUrlRequest;
import com.amalitech.hilfe.dto.PresignedUrlResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Media;
import com.amalitech.hilfe.repositories.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final MediaProperties mediaProperties;
    private final MediaRepository mediaRepository;

    public PresignedUrlResponse generatePresignedUploadUrl(PresignedUrlRequest request) {
        validateContentType(request.contentType());
        validateFileSize(request.fileSize());

        String mediaId = UUID.randomUUID().toString();
        String fileKey = "media/" + mediaId + "/" + sanitizeFileName(request.fileName());

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucketName())
                .key(fileKey)
                .contentType(request.contentType())
                .contentLength(request.fileSize())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(s3Properties.presignExpiry())
                .putObjectRequest(putRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        return new PresignedUrlResponse(uploadUrl, fileKey, s3Properties.presignExpiry().toSeconds());
    }

    public List<Media> createMediaForIncident(String incidentId, List<AttachmentRef> attachments) {
        if (attachments.size() > mediaProperties.maxAttachments()) {
            throw new ArmsAuthException(
                    "Maximum " + mediaProperties.maxAttachments() + " attachments allowed", 400);
        }

        return attachments.stream()
                .map(ref -> {
                    validateContentType(ref.contentType());
                    validateFileSize(ref.fileSize());
                    verifyFileExistsInS3(ref.fileKey());

                    String downloadUrl = generatePresignedGetUrl(ref.fileKey());

                    Media media = Media.builder()
                            .id(UUID.randomUUID().toString())
                            .incidentId(incidentId)
                            .originalName(ref.originalName())
                            .fileKey(ref.fileKey())
                            .url(downloadUrl)
                            .contentType(ref.contentType())
                            .fileSize(ref.fileSize())
                            .build();
                    return mediaRepository.save(media);
                })
                .toList();
    }

    public List<MediaResponse> toMediaResponses(List<Media> mediaList) {
        return mediaList.stream()
                .map(media -> MediaResponse.from(media, generatePresignedGetUrl(media.getFileKey())))
                .toList();
    }

    public String generatePresignedGetUrl(String fileKey) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(s3Properties.bucketName())
                .key(fileKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(s3Properties.presignExpiry())
                .getObjectRequest(getRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void validateContentType(String contentType) {
        if (!mediaProperties.allowedContentTypes().contains(contentType)) {
            throw new ArmsAuthException(
                    "Content type '" + contentType + "' is not allowed. Allowed types: "
                            + String.join(", ", mediaProperties.allowedContentTypes()),
                    400);
        }
    }

    private void validateFileSize(long fileSize) {
        if (fileSize > mediaProperties.maxFileSize()) {
            throw new ArmsAuthException(
                    "File size " + fileSize + " bytes exceeds the maximum of "
                            + mediaProperties.maxFileSize() + " bytes",
                    400);
        }
    }

    private void verifyFileExistsInS3(String fileKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.bucketName())
                    .key(fileKey)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new ArmsAuthException("File not found in storage: " + fileKey, 400);
        } catch (Exception e) {
            log.error("Failed to verify file in S3: {}", fileKey, e);
            throw new ArmsAuthException("Unable to verify uploaded file", 502);
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
