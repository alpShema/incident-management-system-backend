package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.ArmsEmployeeInfo;
import com.amalitech.hilfe.dto.ArmsUserInfo;

import java.util.List;

public interface ArmsClient {
    ArmsUserInfo getUserByToken(String armsToken);

    List<ArmsEmployeeInfo> getAllUsers();

    ArmsUserInfo getUserById(String userId);
}
