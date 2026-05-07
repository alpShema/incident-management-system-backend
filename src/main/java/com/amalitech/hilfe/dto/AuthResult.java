package com.amalitech.hilfe.dto;

public record AuthResult(
        AuthTokens tokens,
        AuthSessionResponse session
) {
}
