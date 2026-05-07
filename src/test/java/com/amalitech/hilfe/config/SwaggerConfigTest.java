package com.amalitech.hilfe.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

    @Test
    void hilfeOpenAPI_returnsConfiguredInstance() {
        SwaggerConfig config = new SwaggerConfig();
        ReflectionTestUtils.setField(config, "serverPort", "8080");

        OpenAPI api = config.hilfeOpenAPI();

        assertThat(api).isNotNull();
        assertThat(api.getInfo()).isNotNull();
        assertThat(api.getInfo().getTitle()).isEqualTo("Hilfe API");
        assertThat(api.getInfo().getVersion()).isEqualTo("v1.0.0");
        assertThat(api.getInfo().getContact().getEmail())
                .isEqualTo("lawson.buabassah@amalitechtraining.org");
        assertThat(api.getServers()).hasSize(1);
        assertThat(api.getServers().get(0).getUrl()).isEqualTo("http://localhost:8080");
    }
}