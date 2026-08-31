package com.conload.ui.createcontext.model;

/**
 * Marker for the download destination choice: a folder on disk where the
 * downloaded content will be placed inside a new subfolder named after the
 * user-supplied context name (filesystem-sanitised).
 *
 * <p>The former {@code ExtendContext} variant (which referenced the removed
 * {@code ProjectContext} registry) has been deleted — a "context" is now
 * simply a folder.
 */
public sealed interface DownloadTarget permits DownloadTarget.NewContext {
    record NewContext(String folderPath, String contextName) implements DownloadTarget {}
}
