package com.amalitech.hilfe.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger / OpenAPI configuration.
 *
 * <p>Exposes the API documentation at:
 * <ul>
 *   <li>Swagger UI: <a href="http://localhost:8080/swagger-ui.html">/swagger-ui.html</a></li>
 *   <li>OpenAPI JSON: <a href="http://localhost:8080/api-docs">/api-docs</a></li>
 * </ul>
 *
 * @author Amalitech Team
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * Defines the OpenAPI metadata and server configuration for the Swagger UI.
     *
     * @return a fully configured {@link OpenAPI} instance
     */
    @Bean
    public OpenAPI hilfeOpenAPI() {
        Server localServer = new Server()
                .url("http://localhost:" + serverPort)
                .description("Local Development Server");

        Contact contact = new Contact()
                .name("Amalitech Team")
                .email("lawson.buabassah@amalitechtraining.org");

        License license = new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT");

        Info info = new Info()
                .title("Hilfe API")
                .description("REST API documentation for the Hilfe application built by Amalitech.")
                .version("v1.0.0")
                .contact(contact)
                .license(license);

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer));
    }
}
