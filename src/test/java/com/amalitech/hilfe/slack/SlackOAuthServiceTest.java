package com.amalitech.hilfe.slack;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.models.SlackUserMapping;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.LocationRepository;
import com.amalitech.hilfe.repositories.SlackUserMappingRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.ArmsClient;
import com.amalitech.hilfe.slack.service.SlackAuditLogService;
import com.amalitech.hilfe.slack.service.SlackOAuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlackOAuthServiceTest {

    @Mock private SlackUserMappingRepository mappingRepository;
    @Mock private UserRepository userRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private ArmsClient armsClient;
    @Mock private SlackAuditLogService auditLogService;
    @Mock private SlackProperties slackProperties;

    @InjectMocks
    private SlackOAuthService service;

    // ── OAuth state management ────────────────────────────────────────────────

    @Test
    void generateOAuthState_returnsNonBlankUniqueValues() {
        String s1 = service.generateOAuthState("U1", "T1");
        String s2 = service.generateOAuthState("U2", "T2");
        assertThat(s1).isNotBlank();
        assertThat(s2).isNotBlank();
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void validateAndConsumeState_unknownState_returnsEmpty() {
        assertThat(service.validateAndConsumeState("nonexistent-state")).isEmpty();
    }

    @Test
    void validateAndConsumeState_validState_returnsStateAndRemovesIt() {
        String state = service.generateOAuthState("U1", "T1");

        Optional<SlackOAuthService.OAuthState> result = service.validateAndConsumeState(state);

        assertThat(result).isPresent();
        assertThat(result.get().slackUserId()).isEqualTo("U1");
        assertThat(result.get().slackTeamId()).isEqualTo("T1");
        // Consumed — second call returns empty
        assertThat(service.validateAndConsumeState(state)).isEmpty();
    }

    @Test
    void isValidState_afterGenerate_returnsTrue() {
        String state = service.generateOAuthState("U1", "T1");
        assertThat(service.isValidState(state)).isTrue();
    }

    @Test
    void isValidState_unknownState_returnsFalse() {
        assertThat(service.isValidState("bogus")).isFalse();
    }

    // ── Repository delegation ─────────────────────────────────────────────────

    @Test
    void isConnected_delegatesToRepository() {
        when(mappingRepository.existsBySlackUserId("U1")).thenReturn(true);
        assertThat(service.isConnected("U1")).isTrue();

        when(mappingRepository.existsBySlackUserId("U2")).thenReturn(false);
        assertThat(service.isConnected("U2")).isFalse();
    }

    @Test
    void findBySlackUserId_delegatesToRepository() {
        SlackUserMapping m = SlackUserMapping.builder().slackUserId("U1").build();
        when(mappingRepository.findBySlackUserId("U1")).thenReturn(Optional.of(m));
        assertThat(service.findBySlackUserId("U1")).contains(m);
    }

    @Test
    void findByHilfeUserId_delegatesToRepository() {
        SlackUserMapping m = SlackUserMapping.builder().hilfeUserId("h1").build();
        when(mappingRepository.findByHilfeUserId("h1")).thenReturn(Optional.of(m));
        assertThat(service.findByHilfeUserId("h1")).contains(m);
    }

    // ── connectUser ───────────────────────────────────────────────────────────

    @Test
    void connectUser_invalidArmsToken_throwsIllegalArgument() {
        when(armsClient.getUserByToken("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> service.connectUser("bad-token", "U1", "T1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ARMS token");
    }

    @Test
    void connectUser_newUser_createsAndSavesMapping() {
        ArmsUserInfo armsUser = new ArmsUserInfo(
                "arms-id", "John", "Doe", "john@test.com", null, "Accra");
        User hilfeUser = User.builder().id("arms-id").email("john@test.com").build();

        when(armsClient.getUserByToken("arms-token")).thenReturn(armsUser);
        when(locationRepository.findAll()).thenReturn(List.of());
        doNothing().when(userRepository).upsert(any(), any(), any(), any(), any(), any(), any());
        when(userRepository.findAuthUserById("arms-id")).thenReturn(Optional.of(hilfeUser));
        when(mappingRepository.findBySlackUserId("U1")).thenReturn(Optional.empty());
        when(mappingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SlackUserMapping result = service.connectUser("arms-token", "U1", "T1");

        assertThat(result.getSlackUserId()).isEqualTo("U1");
        assertThat(result.getHilfeUserId()).isEqualTo("arms-id");
        assertThat(result.getSlackTeamId()).isEqualTo("T1");
        verify(auditLogService).log(eq("SLACK_CONNECTED"), eq("U1"), eq("arms-id"),
                any(), any(), any());
    }

    @Test
    void connectUser_existingMapping_updatesInsteadOfCreating() {
        ArmsUserInfo armsUser = new ArmsUserInfo(
                "arms-id", "Jane", "Doe", "jane@test.com", null, null);
        User hilfeUser = User.builder().id("arms-id").email("jane@test.com").build();
        SlackUserMapping existing = SlackUserMapping.builder()
                .slackUserId("U1").hilfeUserId("old-hilfe-id").build();

        when(armsClient.getUserByToken("token")).thenReturn(armsUser);
        when(userRepository.findAuthUserById("arms-id")).thenReturn(Optional.of(hilfeUser));
        when(mappingRepository.findBySlackUserId("U1")).thenReturn(Optional.of(existing));
        when(mappingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SlackUserMapping result = service.connectUser("token", "U1", "T1");

        assertThat(result.getHilfeUserId()).isEqualTo("arms-id");
    }

    @Test
    void connectUser_userNotFoundAfterUpsert_throwsIllegalState() {
        ArmsUserInfo armsUser = new ArmsUserInfo(
                "arms-id", "Ghost", "User", "ghost@test.com", null, null);

        when(armsClient.getUserByToken("token")).thenReturn(armsUser);
        when(userRepository.findAuthUserById("arms-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.connectUser("token", "U1", "T1"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── disconnectUser ────────────────────────────────────────────────────────

    @Test
    void disconnectUser_whenMappingExists_deletesAndLogsAudit() {
        SlackUserMapping m = SlackUserMapping.builder()
                .slackUserId("U1").hilfeUserId("h1").build();
        when(mappingRepository.findBySlackUserId("U1")).thenReturn(Optional.of(m));

        service.disconnectUser("U1");

        verify(mappingRepository).deleteBySlackUserId("U1");
        verify(auditLogService).log(eq("SLACK_DISCONNECTED"), eq("U1"), eq("h1"),
                any(), any(), any());
    }

    @Test
    void disconnectUser_whenNoMappingExists_doesNothing() {
        when(mappingRepository.findBySlackUserId("U1")).thenReturn(Optional.empty());

        service.disconnectUser("U1");

        verify(mappingRepository, never()).deleteBySlackUserId(any());
        verifyNoInteractions(auditLogService);
    }

    // ── buildOAuthUrl ─────────────────────────────────────────────────────────

    @Test
    void buildOAuthUrl_withFrontendConnectUrl_appendsStateParam() {
        when(slackProperties.frontendConnectUrl()).thenReturn("https://hilfe.amalitech.net/slack/connect");

        String url = service.buildOAuthUrl("my-state");

        assertThat(url).isEqualTo("https://hilfe.amalitech.net/slack/connect?state=my-state");
    }

    @Test
    void buildOAuthUrl_withoutFrontendUrl_derivesFromRedirectUri() {
        when(slackProperties.frontendConnectUrl()).thenReturn(null);
        when(slackProperties.redirectUri()).thenReturn("https://api.hilfe.net/slack/oauth/callback");

        String url = service.buildOAuthUrl("my-state");

        assertThat(url).isEqualTo("https://api.hilfe.net/slack/oauth/authorize?state=my-state");
    }
}
