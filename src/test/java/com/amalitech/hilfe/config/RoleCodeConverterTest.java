package com.amalitech.hilfe.config;

import com.amalitech.hilfe.models.RoleCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleCodeConverterTest {

    private final RoleCodeConverter converter = new RoleCodeConverter();

    @Test
    void convert_validCode_returnsRoleCode() {
        assertThat(converter.convert("CLIENT")).isEqualTo(RoleCode.CLIENT);
        assertThat(converter.convert("AGENT")).isEqualTo(RoleCode.AGENT);
        assertThat(converter.convert("ADMIN")).isEqualTo(RoleCode.ADMIN);
        assertThat(converter.convert("ADMIN_AGENT")).isEqualTo(RoleCode.ADMIN_AGENT);
    }

    @Test
    void convert_lowercaseCode_returnsRoleCodeAfterUppercase() {
        assertThat(converter.convert("client")).isEqualTo(RoleCode.CLIENT);
        assertThat(converter.convert("admin")).isEqualTo(RoleCode.ADMIN);
    }

    @Test
    void convert_invalidCode_throwsIllegalArgumentExceptionWithMessage() {
        assertThatThrownBy(() -> converter.convert("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid role code: 'INVALID'");
    }
}
