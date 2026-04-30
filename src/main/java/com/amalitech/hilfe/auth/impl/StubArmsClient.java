package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.auth.ArmsClient;
import com.amalitech.hilfe.auth.dto.ArmsUserInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Owner: Alphone
 * Depends on: real ARMS client implementation to replace this stub.
 */
@Service
@ConditionalOnMissingBean(ArmsClient.class)
public class StubArmsClient implements ArmsClient {
    @Override
    public ArmsUserInfo getUserByToken(String armsToken) {
        throw new UnsupportedOperationException("ARMS client not implemented");
    }
}
