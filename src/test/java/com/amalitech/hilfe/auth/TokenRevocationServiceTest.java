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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock SessionRepository sessionRepository;
    @InjectMocks TokenRevocationService service;

    @Test
    void isRevoked_nullJti_returnsTrue() {
        assertThat(service.isRevoked(null)).isTrue();
    }

    @Test
    void isRevoked_jtiPresentInRepo_returnsTrue() {
        when(sessionRepository.existsById("jti-1")).thenReturn(true);

        assertThat(service.isRevoked("jti-1")).isTrue();
    }

    @Test
    void isRevoked_jtiAbsentInRepo_returnsFalse() {
        when(sessionRepository.existsById("jti-2")).thenReturn(false);

        assertThat(service.isRevoked("jti-2")).isFalse();
    }

    @Test
    void revoke_savesSessionRecord() {
        Instant expiresAt = Instant.now().plusSeconds(3600);

        service.revoke("jti-3", "u1", expiresAt);

        verify(sessionRepository).save(argThat(s ->
                "jti-3".equals(s.getId())
                && "jti-3".equals(s.getSid())
                && "u1".equals(s.getData())
                && expiresAt.equals(s.getExpiresAt())
        ));
    }

    @Test
    void revoke_duplicateJti_doesNotThrow() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        when(sessionRepository.save(any(Session.class)))
                .thenThrow(new DataIntegrityViolationException("pk conflict"));

        assertThatCode(() -> service.revoke("jti-dup", "u1", expiresAt))
                .doesNotThrowAnyException();
    }

    @Test
    void purgeExpiredTokens_deletesExpiredRows() {
        when(sessionRepository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(5);

        service.purgeExpiredTokens();

        verify(sessionRepository).deleteByExpiresAtBefore(any(Instant.class));
    }
}
