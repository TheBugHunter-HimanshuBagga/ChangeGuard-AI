package com.changeguard.controlplane.repository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.changeguard.controlplane.AbstractIntegrationTest;
import com.changeguard.controlplane.support.TestAuthSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class RepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerUser() throws Exception {
        String email = "repo-user-" + UUID.randomUUID() + "@example.com";
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
    void linkListAndDetailFlow() throws Exception {
        String token = registerUser();
        String slug = "link-" + UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(token, slug);
        long githubId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);

        MvcResult created = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/repositories")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"githubRepoId":%d,"fullName":"acme/service-%s","defaultBranch":"main","languagesEnabled":["JAVA","PYTHON"]}"""
                                .formatted(githubId, slug)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("acme/service-" + slug))
                .andExpect(jsonPath("$.languagesEnabled.length()").value(2))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.webhookSecretSet").value(false))
                .andReturn();
        String repoId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/repositories")
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/repositories/" + repoId)
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(repoId))
                .andExpect(jsonPath("$.githubRepoId").value(githubId));
    }

    @Test
    void duplicateGithubRepoIdReturnsConflict() throws Exception {
        String token = registerUser();
        String slug = "dup-" + UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(token, slug);
        long githubId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
        String body = """
                {"githubRepoId":%d,"fullName":"acme/dup","languagesEnabled":["JAVA"]}"""
                .formatted(githubId);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/repositories")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/repositories")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void invalidLanguageRejected() throws Exception {
        String token = registerUser();
        String slug = "lang-" + UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(token, slug);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/repositories")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"githubRepoId":12345,"fullName":"acme/x","languagesEnabled":["RUST"]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void nonMemberCannotLinkOrView() throws Exception {
        String ownerToken = registerUser();
        String strangerToken = registerUser();
        String slug = "sec-" + UUID.randomUUID().toString().substring(0, 8);
        String orgId = createOrg(ownerToken, slug);
        long githubId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/repositories")
                        .header("Authorization", TestAuthSupport.bearer(strangerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"githubRepoId":%d,"fullName":"acme/s","languagesEnabled":["JAVA"]}"""
                                .formatted(githubId)))
                .andExpect(status().isNotFound());

        MvcResult created = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/repositories")
                        .header("Authorization", TestAuthSupport.bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"githubRepoId":%d,"fullName":"acme/s","languagesEnabled":["JAVA"]}"""
                                .formatted(githubId)))
                .andExpect(status().isCreated())
                .andReturn();
        String repoId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/repositories/" + repoId)
                        .header("Authorization", TestAuthSupport.bearer(strangerToken)))
                .andExpect(status().isNotFound());
    }
}
