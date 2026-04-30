package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.auth.dto.ArmsUserInfo;

/**
 * Owner: Alphone
 * Depends on: ARMS endpoints/config and HTTP client implementation.
 */
public interface ArmsClient {
    /** Owner: Alphone. Depends on: ARMS SSO endpoint and HTTP client. */
    ArmsUserInfo getUserByToken(String armsToken);
}
