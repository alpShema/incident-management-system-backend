package com.amalitech.hilfe.slack.service;

import com.amalitech.hilfe.models.SlackAuditLog;
import com.amalitech.hilfe.repositories.SlackAuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlackAuditLogService {

    private final SlackAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            String action,
            String slackUserId,
            String hilfeUserId,
            String resourceType,
            String resourceId,
            Map<String, Object> metadata
    ) {
        try {
            SlackAuditLog auditLog = SlackAuditLog.builder()
                    .action(action)
                    .slackUserId(slackUserId)
                    .hilfeUserId(hilfeUserId)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .metadata(serializeMetadata(metadata))
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit log saved: {} for user {}", action, slackUserId);
        } catch (Exception e) {
            log.error("Failed to save audit log: {} for user {}", action, slackUserId, e);
        }
    }

    public void logSync(
            String action,
            String slackUserId,
            String hilfeUserId,
            String resourceType,
            String resourceId,
            Map<String, Object> metadata
    ) {
        try {
            SlackAuditLog auditLog = SlackAuditLog.builder()
                    .action(action)
                    .slackUserId(slackUserId)
                    .hilfeUserId(hilfeUserId)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .metadata(serializeMetadata(metadata))
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: {} for user {}", action, slackUserId, e);
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit log metadata", e);
            return "{}";
        }
    }
}
