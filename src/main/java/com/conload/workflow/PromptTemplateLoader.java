package com.conload.workflow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads a prompt template from the classpath resources ({@code /defaults/workflows/}).
 * Templates are bundled in the JAR and not exposed to the user — only the
 * substituted result is shown in the dialog.
 */
public final class PromptTemplateLoader {

    private PromptTemplateLoader() {}

    /**
     * Load the template text for the given resource path (e.g.
     * {@code /defaults/workflows/prepare-to-refinement.md.tpl}).
     *
     * @return the template text (never null; empty string on failure)
     */
    public static String load(String resourcePath) {
        try (InputStream is = PromptTemplateLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return "[ERROR] Template not found: " + resourcePath;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "[ERROR] Failed to load template " + resourcePath + ": " + e.getMessage();
        }
    }
}
