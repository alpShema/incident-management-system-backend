package com.amalitech.hilfe.auth.dto;

/**
 * Owner: Lawson
 * Depends on: refresh token issuance format and storage strategy.
 */
public record RefreshTokenRequest(String refreshToken) {}
