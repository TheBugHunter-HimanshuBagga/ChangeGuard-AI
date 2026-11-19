package com.changeguard.controlplane.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.changeguard.controlplane.AbstractIntegrationTest;
import com.changeguard.controlplane.support.TestAuthSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginAndMeFlow() throws Exception {
        String email = "alice-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"Alice","password":"password123"}"""
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-API-Version", "v1"))
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInMinutes").value(15))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("MEMBER"));

        String token = TestAuthSupport.login(mockMvc, objectMapper, email, "password123");
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Alice"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void duplicateRegistrationReturnsConflictEnvelope() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"email":"%s","displayName":"Dup","password":"password123"}""".formatted(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.error.timestamp").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordReturnsInvalidCredentials() throws Exception {
        String email = "bob-" + UUID.randomUUID() + "@example.com";
        TestAuthSupport.register(mockMvc, objectMapper, email, "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-password"}""".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void meWithoutTokenReturnsUnauthenticatedEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.error.correlationId").isNotEmpty());
    }

    @Test
    void validationFailureReturnsFieldDetails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","displayName":"","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.error.details[?(@.field == 'password')]").exists());
    }

    @Test
    void inboundCorrelationIdIsEchoed() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-Correlation-ID", correlationId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", correlationId))
                .andReturn();
        org.assertj.core.api.Assertions
                .assertThat(result.getResponse().getContentAsString())
                .contains(correlationId);
    }
}
