package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.MessageResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.Message;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.MessageRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final IncidentRepository incidentRepository;
    private final AgentRepository agentRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public MessageResponse sendMessage(String userId, RoleCode role, String incidentId, String content) {
        Incident incident = incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));
        enforceAccess(userId, role, incident);

        Message saved = messageRepository.save(Message.builder()
                .id(UUID.randomUUID().toString())
                .senderId(userId)
                .incidentId(incidentId)
                .content(content)
                .build());

        messageRepository.flush();
        MessageResponse response = MessageResponse.from(
                messageRepository.findByIdWithSender(saved.getId()).orElseThrow());

        messagingTemplate.convertAndSend("/topic/incidents/" + incidentId + "/messages", response);
        return response;
    }

    public Page<MessageResponse> listMessages(String userId, RoleCode role, String incidentId, Pageable pageable) {
        Incident incident = incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));
        enforceAccess(userId, role, incident);
        return messageRepository.findByIncidentId(incidentId, pageable).map(MessageResponse::from);
    }

    @Transactional
    public void deleteMessage(String userId, RoleCode role, String messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ArmsAuthException("Message not found", 404));

        boolean isAdmin = role == RoleCode.ADMIN || role == RoleCode.SUPER_ADMIN;
        boolean isAuthor = message.getSenderId().equals(userId);

        if (!isAdmin && !isAuthor) {
            throw new ArmsAuthException("You can only delete your own messages", 403);
        }

        messageRepository.delete(message);
    }

    private void enforceAccess(String userId, RoleCode role, Incident incident) {
        if (role == RoleCode.ADMIN || role == RoleCode.SUPER_ADMIN) return;
        if (userId.equals(incident.getUserId())) return;
        if (role == RoleCode.AGENT && isSameDepartment(userId, incident)) return;
        throw new ArmsAuthException("You do not have access to this incident", 403);
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
