package com.conload.github.actions;

import com.conload.github.GitHubClient;
import com.conload.util.Json;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Retrieves workflow runs, jobs, steps, and logs using GitHubClient's transport. */
public final class GitHubActionsClient {

    public interface Transport {
        String getJson(String url) throws IOException, InterruptedException;

        String fetchJobLogs(String url) throws IOException, InterruptedException;
    }

    private static final String DEFAULT_BASE = "https://api.github.com";
    private final String apiBase;
    private final Transport transport;
    private final Logger log = Logger.getLogger(getClass().getName());

    public GitHubActionsClient(Transport transport) {
        this(DEFAULT_BASE, transport);
    }

    public GitHubActionsClient(String apiBase, Transport transport) {
        this.apiBase = (apiBase == null || apiBase.isBlank()) ? DEFAULT_BASE
                : apiBase.strip().replaceAll("/+$", "");
        this.transport = transport;
    }

    public GitHubClient.GitActionRun getActionRun(String owner, String repo, long runId, Long jobId)
            throws IOException, InterruptedException {
        String runUrl = apiBase + "/repos/" + owner + "/" + repo + "/actions/runs/" + runId;
        String rawJson = transport.getJson(runUrl);
        JsonNode runRoot = Json.MAPPER.readTree(rawJson);
        String workflowName = runRoot.path("name").asText("");
        String runName = runRoot.path("display_title").asText(runRoot.path("name").asText(""));
        String status = runRoot.path("status").asText("");
        String conclusion = runRoot.path("conclusion").asText("");
        String htmlUrl = runRoot.path("html_url").asText("");

        StringBuilder logs = new StringBuilder();
        String jobName = "";
        long resolvedJobId = jobId != null ? jobId : 0L;
        List<GitHubClient.GitActionStep> steps = new ArrayList<>();

        if (jobId != null) {
            JsonNode jobJson = fetchJobJson(owner, repo, jobId);
            if (jobJson != null) {
                jobName = jobJson.path("name").asText("");
                steps = parseSteps(jobJson.path("steps"));
            }
            appendJobLog(logs, fetchJobLogs(owner, repo, jobId));
        } else {
            for (Long jid : listRunJobIds(owner, repo, runId)) {
                JsonNode jobJson = fetchJobJson(owner, repo, jid);
                String jn = "";
                if (jobJson != null) {
                    jn = jobJson.path("name").asText("");
                    steps.addAll(parseSteps(jobJson.path("steps")));
                }
                String jl = fetchJobLogs(owner, repo, jid);
                if (jl != null && !jl.isBlank()) {
                    logs.append("===== JOB ").append(jid)
                        .append(jn.isBlank() ? "" : " — " + jn)
                        .append(" =====\n").append(jl).append("\n");
                }
            }
        }
        return new GitHubClient.GitActionRun(runId, resolvedJobId, workflowName, runName,
                jobName, status, conclusion, htmlUrl, rawJson, logs.toString(), steps);
    }

    private void appendJobLog(StringBuilder logs, String jobLog) {
        if (jobLog != null && !jobLog.isBlank()) logs.append(jobLog);
    }

    private List<GitHubClient.GitActionStep> parseSteps(JsonNode stepsNode) {
        List<GitHubClient.GitActionStep> out = new ArrayList<>();
        if (stepsNode != null && stepsNode.isArray()) {
            for (JsonNode s : stepsNode) {
                out.add(new GitHubClient.GitActionStep(
                    s.path("number").asInt(0), s.path("name").asText(""),
                    s.path("status").asText(""), s.path("conclusion").asText("")));
            }
        }
        return out;
    }

    private JsonNode fetchJobJson(String owner, String repo, long jobId) {
        try {
            return Json.MAPPER.readTree(transport.getJson(
                apiBase + "/repos/" + owner + "/" + repo + "/actions/jobs/" + jobId));
        } catch (Exception e) {
            return null;
        }
    }

    private String fetchJobLogs(String owner, String repo, long jobId) {
        String url = apiBase + "/repos/" + owner + "/" + repo + "/actions/jobs/" + jobId + "/logs";
        try {
            return transport.fetchJobLogs(url);
        } catch (Exception e) {
            log.warning("Failed to fetch job logs for " + jobId + ": " + e.getMessage());
            return "";
        }
    }

    private List<Long> listRunJobIds(String owner, String repo, long runId) {
        List<Long> ids = new ArrayList<>();
        try {
            JsonNode arr = Json.MAPPER.readTree(transport.getJson(
                apiBase + "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/jobs")).path("jobs");
            if (arr.isArray()) {
                for (JsonNode j : arr) {
                    long id = j.path("id").asLong(0L);
                    if (id != 0L) ids.add(id);
                }
            }
        } catch (Exception e) {
            log.warning("Failed to list jobs for run " + runId + ": " + e.getMessage());
        }
        return ids;
    }
}
