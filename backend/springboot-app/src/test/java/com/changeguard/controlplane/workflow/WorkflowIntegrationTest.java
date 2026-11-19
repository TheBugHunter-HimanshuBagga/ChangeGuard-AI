package com.changeguard.controlplane.workflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.changeguard.controlplane.AbstractIntegrationTest;
import com.changeguard.controlplane.support.TestAuthSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class WorkflowIntegrationTest extends AbstractIntegrationTest {

    private static final String HEAD_SHA = "a".repeat(40);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerUser() throws Exception {
        String email = "wf-user-" + UUID.randomUUID() + "@example.com";
        TestAuthSupport.register(mockMvc, objectMapper, email, "password123");
        return TestAuthSupport.login(mockMvc, objectMapper, email, "password123");
    }

    private String setupRepo(String token) throws Exception {
        String slug = "wf-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult org = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Org %s","slug":"%s"}""".formatted(slug, slug)))
                .andExpect(status().isCreated())
                .andReturn();
        String orgId = objectMapper.readTree(org.getResponse().getContentAsString()).get("id").asText();
        long githubId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
        MvcResult repo = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/repositories")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"githubRepoId":%d,"fullName":"acme/%s","defaultBranch":"main","languagesEnabled":["JAVA"]}"""
                                .formatted(githubId, slug)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(repo.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createDetailSearchAndCancelFlow() throws Exception {
        String token = registerUser();
        String repoId = setupRepo(token);

        MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryId":"%s","pullRequestNumber":7,"eventType":"pull_request.synchronize","commitSha":"%s"}"""
                                .formatted(repoId, HEAD_SHA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.commitSha").value(HEAD_SHA))
                .andExpect(jsonPath("$.steps").isArray())
                .andReturn();
        JsonNode workflow = objectMapper.readTree(created.getResponse().getContentAsString());
        String workflowId = workflow.get("id").asText();

        mockMvc.perform(get("/api/v1/workflows/" + workflowId)
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workflowId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/v1/workflows")
                        .param("repositoryId", repoId)
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + workflowId + "')]").exists());

        mockMvc.perform(get("/api/v1/workflows")
                        .param("commitSha", HEAD_SHA)
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + workflowId + "')]").exists());

        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/cancel")
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Cancel twice → 409 (terminal state)
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/cancel")
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void createIsIdempotentForSameRepoPrShaEvent() throws Exception {
        String token = registerUser();
        String repoId = setupRepo(token);
        String body = """
                {"repositoryId":"%s","pullRequestNumber":11,"eventType":"pull_request.opened","commitSha":"%s"}"""
                .formatted(repoId, "b".repeat(40));

        MvcResult first = mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        MvcResult second = mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void invalidCommitShaRejected() throws Exception {
        String token = registerUser();
        String repoId = setupRepo(token);

        mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryId":"%s","eventType":"push","commitSha":"not-a-sha"}"""
                                .formatted(repoId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void nonMemberCannotCreateWorkflow() throws Exception {
        String ownerToken = registerUser();
        String strangerToken = registerUser();
        String repoId = setupRepo(ownerToken);

        mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", TestAuthSupport.bearer(strangerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryId":"%s","eventType":"push","commitSha":"%s"}"""
                                .formatted(repoId, "c".repeat(40))))
                .andExpect(status().isNotFound());
    }

    @Test
    void jobLifecycleAgainstWorkflow() throws Exception {
        String token = registerUser();
        String repoId = setupRepo(token);

        MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryId":"%s","pullRequestNumber":3,"eventType":"pull_request.synchronize","commitSha":"%s"}"""
                                .formatted(repoId, "d".repeat(40))))
                .andExpect(status().isCreated())
                .andReturn();
        String workflowId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        MvcResult job = mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/jobs")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"JAVA"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.language").value("JAVA"))
                .andExpect(jsonPath("$.commitSha").value("d".repeat(40)))
                .andReturn();
        String jobId = objectMapper.readTree(job.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/jobs/" + jobId)
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId));

        mockMvc.perform(get("/api/v1/workflows/" + workflowId + "/jobs")
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Cancel workflow → terminal; further job creation rejected
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/cancel")
                        .header("Authorization", TestAuthSupport.bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/jobs")
                        .header("Authorization", TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"PYTHON"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }
}
