package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.auth.ArmsClient;
import com.amalitech.hilfe.auth.dto.ArmsEmployeeInfo;
import com.amalitech.hilfe.auth.dto.ArmsUserInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Owner: Alphonse
 * Depends on: real ARMS client implementation to replace this stub.
 */
@Service
@ConditionalOnMissingBean(ArmsClient.class)
public class StubArmsClient implements ArmsClient {
    @Override
    public ArmsUserInfo getUserByToken(String armsToken) {
        throw new UnsupportedOperationException("ARMS client not implemented");
    }

    @Override
    public List<ArmsEmployeeInfo> getAllUsers() {
        return List.of();
    }

    @Override
    public ArmsUserInfo getUserById(String userId) {
        throw new UnsupportedOperationException("ARMS client not implemented");
    }
}