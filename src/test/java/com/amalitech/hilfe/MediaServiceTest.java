package com.amalitech.hilfe;

import com.amalitech.hilfe.config.MediaProperties;
import com.amalitech.hilfe.config.S3Properties;
import com.amalitech.hilfe.dto.AttachmentRef;
import com.amalitech.hilfe.dto.MediaResponse;
import com.amalitech.hilfe.dto.PresignedUrlRequest;
import com.amalitech.hilfe.dto.PresignedUrlResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Media;
import com.amalitech.hilfe.repositories.MediaRepository;
import com.amalitech.hilfe.services.MediaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock S3Presigner s3Presigner;
    @Mock S3Client s3Client;
    @Mock S3Properties s3Properties;
    @Mock MediaProperties mediaProperties;
    @Mock MediaRepository mediaRepository;
    @InjectMocks MediaService mediaService;

    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf");

    // ── generatePresignedUploadUrl ───────────────────────────────────────────

    @Test
    void generatePresignedUploadUrl_validRequest_returnsPresignedUrl() throws Exception {
        when(mediaProperties.allowedContentTypes()).thenReturn(ALLOWED_TYPES);
        when(mediaProperties.maxFileSize()).thenReturn(10_485_760L);
        when(s3Properties.bucketName()).thenReturn("test-bucket");
        when(s3Properties.presignExpiry()).thenReturn(Duration.ofMinutes(15));

        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3.example.com/presigned-put").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        PresignedUrlRequest request = new PresignedUrlRequest("photo.png", "image/png", 1024L);
        PresignedUrlResponse response = mediaService.generatePresignedUploadUrl(request);

        assertThat(response.uploadUrl()).contains("presigned-put");
        assertThat(response.fileKey()).startsWith("media/");
        assertThat(response.fileKey()).endsWith("/photo.png");
        assertThat(response.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void generatePresignedUploadUrl_invalidContentType_throws400() {
        when(mediaProperties.allowedContentTypes()).thenReturn(ALLOWED_TYPES);

        PresignedUrlRequest request = new PresignedUrlRequest("script.exe", "application/x-msdownload", 1024L);

        assertThatThrownBy(() -> mediaService.generatePresignedUploadUrl(request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("not allowed")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void generatePresignedUploadUrl_fileTooLarge_throws400() {
        when(mediaProperties.allowedContentTypes()).thenReturn(ALLOWED_TYPES);
        when(mediaProperties.maxFileSize()).thenReturn(10_485_760L);

        PresignedUrlRequest request = new PresignedUrlRequest("huge.pdf", "application/pdf", 99_999_999L);

        assertThatThrownBy(() -> mediaService.generatePresignedUploadUrl(request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("exceeds the maximum")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    // ── createMediaForIncident ───────────────────────────────────────────────

    @Test
    void createMediaForIncident_validAttachments_savesAll() throws Exception {
        when(mediaProperties.maxAttachments()).thenReturn(5);
        when(mediaProperties.allowedContentTypes()).thenReturn(ALLOWED_TYPES);
        when(mediaProperties.maxFileSize()).thenReturn(10_485_760L);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
        when(s3Properties.bucketName()).thenReturn("test-bucket");
        when(s3Properties.presignExpiry()).thenReturn(Duration.ofMinutes(15));

        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3.example.com/presigned-get").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> inv.getArgument(0));

        List<AttachmentRef> attachments = List.of(
                new AttachmentRef("media/uuid1/photo.png", "photo.png", "image/png", 2048L));

        List<Media> result = mediaService.createMediaForIncident("inc-1", attachments);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getIncidentId()).isEqualTo("inc-1");
        assertThat(result.getFirst().getOriginalName()).isEqualTo("photo.png");
        verify(mediaRepository).save(any(Media.class));
    }

    @Test
    void createMediaForIncident_tooManyAttachments_throws400() {
        when(mediaProperties.maxAttachments()).thenReturn(2);

        List<AttachmentRef> attachments = List.of(
                new AttachmentRef("key1", "a.png", "image/png", 1024L),
                new AttachmentRef("key2", "b.png", "image/png", 1024L),
                new AttachmentRef("key3", "c.png", "image/png", 1024L));

        assertThatThrownBy(() -> mediaService.createMediaForIncident("inc-1", attachments))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Maximum 2 attachments")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void createMediaForIncident_fileNotInS3_throws400() {
        when(mediaProperties.maxAttachments()).thenReturn(5);
        when(mediaProperties.allowedContentTypes()).thenReturn(ALLOWED_TYPES);
        when(mediaProperties.maxFileSize()).thenReturn(10_485_760L);
        when(s3Properties.bucketName()).thenReturn("test-bucket");
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        List<AttachmentRef> attachments = List.of(
                new AttachmentRef("media/missing/file.png", "file.png", "image/png", 1024L));

        assertThatThrownBy(() -> mediaService.createMediaForIncident("inc-1", attachments))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("File not found in storage")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    // ── toMediaResponses ─────────────────────────────────────────────────────

    @Test
    void toMediaResponses_generatesPresignedGetUrls() throws Exception {
        when(s3Properties.bucketName()).thenReturn("test-bucket");
        when(s3Properties.presignExpiry()).thenReturn(Duration.ofMinutes(15));

        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3.example.com/get-url").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        Media media = Media.builder()
                .id("m-1").incidentId("inc-1").originalName("doc.pdf")
                .fileKey("media/m-1/doc.pdf").url("old-url")
                .contentType("application/pdf").fileSize(5000L)
                .build();

        List<MediaResponse> responses = mediaService.toMediaResponses(List.of(media));

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().url()).contains("get-url");
        assertThat(responses.getFirst().originalName()).isEqualTo("doc.pdf");
    }
}
