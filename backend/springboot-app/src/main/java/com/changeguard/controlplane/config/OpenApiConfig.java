package com.changeguard.controlplane.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata and JWT bearer scheme for Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI changeGuardOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ChangeGuard AI — Control Plane API")
                        .description("""
                                Control plane REST API for ChangeGuard AI.
                                All endpoints are versioned under /api/v1.
                                Errors use the canonical envelope from docs/API_DESIGN.md.
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the JWT obtained from POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
