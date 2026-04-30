package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.auth.dto.ArmsEmployeeInfo;
import com.amalitech.hilfe.auth.dto.ArmsUserInfo;

import java.util.List;

/**
 * Owner: Alphonse
 * Depends on: ARMS endpoints/config and HTTP client implementation.
 */
public interface ArmsClient {
    /** Owner: Alphonse. Depends on: ARMS SSO endpoint and HTTP client. */
    ArmsUserInfo getUserByToken(String armsToken);
    List<ArmsEmployeeInfo> getAllUsers();
    ArmsUserInfo getUserById(String userId);
}
