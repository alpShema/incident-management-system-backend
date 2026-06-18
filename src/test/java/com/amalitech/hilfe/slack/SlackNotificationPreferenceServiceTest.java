package com.amalitech.hilfe.slack;

import com.amalitech.hilfe.models.SlackNotificationPreference;
import com.amalitech.hilfe.repositories.SlackNotificationPreferenceRepository;
import com.amalitech.hilfe.slack.service.SlackNotificationPreferenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlackNotificationPreferenceServiceTest {

    @Mock
    private SlackNotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    private SlackNotificationPreferenceService service;

    @Test
    void getPreferences_noneExist_createsDefaultsForAllTypes() {
        when(preferenceRepository.findByUserId("user-1")).thenReturn(new ArrayList<>());
        when(preferenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<SlackNotificationPreference> result = service.getPreferences("user-1");

        assertThat(result).hasSameSizeAs(SlackNotificationPreferenceService.NOTIFICATION_TYPES);
        verify(preferenceRepository, times(SlackNotificationPreferenceService.NOTIFICATION_TYPES.size()))
                .save(any());
    }

    @Test
    void getPreferences_allExist_noSaveCalled() {
        List<SlackNotificationPreference> existing = SlackNotificationPreferenceService.NOTIFICATION_TYPES
                .stream()
                .map(type -> SlackNotificationPreference.builder()
                        .userId("user-1")
                        .notificationType(type)
                        .enabled(true)
                        .build())
                .toList();

        when(preferenceRepository.findByUserId("user-1")).thenReturn(new ArrayList<>(existing));

        List<SlackNotificationPreference> result = service.getPreferences("user-1");

        assertThat(result).hasSameSizeAs(SlackNotificationPreferenceService.NOTIFICATION_TYPES);
        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void getPreferences_partialExist_onlyMissingSaved() {
        List<SlackNotificationPreference> partial = new ArrayList<>();
        partial.add(SlackNotificationPreference.builder()
                .userId("user-1")
                .notificationType("INCIDENT_ASSIGNED")
                .enabled(true)
                .build());

        when(preferenceRepository.findByUserId("user-1")).thenReturn(partial);
        when(preferenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.getPreferences("user-1");

        verify(preferenceRepository, times(SlackNotificationPreferenceService.NOTIFICATION_TYPES.size() - 1))
                .save(any());
    }

    @Test
    void isEnabled_noPreferenceExists_defaultsToTrue() {
        when(preferenceRepository.findByUserIdAndNotificationType("user-1", "INCIDENT_ASSIGNED"))
                .thenReturn(Optional.empty());

        assertThat(service.isEnabled("user-1", "INCIDENT_ASSIGNED")).isTrue();
    }

    @Test
    void isEnabled_preferenceEnabled_returnsTrue() {
        SlackNotificationPreference pref = SlackNotificationPreference.builder()
                .userId("user-1").notificationType("INCIDENT_ASSIGNED").enabled(true).build();
        when(preferenceRepository.findByUserIdAndNotificationType("user-1", "INCIDENT_ASSIGNED"))
                .thenReturn(Optional.of(pref));

        assertThat(service.isEnabled("user-1", "INCIDENT_ASSIGNED")).isTrue();
    }

    @Test
    void isEnabled_preferenceDisabled_returnsFalse() {
        SlackNotificationPreference pref = SlackNotificationPreference.builder()
                .userId("user-1").notificationType("INCIDENT_ASSIGNED").enabled(false).build();
        when(preferenceRepository.findByUserIdAndNotificationType("user-1", "INCIDENT_ASSIGNED"))
                .thenReturn(Optional.of(pref));

        assertThat(service.isEnabled("user-1", "INCIDENT_ASSIGNED")).isFalse();
    }

    @Test
    void updatePreference_existingPref_togglesEnabled() {
        SlackNotificationPreference pref = SlackNotificationPreference.builder()
                .userId("user-1").notificationType("INCIDENT_ASSIGNED").enabled(true).build();
        when(preferenceRepository.findByUserIdAndNotificationType("user-1", "INCIDENT_ASSIGNED"))
                .thenReturn(Optional.of(pref));

        service.updatePreference("user-1", "INCIDENT_ASSIGNED", false);

        ArgumentCaptor<SlackNotificationPreference> captor =
                ArgumentCaptor.forClass(SlackNotificationPreference.class);
        verify(preferenceRepository).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }

    @Test
    void updatePreference_noExistingPref_createsNewWithCorrectFields() {
        when(preferenceRepository.findByUserIdAndNotificationType("user-1", "INCIDENT_SLA_BREACHED"))
                .thenReturn(Optional.empty());

        service.updatePreference("user-1", "INCIDENT_SLA_BREACHED", false);

        ArgumentCaptor<SlackNotificationPreference> captor =
                ArgumentCaptor.forClass(SlackNotificationPreference.class);
        verify(preferenceRepository).save(captor.capture());
        SlackNotificationPreference saved = captor.getValue();
        assertThat(saved.getNotificationType()).isEqualTo("INCIDENT_SLA_BREACHED");
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getEnabled()).isFalse();
    }
}
