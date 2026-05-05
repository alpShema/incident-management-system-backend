package com.amalitech.hilfe.mappers;

import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.models.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArmsUserMapperTest {

    @Test
    void mapArmsUserToImsUser_validInput_mapsAllFieldsCorrectly() {
        ArmsUserInfo armsUser = new ArmsUserInfo(
                "10042",
                "Alphonse",
                "Shema",
                "alphonse@amalitech.org",
                "https://example.com/photo.jpg"
        );

        User result = ArmsUserMapper.mapArmsUserToImsUser(armsUser);

        assertThat(result.getId()).isEqualTo("10042");
        assertThat(result.getFullName()).isEqualTo("Alphonse Shema");
        assertThat(result.getEmail()).isEqualTo("alphonse@amalitech.org");
        assertThat(result.getProfileImg()).isEqualTo("https://example.com/photo.jpg");
        assertThat(result.getStatus()).isTrue();
        assertThat(result.getPermissions()).isEmpty();
    }

    @Test
    void mapArmsUserToImsUser_nullProfileImage_doesNotThrow() {
        ArmsUserInfo armsUser = new ArmsUserInfo(
                "10042",
                "Alphonse",
                "Shema",
                "alphonse@amalitech.org",
                null
        );

        User result = ArmsUserMapper.mapArmsUserToImsUser(armsUser);

        assertThat(result.getProfileImg()).isNull();
        assertThat(result.getId()).isEqualTo("10042");
        assertThat(result.getStatus()).isTrue();
    }
}