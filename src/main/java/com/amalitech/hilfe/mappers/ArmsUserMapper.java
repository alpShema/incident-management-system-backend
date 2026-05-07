package com.amalitech.hilfe.mappers;

import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.models.User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class ArmsUserMapper {

    private ArmsUserMapper() {
    }

    public static User mapArmsUserToImsUser(ArmsUserInfo armsUser) {
        return User.builder()
                .id(armsUser.userId())
                .fullName(armsUser.firstName() + " " + armsUser.lastName())
                .email(armsUser.email())
                .profileImg(armsUser.profileImage())
                .status(true)
                .permissions(new ArrayList<>())
                .build();
    }
}
