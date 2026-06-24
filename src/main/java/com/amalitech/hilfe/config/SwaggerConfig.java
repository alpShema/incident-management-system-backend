package com.amalitech.hilfe.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String COOKIE_SCHEME = "cookieAuth";

    @Bean
    public GroupedOpenApi restApiGroup() {
        return GroupedOpenApi.builder()
                .group("rest-api")
                .displayName("REST API")
                .pathsToExclude("/graphql")
                .build();
    }

    @Bean
    public GroupedOpenApi graphQlApiGroup() {
        return GroupedOpenApi.builder()
                .group("graphql-api")
                .displayName("GraphQL API")
                .pathsToMatch("/graphql")
                .addOpenApiCustomizer(new GraphQlOpenApiConfig())
                .build();
    }

    @Bean
    public OpenAPI hilfeOpenAPI() {
        Server localServer = new Server()
                .url("/")
                .description("Default Server");

        Contact contact = new Contact()
                .name("Amalitech Team")
                .email("lawson.buabassah@amalitechtraining.org");

        License license = new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT");

        Info info = new Info()
                .title("Hilfe API")
                .description("""
                        REST API documentation for the Hilfe application built by Amalitech.

                        **Authentication**

                        This API uses HttpOnly cookies. To authenticate in Swagger UI:
                        1. Call `POST /auth/login` with your ARMS token — this sets the `access_token` cookie in your browser.
                        2. Swagger UI will automatically send the cookie on subsequent requests (same-origin).

                        If Swagger UI does not pick up the cookie automatically, use the **Authorize** button above \
                        and paste the raw JWT value from your `access_token` cookie.""")
                .version("v1.0.0")
                .contact(contact)
                .license(license);

        SecurityScheme cookieScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name("access_token")
                .description("Paste the value of your `access_token` cookie (the raw JWT, without quotes).");

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer))
                .components(new Components().addSecuritySchemes(COOKIE_SCHEME, cookieScheme))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_SCHEME));
    }
}
