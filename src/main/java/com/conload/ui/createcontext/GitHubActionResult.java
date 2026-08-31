package com.conload.ui.createcontext;

import com.conload.github.GitHubClient;
import com.conload.util.Json;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * UI model for a single GitHub Actions run fetched by URL.
 * Mirrors {@link GitHubPrResult}: a checkboxable table row backing
 * a {@code TableView<GitHubActionResult>}. The raw run metadata and
 * the downloaded job logs are carried for the download step.
 */
public class GitHubActionResult {

    private final BooleanProperty selected;
    private final long   runId;
    private final long   jobId;
    private final String workflowName;
    private final String runName;
    private final String jobName;
    private final String status;
    private final String conclusion;
    private final String htmlUrl;
    private final String rawJson;
    private final String jobLogs;
    /** JSON-serialised array of steps: [{"number","name","status","conclusion"}, …]. */
    private final String stepsJson;

    public GitHubActionResult(boolean selected, long runId, long jobId,
                              String workflowName, String runName, String jobName,
                              String status, String conclusion, String htmlUrl,
                              String rawJson, String jobLogs, String stepsJson) {
        this.selected     = new SimpleBooleanProperty(selected);
        this.runId        = runId;
        this.jobId        = jobId;
        this.workflowName = workflowName;
        this.runName      = runName;
        this.jobName      = jobName;
        this.status       = status;
        this.conclusion   = conclusion;
        this.htmlUrl      = htmlUrl;
        this.rawJson      = rawJson;
        this.jobLogs      = jobLogs;
        this.stepsJson    = stepsJson != null ? stepsJson : "[]";
    }

    public static GitHubActionResult from(GitHubClient.GitActionRun run) {
        String sj;
        try {
            sj = Json.MAPPER.writeValueAsString(run.steps());
        } catch (Exception e) {
            sj = "[]";
        }
        return new GitHubActionResult(
            true,
            run.runId(), run.jobId(), run.workflowName(), run.runName(),
            run.jobName(), run.status(), run.conclusion(), run.htmlUrl(),
            run.rawJson(), run.jobLogs(), sj
        );
    }

    // JavaFX property (needed by PropertyValueFactory)
    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected()  { return selected.get(); }
    public void    setSelected(boolean v) { selected.set(v); }

    // Bean getters for PropertyValueFactory
    public long   getRunId()        { return runId; }
    public long   getJobId()        { return jobId; }
    public String getWorkflowName() { return workflowName; }
    public String getRunName()      { return runName; }
    public String getJobName()      { return jobName; }
    public String getStatus()       { return status; }
    public String getConclusion()   { return conclusion; }
    public String getHtmlUrl()      { return htmlUrl; }
    public String getRawJson()      { return rawJson; }
    public String getJobLogs()      { return jobLogs; }
    public String getStepsJson()    { return stepsJson; }
}
