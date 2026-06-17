package com.amalitech.hilfe.slack.service;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.mappers.ArmsUserMapper;
import com.amalitech.hilfe.models.Location;
import com.amalitech.hilfe.models.SlackUserMapping;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.LocationRepository;
import com.amalitech.hilfe.repositories.SlackUserMappingRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.ArmsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlackOAuthService {

    private final SlackUserMappingRepository mappingRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final ArmsClient armsClient;
    private final SlackAuditLogService auditLogService;
    private final SlackProperties slackProperties;

    private final Map<String, OAuthState> pendingStates = new ConcurrentHashMap<>();

    public record OAuthState(
            String slackUserId,
            String slackTeamId,
            Instant createdAt
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(600)); // 10 minutes
        }
    }

    public String generateOAuthState(String slackUserId, String slackTeamId) {
        String state = UUID.randomUUID().toString();
        pendingStates.put(state, new OAuthState(slackUserId, slackTeamId, Instant.now()));
        cleanupExpiredStates();
        return state;
    }

    public Optional<OAuthState> validateAndConsumeState(String state) {
        OAuthState oauthState = pendingStates.remove(state);
        if (oauthState == null || oauthState.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(oauthState);
    }

    public boolean isValidState(String state) {
        OAuthState oauthState = pendingStates.get(state);
        return oauthState != null && !oauthState.isExpired();
    }

    @Transactional
    public SlackUserMapping connectUser(String armsToken, String slackUserId, String slackTeamId) {
        ArmsUserInfo armsUser = armsClient.getUserByToken(armsToken);
        if (armsUser == null) {
            throw new IllegalArgumentException("Invalid ARMS token");
        }

        String locationId = resolveLocationId(armsUser);

        userRepository.upsert(
                armsUser.userId(),
                armsUser.email(),
                ArmsUserMapper.buildFullName(armsUser),
                armsUser.phoneNumber(),
                armsUser.profileImage(),
                armsUser.positionName(),
                locationId
        );

        User hilfeUser = userRepository.findAuthUserById(armsUser.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "User not found after upsert for id=" + armsUser.userId()));

        Optional<SlackUserMapping> existing = mappingRepository.findBySlackUserId(slackUserId);
        SlackUserMapping mapping;

        if (existing.isPresent()) {
            mapping = existing.get();
            mapping.setHilfeUserId(hilfeUser.getId());
            mapping.setSlackTeamId(slackTeamId);
            mapping.setConnectedAt(Instant.now());
            log.info("Updated Slack connection for user {} -> HILFE user {}",
                    slackUserId, hilfeUser.getId());
        } else {
            mapping = SlackUserMapping.builder()
                    .slackUserId(slackUserId)
                    .hilfeUserId(hilfeUser.getId())
                    .slackTeamId(slackTeamId)
                    .connectedAt(Instant.now())
                    .build();
            log.info("Created new Slack connection for user {} -> HILFE user {}",
                    slackUserId, hilfeUser.getId());
        }

        mapping = mappingRepository.save(mapping);

        auditLogService.log(
                "SLACK_CONNECTED",
                slackUserId,
                hilfeUser.getId(),
                "USER",
                hilfeUser.getId(),
                Map.of("slackTeamId", slackTeamId)
        );

        return mapping;
    }

    @Transactional
    public void disconnectUser(String slackUserId) {
        Optional<SlackUserMapping> mapping = mappingRepository.findBySlackUserId(slackUserId);
        if (mapping.isPresent()) {
            String hilfeUserId = mapping.get().getHilfeUserId();
            mappingRepository.deleteBySlackUserId(slackUserId);

            auditLogService.log(
                    "SLACK_DISCONNECTED",
                    slackUserId,
                    hilfeUserId,
                    "USER",
                    hilfeUserId,
                    Map.of()
            );

            log.info("Disconnected Slack user {} from HILFE user {}", slackUserId, hilfeUserId);
        }
    }

    public Optional<SlackUserMapping> findBySlackUserId(String slackUserId) {
        return mappingRepository.findBySlackUserId(slackUserId);
    }

    public Optional<SlackUserMapping> findByHilfeUserId(String hilfeUserId) {
        return mappingRepository.findByHilfeUserId(hilfeUserId);
    }

    public boolean isConnected(String slackUserId) {
        return mappingRepository.existsBySlackUserId(slackUserId);
    }

    @Transactional
    public void updateLastUsed(String slackUserId) {
        mappingRepository.findBySlackUserId(slackUserId).ifPresent(mapping -> {
            mapping.setLastUsedAt(Instant.now());
            mappingRepository.save(mapping);
        });
    }

    private void cleanupExpiredStates() {
        pendingStates.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public String buildOAuthUrl(String state) {
        String baseUrl = slackProperties.frontendConnectUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            // Fallback to backend authorize page for backward compatibility / local testing
            baseUrl = slackProperties.redirectUri().replace("/callback", "/authorize");
        }
        return baseUrl + "?state=" + state;
    }

    private String resolveLocationId(ArmsUserInfo armsUser) {
        if (armsUser.officeName() == null || armsUser.officeName().isBlank()) {
            return null;
        }
        return locationRepository.findAll().stream()
                .filter(loc -> armsUser.officeName().equalsIgnoreCase(loc.getName()))
                .min(Comparator.comparing(Location::getName))
                .map(Location::getId)
                .orElse(null);
    }
}
