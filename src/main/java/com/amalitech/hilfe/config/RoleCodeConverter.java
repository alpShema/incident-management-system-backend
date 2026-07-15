package com.amalitech.hilfe.config;

import com.amalitech.hilfe.models.RoleCode;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class RoleCodeConverter implements Converter<String, RoleCode> {

    @Override
    public RoleCode convert(String source) {
        try {
            return RoleCode.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "'" + source + "' is not a valid role. Please choose one of: CLIENT, AGENT, ADMIN, ADMIN_AGENT, SUPER_ADMIN.");
        }
    }
}
