package com.amalitech.hilfe.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RoleCodeConverter roleCodeConverter;

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(roleCodeConverter);
    }
}
