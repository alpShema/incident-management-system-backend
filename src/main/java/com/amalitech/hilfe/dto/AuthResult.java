package com.amalitech.hilfe.dto;

public record AuthResult(
        long sessionTtlSeconds,
        AuthSessionResponse session
) {
}
