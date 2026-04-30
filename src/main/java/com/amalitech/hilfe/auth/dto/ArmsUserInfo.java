package com.amalitech.hilfe.auth.dto;

/**
 * Owner: Alphone
 * Depends on: ARMS token lookup response fields.
 */
public record ArmsUserInfo(
    String userId,
    String firstName,
    String lastName,
    String email,
    String profileImage
) {}
