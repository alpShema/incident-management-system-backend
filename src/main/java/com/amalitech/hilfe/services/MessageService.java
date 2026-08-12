package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AttachmentRef;
import com.amalitech.hilfe.dto.MessageResponse;
import com.amalitech.hilfe.dto.PresignedUrlRequest;
import com.amalitech.hilfe.dto.PresignedUrlResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.Message;
import com.amalitech.hilfe.models.MessageMedia;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.notifications.NotificationEventPublisher;
import com.amalitech.hilfe.notifications.events.NewIncidentMessageEvent;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.MessageRepository;
import com.amalitech.hilfe.repositories.MessageMediaRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final String MSG_INCIDENT_NOT_FOUND = "Incident not found";
    private static final String ROLE_AGENT = "AGENT";

    private final MessageRepository messageRepository;
    private final IncidentRepository incidentRepository;
    private final AgentRepository agentRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final UserRepository userRepository;
    private final MessageMediaRepository messageMediaRepository;
    private final MediaService mediaService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SlaService slaService;
    private final NotificationEventPublisher notificationEventPublisher;
    private final IncidentService incidentService;
    private final ConfidentialIncidentAccess confidentialIncidentAccess;

    public PresignedUrlResponse generateMessagePresignedUrl(String userId, String role, String incidentId, PresignedUrlRequest request) {
        Incident incident = incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException(MSG_INCIDENT_NOT_FOUND, 404));
        enforceSendAccess(userId, role, incident);
        return mediaService.generateMessagePresignedUploadUrl(request);
    }

    public PresignedUrlResponse generateMessagePresignedUrl(String userId, RoleCode role, String incidentId, PresignedUrlRequest request) {
        return generateMessagePresignedUrl(userId, role == null ? null : role.name(), incidentId, request);
    }

    @Transactional
    public MessageResponse sendMessage(String userId, String role, String incidentId, String content, List<AttachmentRef> attachments) {
        Incident incident = incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException(MSG_INCIDENT_NOT_FOUND, 404));
        enforceSendAccess(userId, role, incident);
        validateMessagePayload(content, attachments);

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new ArmsAuthException("User not found", 404));

        Message saved = messageRepository.save(Message.builder()
                .id(UUID.randomUUID().toString())
                .senderId(userId)
                .incidentId(incidentId)
                .content(trimToNull(content))
                .build());

        List<MessageMedia> media = List.of();
        if (attachments != null && !attachments.isEmpty()) {
            media = mediaService.createMediaForMessage(incidentId, saved.getId(), attachments);
        }

        messageRepository.flush();
        boolean firstResponse = slaService.onAgentMessageSent(incident, userId);
        if (firstResponse) {
            incidentService.onFirstAgentResponse(incident, userId);
        }
        MessageResponse response = MessageResponse.withAttachments(
                MessageResponse.from(saved, sender),
                mediaService.toMediaResponsesForMessage(media)
        );

        messagingTemplate.convertAndSend("/topic/incidents/" + incidentId + "/messages", response);
        publishMessageNotifications(userId, sender.getFullName(), incidentId, incident, content);
        return response;
    }

    private void publishMessageNotifications(String senderId, String senderName, String incidentId,
                                              Incident incident, String content) {
        int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;
        String preview = (content != null && !content.isBlank())
                ? content.substring(0, Math.min(80, content.length()))
                : "[attachment]";

        String clientUserId = incident.getUserId();
        String assignedAgentUserId = incident.getAssignedToId() != null
                ? agentRepository.findById(incident.getAssignedToId()).map(Agent::getUserId).orElse(null)
                : null;

        if (clientUserId != null && !clientUserId.equals(senderId)) {
            notificationEventPublisher.publish(
                    new NewIncidentMessageEvent(clientUserId, incidentId, incidentNo, senderName, preview));
        }
        if (assignedAgentUserId != null && !assignedAgentUserId.equals(senderId)) {
            notificationEventPublisher.publish(
                    new NewIncidentMessageEvent(assignedAgentUserId, incidentId, incidentNo, senderName, preview));
        }
    }

    public MessageResponse sendMessage(String userId, RoleCode role, String incidentId, String content, List<AttachmentRef> attachments) {
        return sendMessage(userId, role == null ? null : role.name(), incidentId, content, attachments);
    }

    public Page<MessageResponse> listMessages(String userId, String role, String incidentId, Pageable pageable) {
        Incident incident = incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException(MSG_INCIDENT_NOT_FOUND, 404));
        enforceAccess(userId, role, incident);

        Page<Message> messagePage = messageRepository.findByIncidentId(incidentId, pageable);

        var messageIds = messagePage.getContent().stream().map(Message::getId).toList();
        var attachmentsByMessageId = (messageIds.isEmpty() ? List.<MessageMedia>of() : messageMediaRepository.findByMessageIdIn(messageIds)).stream()
                .collect(Collectors.groupingBy(MessageMedia::getMessageId));

        List<MessageResponse> responses = messagePage.getContent().stream()
                .map(msg -> MessageResponse.withAttachments(
                        MessageResponse.from(msg),
                        mediaService.toMediaResponsesForMessage(
                                attachmentsByMessageId.getOrDefault(msg.getId(), List.of())
                        )
                ))
                .collect(Collectors.toList());

        // If sorted DESC, reverse items so oldest appears first within the page
        var createdAtOrder = pageable.getSort().getOrderFor("createdAt");
        if (createdAtOrder != null && createdAtOrder.isDescending()) {
            Collections.reverse(responses);
        }

        return new PageImpl<>(responses, pageable, messagePage.getTotalElements());
    }

    public Page<MessageResponse> listMessages(String userId, RoleCode role, String incidentId, Pageable pageable) {
        return listMessages(userId, role == null ? null : role.name(), incidentId, pageable);
    }

    @Transactional
    public void deleteMessage(String userId, String role, String messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ArmsAuthException("Message not found", 404));

        if (!message.getSenderId().equals(userId)) {
            throw new ArmsAuthException("You can only delete your own messages", 403);
        }
        // HV-1619: the sender's own message on a confidential incident could still leak by a
        // stale membership -- re-check in case group membership changed since it was sent.
        incidentRepository.findByIdWithDetails(message.getIncidentId())
                .filter(this::requiresConfidentialAccess)
                .ifPresent(incident -> {
                    if (!confidentialIncidentAccess.canAccess(userId, incident)) {
                        throw new ArmsAuthException("You do not have access to this incident", 403);
                    }
                });

        messageRepository.delete(message);
    }

    public void deleteMessage(String userId, RoleCode role, String messageId) {
        deleteMessage(userId, role == null ? null : role.name(), messageId);
    }

    private void validateMessagePayload(String content, List<AttachmentRef> attachments) {
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasAttachments = attachments != null && !attachments.isEmpty();
        if (!hasContent && !hasAttachments) {
            throw new ArmsAuthException("Either content or attachments must be provided", 400);
        }
    }

    private String trimToNull(String content) {
        if (content == null) return null;
        String trimmed = content.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void enforceAccess(String userId, String role, Incident incident) {
        // HV-1619: chat messages are exactly the kind of detail (assignee identity, incident
        // specifics) this feature hides elsewhere -- confidential incidents override every
        // other rule below, including the admin bypass.
        if (requiresConfidentialAccess(incident)) {
            if (!confidentialIncidentAccess.canAccess(userId, incident)) {
                throw new ArmsAuthException("You do not have access to this incident", 403);
            }
            return;
        }
        if ("ADMIN".equalsIgnoreCase(role) || "ADMIN_AGENT".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) return;
        if (userId.equals(incident.getUserId())) return;
        if (ROLE_AGENT.equalsIgnoreCase(role) && isAssignedToActor(userId, incident)) return;
        if (ROLE_AGENT.equalsIgnoreCase(role) && isSameDepartment(userId, incident)) return;
        throw new ArmsAuthException("You do not have access to this incident", 403);
    }

    private void enforceSendAccess(String userId, String role, Incident incident) {
        if (requiresConfidentialAccess(incident)) {
            if (!confidentialIncidentAccess.canAccess(userId, incident)) {
                throw new ArmsAuthException("You do not have access to this incident", 403);
            }
            return;
        }
        if ("ADMIN".equalsIgnoreCase(role) || "ADMIN_AGENT".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) return;
        if (userId.equals(incident.getUserId())) return;
        if (ROLE_AGENT.equalsIgnoreCase(role) && isAssignedToActor(userId, incident)) return;
        throw new ArmsAuthException("You do not have access to this incident", 403);
    }

    private boolean requiresConfidentialAccess(Incident incident) {
        return incident.getIncidentType() != null && incident.getIncidentType().isConfidential();
    }

    private boolean isAssignedToActor(String userId, Incident incident) {
        if (incident.getAssignedToId() == null) return false;
        return agentRepository.findByUserId(userId)
                .map(agent -> incident.getAssignedToId().equals(agent.getId()))
                .orElse(false);
    }

    private boolean isSameDepartment(String userId, Incident incident) {
        if (incident.getAssignedToId() == null) return false;
        List<String> actorDepts = findAgentGroupIds(userId).orElse(List.of());
        if (actorDepts.isEmpty()) return false;
        List<String> assignedDepts = agentGroupMemberRepository.findAgentGroupIdsByAgentId(incident.getAssignedToId());
        return assignedDepts.stream().anyMatch(actorDepts::contains);
    }

    private Optional<List<String>> findAgentGroupIds(String userId) {
        return agentRepository.findByUserId(userId)
                .map(agent -> agentGroupMemberRepository.findAgentGroupIdsByAgentId(agent.getId()));
    }
}
