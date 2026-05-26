package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.AttachmentRef;
import com.amalitech.hilfe.dto.MediaResponse;
import com.amalitech.hilfe.dto.MessageResponse;
import com.amalitech.hilfe.dto.PresignedUrlRequest;
import com.amalitech.hilfe.dto.PresignedUrlResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.Message;
import com.amalitech.hilfe.models.MessageMedia;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.MessageMediaRepository;
import com.amalitech.hilfe.repositories.MessageRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.MediaService;
import com.amalitech.hilfe.services.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock MessageRepository messageRepository;
    @Mock IncidentRepository incidentRepository;
    @Mock AgentRepository agentRepository;
    @Mock AgentGroupMemberRepository agentGroupMemberRepository;
    @Mock UserRepository userRepository;
    @Mock MessageMediaRepository messageMediaRepository;
    @Mock MediaService mediaService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks MessageService messageService;

    @Test
    void generateMessagePresignedUrl_withAccess_returnsPresignedResponse() {
        Incident incident = Incident.builder().id("inc-1").userId("u1").build();
        PresignedUrlRequest request = new PresignedUrlRequest("img.png", "image/png", 1024L);
        PresignedUrlResponse presigned = new PresignedUrlResponse("https://upload", "messages/a/img.png", 900);
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(mediaService.generateMessagePresignedUploadUrl(request)).thenReturn(presigned);

        PresignedUrlResponse result = messageService.generateMessagePresignedUrl("u1", "CLIENT", "inc-1", request);

        assertThat(result).isEqualTo(presigned);
    }

    @Test
    void sendMessage_attachmentsOnly_persistsAndBroadcastsWithAttachments() {
        Incident incident = Incident.builder().id("inc-1").userId("u1").build();
        User sender = User.builder().id("u1").fullName("Jane").email("jane@test.com").build();
        Message saved = Message.builder()
                .id("msg-1")
                .incidentId("inc-1")
                .senderId("u1")
                .content(null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        AttachmentRef attachment = new AttachmentRef("messages/a/img.png", "img.png", "image/png", 1024L);
        MessageMedia messageMedia = MessageMedia.builder()
                .id("mm-1")
                .messageId("msg-1")
                .incidentId("inc-1")
                .originalName("img.png")
                .fileKey("messages/a/img.png")
                .contentType("image/png")
                .fileSize(1024L)
                .build();
        MediaResponse mediaResponse = new MediaResponse("mm-1", "img.png", "image/png", 1024L, "https://download");

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(userRepository.findById("u1")).thenReturn(Optional.of(sender));
        when(messageRepository.save(any(Message.class))).thenReturn(saved);
        when(mediaService.createMediaForMessage("inc-1", "msg-1", List.of(attachment))).thenReturn(List.of(messageMedia));
        when(mediaService.toMediaResponsesForMessage(List.of(messageMedia))).thenReturn(List.of(mediaResponse));

        MessageResponse result = messageService.sendMessage("u1", "CLIENT", "inc-1", null, List.of(attachment));

        assertThat(result.id()).isEqualTo("msg-1");
        assertThat(result.attachments()).hasSize(1);
        assertThat(result.attachments().getFirst().fileSize()).isEqualTo(1024L);
        verify(messagingTemplate).convertAndSend(eq("/topic/incidents/inc-1/messages"), any(MessageResponse.class));
    }

    @Test
    void sendMessage_emptyContentAndAttachments_throws400() {
        Incident incident = Incident.builder().id("inc-1").userId("u1").build();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> messageService.sendMessage("u1", "CLIENT", "inc-1", "   ", List.of()))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Either content or attachments must be provided")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void listMessages_includesAttachmentResponses() {
        Incident incident = Incident.builder().id("inc-1").userId("u1").build();
        Message message = Message.builder()
                .id("msg-1")
                .incidentId("inc-1")
                .senderId("u1")
                .content("hello")
                .sender(User.builder().id("u1").fullName("Jane").build())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        MessageMedia messageMedia = MessageMedia.builder()
                .id("mm-1")
                .messageId("msg-1")
                .incidentId("inc-1")
                .originalName("file.pdf")
                .fileKey("messages/m1/file.pdf")
                .contentType("application/pdf")
                .fileSize(400L)
                .build();
        MediaResponse mediaResponse = new MediaResponse("mm-1", "file.pdf", "application/pdf", 400L, "https://download");

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(messageRepository.findByIncidentId(eq("inc-1"), any())).thenReturn(new PageImpl<>(List.of(message)));
        when(messageMediaRepository.findByMessageIdIn(List.of("msg-1"))).thenReturn(List.of(messageMedia));
        when(mediaService.toMediaResponsesForMessage(List.of(messageMedia))).thenReturn(List.of(mediaResponse));

        var page = messageService.listMessages("u1", "CLIENT", "inc-1", PageRequest.of(0, 50));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().attachments()).hasSize(1);
        assertThat(page.getContent().getFirst().attachments().getFirst().originalName()).isEqualTo("file.pdf");
    }
}
