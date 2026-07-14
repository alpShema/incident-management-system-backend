package com.amalitech.hilfe.slack.scheduler;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.repositories.SlackAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "slack.enabled", havingValue = "true")
public class SlackAuditLogCleanupScheduler {

    private static final int RETENTION_DAYS = 90;

    private final SlackAuditLogRepository auditLogRepository;
    private final SlackProperties slackProperties;

    @Scheduled(cron = "0 0 3 * * *") // Run daily at 03:00 UTC
    @Transactional
    public void cleanupOldAuditLogs() {
        if (!slackProperties.enabled()) {
            return;
        }

        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = auditLogRepository.deleteByCreatedAtBefore(cutoff);

        if (deleted > 0) {
            log.info("Slack audit log cleanup: deleted {} records older than {} days", deleted, RETENTION_DAYS);
        } else {
            log.debug("Slack audit log cleanup: no records to delete");
        }
    }
}
