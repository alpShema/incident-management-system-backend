package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.models.Session;
import com.amalitech.hilfe.repositories.SessionRepository;
import com.amalitech.hilfe.services.TokenRevocationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-29T14:00:00Z");

    @Mock SessionRepository sessionRepository;
    @InjectMocks TokenRevocationService service;

    private static String sha256Hex(String rawToken) throws Exception {
        byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }

    @Test
    void isRevoked_nullToken_returnsTrue() {
        assertThat(service.isRevoked(null)).isTrue();
    }

    @Test
    void isRevoked_blankToken_returnsTrue() {
        assertThat(service.isRevoked(" ")).isTrue();
    }

    @Test
    void isRevoked_hashPresentInRepo_returnsTrue() throws Exception {
        when(sessionRepository.existsById(sha256Hex("arms-token-1"))).thenReturn(true);

        assertThat(service.isRevoked("arms-token-1")).isTrue();
    }

    @Test
    void isRevoked_hashAbsentInRepo_returnsFalse() throws Exception {
        when(sessionRepository.existsById(sha256Hex("arms-token-2"))).thenReturn(false);

        assertThat(service.isRevoked("arms-token-2")).isFalse();
    }

    @Test
    void revoke_savesSessionRecordKeyedByTokenHash() throws Exception {
        Instant expiresAt = FIXED_NOW.plusSeconds(3600);
        String expectedHash = sha256Hex("arms-token-3");

        service.revoke("arms-token-3", "u1", expiresAt);

        verify(sessionRepository).save(argThat(s ->
                expectedHash.equals(s.getId())
                && expectedHash.equals(s.getSid())
                && "u1".equals(s.getData())
                && expiresAt.equals(s.getExpiresAt())
        ));
    }

    @Test
    void revoke_duplicateToken_doesNotThrow() {
        Instant expiresAt = FIXED_NOW.plusSeconds(3600);
        when(sessionRepository.save(any(Session.class)))
                .thenThrow(new DataIntegrityViolationException("pk conflict"));

        assertThatCode(() -> service.revoke("arms-token-dup", "u1", expiresAt))
                .doesNotThrowAnyException();
    }

    @Test
    void purgeExpiredTokens_deletesExpiredRows() {
        when(sessionRepository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(5);

        service.purgeExpiredTokens();

        verify(sessionRepository).deleteByExpiresAtBefore(any(Instant.class));
    }
}
