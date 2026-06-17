package com.amalitech.hilfe.slack.service;

import com.amalitech.hilfe.models.SlackNotificationPreference;
import com.amalitech.hilfe.repositories.SlackNotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlackNotificationPreferenceService {

    private final SlackNotificationPreferenceRepository preferenceRepository;

    public static final List<String> NOTIFICATION_TYPES = List.of(
            "INCIDENT_ASSIGNED",
            "INCIDENT_STATUS_CHANGED",
            "INCIDENT_NEW_MESSAGE",
            "INCIDENT_SLA_BREACHED"
    );

    public List<SlackNotificationPreference> getPreferences(String userId) {
        List<SlackNotificationPreference> prefs = preferenceRepository.findByUserId(userId);

        // Create defaults for any missing types
        for (String type : NOTIFICATION_TYPES) {
            boolean exists = prefs.stream()
                    .anyMatch(p -> p.getNotificationType().equals(type));
            if (!exists) {
                SlackNotificationPreference defaultPref = SlackNotificationPreference.builder()
                        .userId(userId)
                        .notificationType(type)
                        .enabled(true)
                        .build();
                prefs.add(preferenceRepository.save(defaultPref));
            }
        }

        return prefs;
    }

    @Transactional
    public void updatePreference(String userId, String type, boolean enabled) {
        SlackNotificationPreference pref = preferenceRepository
                .findByUserIdAndNotificationType(userId, type)
                .orElseGet(() -> SlackNotificationPreference.builder()
                        .userId(userId)
                        .notificationType(type)
                        .build());

        pref.setEnabled(enabled);
        preferenceRepository.save(pref);

        log.debug("Updated notification preference for user {}: {} = {}", userId, type, enabled);
    }

    public boolean isEnabled(String userId, String type) {
        return preferenceRepository
                .findByUserIdAndNotificationType(userId, type)
                .map(SlackNotificationPreference::getEnabled)
                .orElse(true); // Default to enabled
    }
}
