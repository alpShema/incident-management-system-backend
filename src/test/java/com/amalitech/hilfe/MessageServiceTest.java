package com.amalitech.hilfe;

import com.amalitech.hilfe.crypto.FieldEncryptionService;
import com.amalitech.hilfe.dto.AttachmentRef;
import com.amalitech.hilfe.dto.MediaResponse;
import com.amalitech.hilfe.dto.MessageResponse;
import com.amalitech.hilfe.dto.PresignedUrlRequest;
import com.amalitech.hilfe.dto.PresignedUrlResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.models.Message;
import com.amalitech.hilfe.models.MessageMedia;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.MessageMediaRepository;
import com.amalitech.hilfe.repositories.MessageRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.notifications.NotificationEventPublisher;
import com.amalitech.hilfe.services.ConfidentialIncidentAccess;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.services.MediaService;
import com.amalitech.hilfe.services.MessageService;
import com.amalitech.hilfe.services.SlaService;
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

    private static final Instant FIXED_NOW = Instant.parse("2026-06-29T14:00:00Z");

    @Mock MessageRepository messageRepository;
    @Mock IncidentRepository incidentRepository;
    @Mock AgentRepository agentRepository;
    @Mock AgentGroupMemberRepository agentGroupMemberRepository;
    @Mock UserRepository userRepository;
    @Mock MessageMediaRepository messageMediaRepository;
    @Mock MediaService mediaService;
    @Mock SlaService slaService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock NotificationEventPublisher notificationEventPublisher;
    @Mock IncidentService incidentService;
    @Mock ConfidentialIncidentAccess confidentialIncidentAccess;
    @Mock FieldEncryptionService fieldEncryptionService;
    @InjectMocks MessageService messageService;

    private Incident confidentialIncident(String userId, String assignedToId) {
        IncidentType topic = IncidentType.builder()
                .id("type-1").agentGroupId("group-1").confidential(true).build();
        Incident incident = Incident.builder().id("inc-1").userId(userId).assignedToId(assignedToId).build();
        incident.setIncidentType(topic);
        return incident;
    }

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
    void generateMessagePresignedUrl_byUnassignedAgentInSameDepartment_throws403() {
        Incident incident = Incident.builder()
                .id("inc-1")
                .userId("u1")
                .assignedToId("agent-assignee")
                .build();
        Agent teammateAgent = Agent.builder().id("agent-teammate").userId("u-teammate").status(true).build();
        PresignedUrlRequest request = new PresignedUrlRequest("img.png", "image/png", 1024L);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findByUserId("u-teammate")).thenReturn(Optional.of(teammateAgent));

        assertThatThrownBy(() -> messageService.generateMessagePresignedUrl("u-teammate", "AGENT", "inc-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("You do not have access to this incident")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);

        verify(mediaService, never()).generateMessagePresignedUploadUrl(any(PresignedUrlRequest.class));
    }

    @Test
    void generateMessagePresignedUrl_byAssignedAgent_returnsPresignedResponse() {
        Incident incident = Incident.builder()
                .id("inc-1")
                .userId("u1")
                .assignedToId("agent-assignee")
                .build();
        Agent assigneeAgent = Agent.builder().id("agent-assignee").userId("u-assignee").status(true).build();
        PresignedUrlRequest request = new PresignedUrlRequest("img.png", "image/png", 1024L);
        PresignedUrlResponse presigned = new PresignedUrlResponse("https://upload", "messages/a/img.png", 900);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findByUserId("u-assignee")).thenReturn(Optional.of(assigneeAgent));
        when(mediaService.generateMessagePresignedUploadUrl(request)).thenReturn(presigned);

        PresignedUrlResponse result = messageService.generateMessagePresignedUrl("u-assignee", "AGENT", "inc-1", request);

        assertThat(result).isEqualTo(presigned);
    }

    @Test
    void generateMessagePresignedUrl_confidentialIncident_nonMemberAdmin_throws403() {
        // HV-1619: the admin bypass above must not apply to a confidential incident's chat --
        // messages are exactly the kind of detail this feature hides elsewhere.
        Incident incident = confidentialIncident("u1", null);
        PresignedUrlRequest request = new PresignedUrlRequest("img.png", "image/png", 1024L);
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(confidentialIncidentAccess.canAccess("outside-admin", incident)).thenReturn(false);

        assertThatThrownBy(() -> messageService.generateMessagePresignedUrl("outside-admin", "ADMIN", "inc-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);

        verify(mediaService, never()).generateMessagePresignedUploadUrl(any(PresignedUrlRequest.class));
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
                .createdAt(FIXED_NOW)
                .updatedAt(FIXED_NOW)
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
    void sendMessage_byUnassignedAgentInSameDepartment_throws403() {
        // Incident is owned by client "u1" and assigned to agent record "agent-assignee".
        // Actor is a different agent ("agent-teammate") who shares an agent group with the assignee.
        // Under the old rule this passed; the strict send check now rejects.
        Incident incident = Incident.builder()
                .id("inc-1")
                .userId("u1")
                .assignedToId("agent-assignee")
                .build();
        Agent teammateAgent = Agent.builder().id("agent-teammate").userId("u-teammate").status(true).build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findByUserId("u-teammate")).thenReturn(Optional.of(teammateAgent));

        List<AttachmentRef> noAttachments = List.of();
        assertThatThrownBy(() -> messageService.sendMessage("u-teammate", "AGENT", "inc-1", "hi", noAttachments))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("You do not have access to this incident")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);

        verify(messageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(MessageResponse.class));
    }

    @Test
    void sendMessage_byAssignedAgent_succeeds() {
        Incident incident = Incident.builder()
                .id("inc-1")
                .userId("u1")
                .assignedToId("agent-assignee")
                .build();
        Agent assigneeAgent = Agent.builder().id("agent-assignee").userId("u-assignee").status(true).build();
        User sender = User.builder().id("u-assignee").fullName("Assignee").email("a@test.com").build();
        Message saved = Message.builder()
                .id("msg-1").incidentId("inc-1").senderId("u-assignee").content("hi")
                .createdAt(FIXED_NOW).updatedAt(FIXED_NOW).build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findByUserId("u-assignee")).thenReturn(Optional.of(assigneeAgent));
        when(userRepository.findById("u-assignee")).thenReturn(Optional.of(sender));
        when(messageRepository.save(any(Message.class))).thenReturn(saved);

        MessageResponse result = messageService.sendMessage("u-assignee", "AGENT", "inc-1", "hi", List.of());

        assertThat(result.id()).isEqualTo("msg-1");
        verify(slaService).onAgentMessageSent(incident, "u-assignee");
        verify(messagingTemplate).convertAndSend(eq("/topic/incidents/inc-1/messages"), any(MessageResponse.class));
        // Non-confidential incident -- content is stored in plaintext, encryption never touched.
        verifyNoInteractions(fieldEncryptionService);
    }

    // Messages on a confidential incident are exactly the kind of detail HV-1619 hides
    // elsewhere, so they're encrypted at rest the same way the incident's own title/description
    // are.
    @Test
    void sendMessage_confidentialIncident_encryptsContent() {
        Incident incident = confidentialIncident("u1", null);
        User sender = User.builder().id("u1").fullName("Jane").email("jane@test.com").build();
        Message saved = Message.builder()
                .id("msg-1").incidentId("inc-1").senderId("u1").content("v1:hello")
                .createdAt(FIXED_NOW).updatedAt(FIXED_NOW).build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(confidentialIncidentAccess.canAccess("u1", incident)).thenReturn(true);
        when(userRepository.findById("u1")).thenReturn(Optional.of(sender));
        when(fieldEncryptionService.encrypt("hello")).thenReturn("v1:hello");
        when(messageRepository.save(any(Message.class))).thenReturn(saved);

        MessageResponse response = messageService.sendMessage("u1", "CLIENT", "inc-1", "hello", List.of());

        var messageCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("v1:hello");
        verify(fieldEncryptionService).encrypt("hello");
        // The entity's content is ciphertext (never re-fetched after the write above), but the
        // live response/broadcast must carry the plaintext the sender actually typed -- not the
        // "v1:..." ciphertext that was just persisted.
        assertThat(response.content()).isEqualTo("hello");
        var broadcastCaptor = org.mockito.ArgumentCaptor.forClass(MessageResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/incidents/inc-1/messages"), broadcastCaptor.capture());
        assertThat(broadcastCaptor.getValue().content()).isEqualTo("hello");
    }

    @Test
    void sendMessage_firstAgentResponse_triggersOpenToInProgressTransition() {
        Incident incident = Incident.builder()
                .id("inc-1")
                .userId("u1")
                .assignedToId("agent-assignee")
                .build();
        Agent assigneeAgent = Agent.builder().id("agent-assignee").userId("u-assignee").status(true).build();
        User sender = User.builder().id("u-assignee").fullName("Assignee").email("a@test.com").build();
        Message saved = Message.builder()
                .id("msg-1").incidentId("inc-1").senderId("u-assignee").content("hi")
                .createdAt(FIXED_NOW).updatedAt(FIXED_NOW).build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findByUserId("u-assignee")).thenReturn(Optional.of(assigneeAgent));
        when(userRepository.findById("u-assignee")).thenReturn(Optional.of(sender));
        when(messageRepository.save(any(Message.class))).thenReturn(saved);
        when(slaService.onAgentMessageSent(incident, "u-assignee")).thenReturn(true);

        messageService.sendMessage("u-assignee", "AGENT", "inc-1", "hi", List.of());

        verify(incidentService).onFirstAgentResponse(incident, "u-assignee");
    }

    @Test
    void sendMessage_notFirstAgentResponse_doesNotTriggerTransition() {
        Incident incident = Incident.builder()
                .id("inc-1")
                .userId("u1")
                .assignedToId("agent-assignee")
                .build();
        Agent assigneeAgent = Agent.builder().id("agent-assignee").userId("u-assignee").status(true).build();
        User sender = User.builder().id("u-assignee").fullName("Assignee").email("a@test.com").build();
        Message saved = Message.builder()
                .id("msg-1").incidentId("inc-1").senderId("u-assignee").content("hi")
                .createdAt(FIXED_NOW).updatedAt(FIXED_NOW).build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findByUserId("u-assignee")).thenReturn(Optional.of(assigneeAgent));
        when(userRepository.findById("u-assignee")).thenReturn(Optional.of(sender));
        when(messageRepository.save(any(Message.class))).thenReturn(saved);
        when(slaService.onAgentMessageSent(incident, "u-assignee")).thenReturn(false);

        messageService.sendMessage("u-assignee", "AGENT", "inc-1", "hi", List.of());

        verify(incidentService, never()).onFirstAgentResponse(any(), any());
    }

    @Test
    void sendMessage_emptyContentAndAttachments_throws400() {
        Incident incident = Incident.builder().id("inc-1").userId("u1").build();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));

        List<AttachmentRef> noAttachments = List.of();
        assertThatThrownBy(() -> messageService.sendMessage("u1", "CLIENT", "inc-1", "   ", noAttachments))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Either content or attachments must be provided")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void deleteMessage_byAuthor_succeeds() {
        Message message = Message.builder().id("msg-1").senderId("u1").incidentId("inc-1").build();
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));

        messageService.deleteMessage("u1", "CLIENT", "msg-1");

        verify(messageRepository).delete(message);
    }

    @Test
    void deleteMessage_byNonAuthor_throws403() {
        Message message = Message.builder().id("msg-1").senderId("u2").incidentId("inc-1").build();
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.deleteMessage("u1", "CLIENT", "msg-1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("You can only delete your own messages")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void deleteMessage_adminDeletingOwnMessage_succeeds() {
        Message message = Message.builder().id("msg-1").senderId("admin1").incidentId("inc-1").build();
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));

        messageService.deleteMessage("admin1", "ADMIN", "msg-1");

        verify(messageRepository).delete(message);
    }

    @Test
    void deleteMessage_adminDeletingAnotherUsersMessage_throws403() {
        Message message = Message.builder().id("msg-1").senderId("u2").incidentId("inc-1").build();
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.deleteMessage("admin1", "ADMIN", "msg-1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("You can only delete your own messages")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void deleteMessage_confidentialIncident_authorNoLongerAMember_throws403() {
        // HV-1619: re-checks membership at delete time in case it changed since the message
        // was sent (e.g. the sender was removed from the linked group).
        Message message = Message.builder().id("msg-1").senderId("u1").incidentId("inc-1").build();
        Incident incident = confidentialIncident("creator-1", null);
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(confidentialIncidentAccess.canAccess("u1", incident)).thenReturn(false);

        assertThatThrownBy(() -> messageService.deleteMessage("u1", "AGENT", "msg-1"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);

        verify(messageRepository, never()).delete(any());
    }

    @Test
    void deleteMessage_confidentialIncident_stillAMember_succeeds() {
        Message message = Message.builder().id("msg-1").senderId("u1").incidentId("inc-1").build();
        Incident incident = confidentialIncident("creator-1", null);
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(confidentialIncidentAccess.canAccess("u1", incident)).thenReturn(true);

        messageService.deleteMessage("u1", "AGENT", "msg-1");

        verify(messageRepository).delete(message);
    }

    @Test
    void deleteMessage_notFound_throws404() {
        when(messageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.deleteMessage("u1", "CLIENT", "missing"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
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
                .createdAt(FIXED_NOW)
                .updatedAt(FIXED_NOW)
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

    @Test
    void listMessages_confidentialIncident_nonMemberAgentInSameDepartment_throws403() {
        // HV-1619: the same-department fallback that lets a teammate read a normal incident's
        // chat must not apply once the incident is confidential.
        Incident incident = confidentialIncident("u1", "assigned-agent");
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(confidentialIncidentAccess.canAccess("u-teammate", incident)).thenReturn(false);

        var pageable = PageRequest.of(0, 50);
        assertThatThrownBy(() -> messageService.listMessages("u-teammate", "AGENT", "inc-1", pageable))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);

        verifyNoInteractions(messageRepository);
    }
}
