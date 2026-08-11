package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.MediaProperties;
import com.amalitech.hilfe.config.S3Properties;
import com.amalitech.hilfe.dto.AttachmentRef;
import com.amalitech.hilfe.dto.MediaResponse;
import com.amalitech.hilfe.dto.PresignedUrlRequest;
import com.amalitech.hilfe.dto.PresignedUrlResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Media;
import com.amalitech.hilfe.models.MessageMedia;
import com.amalitech.hilfe.repositories.MediaRepository;
import com.amalitech.hilfe.repositories.MessageMediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {
    private static final String FILE_NOT_FOUND_MESSAGE = "One of the uploaded files could not be found.";
    private static final String FILE_VERIFICATION_FAILED_MESSAGE = "The uploaded file could not be verified. Please try again.";
    private static final String VIDEO_CONTENT_TYPE_PREFIX = "video/";
    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS_BY_CONTENT_TYPE = Map.ofEntries(
            Map.entry("image/jpeg", Set.of("jpg", "jpeg")),
            Map.entry("image/png", Set.of("png")),
            Map.entry("image/gif", Set.of("gif")),
            Map.entry("image/webp", Set.of("webp")),
            Map.entry("application/pdf", Set.of("pdf")),
            Map.entry("image/svg+xml", Set.of("svg")),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx")),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx")),
            Map.entry("text/plain", Set.of("txt")),
            Map.entry("video/mp4", Set.of("mp4")),
            Map.entry("video/quicktime", Set.of("mov", "qt")),
            Map.entry("video/webm", Set.of("webm"))
    );
    private static final Map<String, String> VIDEO_FORMAT_LABELS = Map.of(
            "video/mp4", "MP4",
            "video/quicktime", "MOV",
            "video/webm", "WEBM"
    );

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final MediaProperties mediaProperties;
    private final MediaRepository mediaRepository;
    private final MessageMediaRepository messageMediaRepository;
    private final ActivityLogService activityLogService;

    public PresignedUrlResponse generatePresignedUploadUrl(PresignedUrlRequest request) {
        validateContentType(request.contentType());
        validateFileSize(request.contentType(), request.fileSize());
        validateFileNameAndExtension(request.fileName(), request.contentType());

        String mediaId = UUID.randomUUID().toString();
        String fileKey = buildFileKey("media", mediaId, request.fileName());
        return presignUploadUrl(fileKey, request.contentType(), request.fileSize());
    }

    public PresignedUrlResponse generateMessagePresignedUploadUrl(PresignedUrlRequest request) {
        validateContentType(request.contentType());
        validateFileSize(request.contentType(), request.fileSize());
        validateFileNameAndExtension(request.fileName(), request.contentType());

        String messageMediaId = UUID.randomUUID().toString();
        String fileKey = buildFileKey("messages", messageMediaId, request.fileName());
        return presignUploadUrl(fileKey, request.contentType(), request.fileSize());
    }

    public List<MessageMedia> createMediaForMessage(String incidentId, String messageId, List<AttachmentRef> attachments) {
        return createMediaForMessage(incidentId, messageId, attachments, null);
    }

    public List<MessageMedia> createMediaForMessage(String incidentId, String messageId, List<AttachmentRef> attachments, String actorUserId) {
        validateAttachments(attachments);

        return attachments.stream()
                .map(ref -> {
                    try {
                        validateFileKey(ref.fileKey(), "messages/");
                        validateContentType(ref.contentType());
                        validateFileSize(ref.contentType(), ref.fileSize());
                        validateFileNameAndExtension(ref.originalName(), ref.contentType());
                        verifyUploadedObject(ref);

                        MessageMedia messageMedia = MessageMedia.builder()
                                .id(UUID.randomUUID().toString())
                                .messageId(messageId)
                                .incidentId(incidentId)
                                .originalName(ref.originalName())
                                .fileKey(ref.fileKey())
                                .contentType(ref.contentType())
                                .fileSize(ref.fileSize())
                                .build();
                        MessageMedia saved = messageMediaRepository.save(messageMedia);
                        activityLogService.logAttachmentUploaded(actorUserId, incidentId, ref.originalName(), ref.contentType(), ref.fileSize());
                        return saved;
                    } catch (ArmsAuthException ex) {
                        activityLogService.logAttachmentUploadFailed(actorUserId, incidentId, ref.originalName(), ref.contentType(), ref.fileSize(), ex.getMessage());
                        throw ex;
                    }
                })
                .toList();
    }

    public List<MediaResponse> toMediaResponsesForMessage(List<MessageMedia> mediaList) {
        return mediaList.stream()
                .map(media -> new MediaResponse(
                        media.getId(),
                        media.getOriginalName(),
                        media.getContentType(),
                        media.getFileSize(),
                        generatePresignedGetUrl(media.getFileKey())
                ))
                .toList();
    }

    private PresignedUrlResponse presignUploadUrl(String fileKey, String contentType, long fileSize) {
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(s3Properties.presignExpiry())
                .putObjectRequest(req -> req
                        .bucket(s3Properties.bucketName())
                        .key(fileKey)
                        .contentType(contentType)
                        .contentLength(fileSize))
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        return new PresignedUrlResponse(uploadUrl, fileKey, s3Properties.presignExpiry().toSeconds());
    }

    public List<Media> createMediaForIncident(String incidentId, List<AttachmentRef> attachments) {
        return createMediaForIncident(incidentId, attachments, null);
    }

    public List<Media> createMediaForIncident(String incidentId, List<AttachmentRef> attachments, String actorUserId) {
        validateAttachments(attachments);

        return attachments.stream()
                .map(ref -> {
                    try {
                        validateFileKey(ref.fileKey(), "media/");
                        validateContentType(ref.contentType());
                        validateFileSize(ref.contentType(), ref.fileSize());
                        validateFileNameAndExtension(ref.originalName(), ref.contentType());
                        verifyUploadedObject(ref);

                        Media media = Media.builder()
                                .id(UUID.randomUUID().toString())
                                .incidentId(incidentId)
                                .originalName(ref.originalName())
                                .fileKey(ref.fileKey())
                                .url(stableObjectUrl(ref.fileKey()))
                                .contentType(ref.contentType())
                                .fileSize(ref.fileSize())
                                .build();
                        Media saved = mediaRepository.save(media);
                        activityLogService.logAttachmentUploaded(actorUserId, incidentId, ref.originalName(), ref.contentType(), ref.fileSize());
                        return saved;
                    } catch (ArmsAuthException ex) {
                        activityLogService.logAttachmentUploadFailed(actorUserId, incidentId, ref.originalName(), ref.contentType(), ref.fileSize(), ex.getMessage());
                        throw ex;
                    }
                })
                .toList();
    }

    public List<MediaResponse> toMediaResponses(List<Media> mediaList) {
        return mediaList.stream()
                .map(media -> MediaResponse.from(media, generatePresignedGetUrl(media.getFileKey())))
                .toList();
    }

    public String generatePresignedGetUrl(String fileKey) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(s3Properties.presignExpiry())
                .getObjectRequest(req -> req
                        .bucket(s3Properties.bucketName())
                        .key(fileKey))
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }



    private void validateAttachments(List<AttachmentRef> attachments) {
        if (attachments.size() > mediaProperties.maxAttachments()) {
            throw new ArmsAuthException(
                    "You can attach a maximum of " + mediaProperties.maxAttachments() + " files.", 400);
        }

        Set<String> fileKeys = new HashSet<>();
        for (AttachmentRef attachment : attachments) {
            if (!fileKeys.add(attachment.fileKey())) {
                throw new ArmsAuthException("This file has already been attached.", 400);
            }
        }

        long totalSize = attachments.stream().mapToLong(AttachmentRef::fileSize).sum();
        if (totalSize > mediaProperties.maxTotalAttachmentSize()) {
            throw new ArmsAuthException(
                    "The combined size of all attachments exceeds the maximum allowed total of "
                            + mediaProperties.maxTotalAttachmentSize() + " bytes.", 400);
        }
    }

    private void validateFileKey(String fileKey, String prefix) {
        if (!fileKey.startsWith(prefix)) {
            throw new ArmsAuthException("One of the uploaded files could not be identified. Please try uploading it again.", 400);
        }
    }

    private boolean isVideoContentType(String contentType) {
        return contentType != null && contentType.startsWith(VIDEO_CONTENT_TYPE_PREFIX);
    }

    private void validateContentType(String contentType) {
        if (mediaProperties.allowedContentTypes().contains(contentType)) {
            return;
        }

        if (isVideoContentType(contentType)) {
            List<String> supportedVideoFormats = mediaProperties.allowedContentTypes().stream()
                    .filter(this::isVideoContentType)
                    .map(ct -> VIDEO_FORMAT_LABELS.getOrDefault(ct, ct) + " (" + ct + ")")
                    .toList();
            throw new ArmsAuthException(
                    supportedVideoFormats.isEmpty()
                            ? "Video attachments are not currently supported."
                            : "This video format is not supported. Supported video formats: "
                                    + String.join(", ", supportedVideoFormats) + ".",
                    400);
        }

        throw new ArmsAuthException(
                "This file type is not supported. Allowed file types: "
                        + String.join(", ", mediaProperties.allowedContentTypes()) + ".",
                400);
    }

    private void validateFileSize(String contentType, long fileSize) {
        boolean isVideo = isVideoContentType(contentType);
        long limit = isVideo ? mediaProperties.maxVideoFileSize() : mediaProperties.maxFileSize();
        if (fileSize > limit) {
            throw new ArmsAuthException(
                    "This " + (isVideo ? "video" : "file") + " exceeds the maximum allowed size of "
                            + limit + " bytes.",
                    400);
        }
    }

    private void validateFileNameAndExtension(String fileName, String contentType) {
        String extension = extractFileExtension(fileName);
        if (extension == null) {
            throw new ArmsAuthException("The file name must include a valid extension.", 400);
        }

        Set<String> allowedExtensions = ALLOWED_EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
        if (allowedExtensions == null || !allowedExtensions.contains(extension)) {
            throw new ArmsAuthException(
                    "Files with the '." + extension + "' extension are not supported for this file type.",
                    400);
        }
    }

    private void verifyUploadedObject(AttachmentRef ref) {
        try {
            HeadObjectResponse object = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.bucketName())
                    .key(ref.fileKey())
                    .build());

            validateUploadedMetadata(ref, object);
        } catch (NoSuchKeyException e) {
            throw new ArmsAuthException(FILE_NOT_FOUND_MESSAGE, 400);
        } catch (ArmsAuthException e) {
            throw e;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new ArmsAuthException(FILE_NOT_FOUND_MESSAGE, 400);
            }

            log.error("Failed to verify file in S3: {}", ref.fileKey(), e);
            throw new ArmsAuthException(FILE_VERIFICATION_FAILED_MESSAGE, 502);
        } catch (Exception e) {
            log.error("Failed to verify file in S3: {}", ref.fileKey(), e);
            throw new ArmsAuthException(FILE_VERIFICATION_FAILED_MESSAGE, 502);
        }
    }

    private void validateUploadedMetadata(AttachmentRef ref, HeadObjectResponse object) {
        if (!ref.fileSize().equals(object.contentLength())) {
            throw new ArmsAuthException("The uploaded file size does not match what was expected. Please try uploading it again.", 400);
        }

        if (!ref.contentType().equals(object.contentType())) {
            throw new ArmsAuthException("The uploaded file type does not match what was expected. Please try uploading it again.", 400);
        }
    }

    private String stableObjectUrl(String fileKey) {
        return "s3://" + s3Properties.bucketName() + "/" + fileKey;
    }

    private String buildFileKey(String scopePrefix, String objectId, String fileName) {
        return scopePrefix + "/" + objectId + "/" + sanitizeFileName(fileName);
    }

    private String extractFileExtension(String fileName) {
        if (fileName == null) return null;
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == fileName.length() - 1) return null;
        String ext = fileName.substring(lastDot + 1).toLowerCase();
        return ext.matches("[a-z0-9]+") ? ext : null;
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
