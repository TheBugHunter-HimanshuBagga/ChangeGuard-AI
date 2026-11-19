package com.changeguard.controlplane.organization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.changeguard.controlplane.AbstractIntegrationTest;
import com.changeguard.controlplane.support.TestAuthSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class OrganizationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerUser() throws Exception {
        String email = "org-user-" + UUID.randomUUID() + "@example.com";
        TestAuthSupport.register(mockMvc, objectMapper, email, "password123");
        return TestAuthSupport.login(mockMvc, objectMapper, email, "password123");
    }

    private String createOrg(String token, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Org %s","slug":"%s"}""".formatted(slug, slug)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void creatorBecomesOwnerAndOrgAppearsInList() throws Exception {
        String token = registerUser();
        String slug = "acme-" + UUID.randomUUID().toString().substring(0, 8);

        String orgId = createOrg(token, slug);

        mockMvc.perform(get("/api/v1/organizations/" + orgId)
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug));

        mockMvc.perform(get("/api/v1/organizations")
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + orgId + "')]").exists());

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/members")
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void duplicateSlugReturnsConflict() throws Exception {
        String token = registerUser();
        String slug = "dup-" + UUID.randomUUID().toString().substring(0, 8);
        createOrg(token, slug);

        mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Other","slug":"%s"}""".formatted(slug)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void nonMemberCannotSeeOrganization() throws Exception {
        String ownerToken = registerUser();
        String strangerToken = registerUser();
        String slug = "sec-" + UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(ownerToken, slug);

        mockMvc.perform(get("/api/v1/organizations/" + orgId)
                        .header("Authorization", TestAuthSupport.bearer(strangerToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void memberCanBeAddedListedAndRemoved() throws Exception {
        String ownerToken = registerUser();
        String memberEmail = "member-" + UUID.randomUUID() + "@example.com";
        TestAuthSupport.register(mockMvc, objectMapper, memberEmail, "password123");
        String slug = "team-" + UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(ownerToken, slug);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                        .header("Authorization", TestAuthSupport.bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","role":"MEMBER"}""".formatted(memberEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.email").value(memberEmail));

        // Duplicate member → 409
        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                        .header("Authorization", TestAuthSupport.bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","role":"MEMBER"}""".formatted(memberEmail)))
                .andExpect(status().isConflict());

        // Member can list members
        String memberToken = TestAuthSupport.login(mockMvc, objectMapper, memberEmail, "password123");
        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/members")
                        .header("Authorization", TestAuthSupport.bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // MEMBER role cannot add others (403)
        String otherEmail = "other-" + UUID.randomUUID() + "@example.com";
        TestAuthSupport.register(mockMvc, objectMapper, otherEmail, "password123");
        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                        .header("Authorization", TestAuthSupport.bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","role":"VIEWER"}""".formatted(otherEmail)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Remove the member
        JsonNode members = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/organizations/" + orgId + "/members")
                                .header("Authorization", TestAuthSupport.bearer(ownerToken)))
                .andReturn().getResponse().getContentAsString());
        String memberId = null;
        for (JsonNode m : members) {
            if (m.get("email").asText().equals(memberEmail)) {
                memberId = m.get("userId").asText();
            }
        }
        org.assertj.core.api.Assertions.assertThat(memberId).isNotNull();

        mockMvc.perform(delete("/api/v1/organizations/" + orgId + "/members/" + memberId)
                        .header("Authorization", TestAuthSupport.bearer(ownerToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void lastOwnerCannotBeRemoved() throws Exception {
        String ownerToken = registerUser();
        String slug = "own-" + UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(ownerToken, slug);

        MvcResult me = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", TestAuthSupport.bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();
        String ownerId = objectMapper.readTree(me.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/v1/organizations/" + orgId + "/members/" + ownerId)
                        .header("Authorization", TestAuthSupport.bearer(ownerToken)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LAST_OWNER_REMOVAL"));
    }
}
