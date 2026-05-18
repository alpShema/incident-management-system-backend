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
                .fullName(buildFullName(armsUser))
                .email(armsUser.email())
                .contact(armsUser.phoneNumber())
                .profileImg(armsUser.profileImage())
                .position(armsUser.positionName())
                .status(true)
                .permissions(new ArrayList<>())
                .build();
    }

    public static String buildFullName(ArmsUserInfo armsUser) {
        return String.join(" ",
                armsUser.firstName() == null ? "" : armsUser.firstName().trim(),
                armsUser.otherName() == null ? "" : armsUser.otherName().trim(),
                armsUser.lastName() == null ? "" : armsUser.lastName().trim()
        ).replaceAll("\\s+", " ").trim();
    }
}
