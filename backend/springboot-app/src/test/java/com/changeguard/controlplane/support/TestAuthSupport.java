package com.changeguard.controlplane.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared helpers for authenticated MockMvc flows in integration tests.
 */
public final class TestAuthSupport {

    private TestAuthSupport() {
    }

    public static String register(MockMvc mockMvc, ObjectMapper mapper,
                                  String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"Test User","password":"%s"}"""
                                .formatted(email, password)))
                .andReturn();
        return tokenFrom(result, mapper);
    }

    public static String login(MockMvc mockMvc, ObjectMapper mapper,
                               String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}"""
                                .formatted(email, password)))
                .andReturn();
        return tokenFrom(result, mapper);
    }

    private static String tokenFrom(MvcResult result, ObjectMapper mapper) throws Exception {
        if (result.getResponse().getStatus() >= 300) {
            throw new IllegalStateException(
                    "Auth call failed status=" + result.getResponse().getStatus()
                            + " body=" + result.getResponse().getContentAsString());
        }
        JsonNode node = mapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
