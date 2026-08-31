package com.conload.ui.createcontext;

import com.conload.github.GitHubClient;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class GitHubPrResult {

    private final BooleanProperty selected;
    private final int    number;
    private final String title;
    private final String author;
    private final String state;
    private final String url;
    private final String rawJson;
    private final String diffContent;

    public GitHubPrResult(boolean selected, int number, String title, String author,
                          String state, String url,
                          String rawJson, String diffContent) {
        this.selected  = new SimpleBooleanProperty(selected);
        this.number    = number;
        this.title     = title;
        this.author    = author;
        this.state     = state;
        this.url       = url;
        this.rawJson   = rawJson;
        this.diffContent = diffContent;
    }

    public static GitHubPrResult from(GitHubClient.GitPullRequest pr) {
        return new GitHubPrResult(
            false,
            pr.number(), pr.title(), pr.author(), pr.state(),
            pr.htmlUrl(),
            pr.rawJson(), pr.diffContent()
        );
    }

    // JavaFX property (needed by CheckBoxTableCell / PropertyValueFactory)
    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected()  { return selected.get(); }
    public void    setSelected(boolean v) { selected.set(v); }

    // Bean getters for PropertyValueFactory
    public int    getNumber()    { return number; }
    public String getTitle()     { return title; }
    public String getAuthor()    { return author; }
    public String getState()     { return state; }
    public String getUrl()       { return url; }
    public String getRawJson()     { return rawJson; }
    public String getDiffContent() { return diffContent; }
}
