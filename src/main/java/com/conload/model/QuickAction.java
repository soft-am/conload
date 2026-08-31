package com.conload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A named reusable command sent to the terminal.
 * Commands may contain ${varName} placeholders — rendered as input fields in the UI.
 * If parameters list is non-empty, a popup is shown to fill them before sending.
 * Persisted to src/quick-actions.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuickAction {

    /** Matches a ${varName} placeholder in a command. varName is alphanumeric/underscore. */
    private static final Pattern VAR = Pattern.compile("\\$\\{(\\w+)}");

    private String id;
    private String name;
    private String command;
    /** Named parameters — each corresponds to a ${paramName} placeholder in command. */
    private List<String> parameters = new ArrayList<>();
    /** Flag to identify if this is a prompt template (larger text, multi-line editor). */
    private boolean template = false;

    public QuickAction() {}

    public QuickAction(String id, String name, String command) {
        this.id = id; this.name = name; this.command = command;
    }

    public QuickAction(String id, String name, String command, List<String> parameters) {
        this.id = id; this.name = name; this.command = command;
        this.parameters = parameters != null ? parameters : new ArrayList<>();
    }

    public String getId()              { return id; }
    public String getName()            { return name; }
    public String getCommand()         { return command; }
    public List<String> getParameters(){ return parameters; }
    public boolean isTemplate()        { return template; }

    public void setId(String id)                    { this.id = id; }
    public void setName(String name)                { this.name = name; }
    public void setCommand(String command)          { this.command = command; }
    public void setParameters(List<String> params)  { this.parameters = params != null ? params : new ArrayList<>(); }
    public void setTemplate(boolean template)       { this.template = template; }

    // ── Placeholder helpers ──────────────────────────────────────────────────

    /** Extracts the ordered, unique ${varName} placeholders found in {@code command}. */
    public static List<String> extractParameters(String command) {
        List<String> out = new ArrayList<>();
        if (command == null || command.isBlank()) return out;
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = VAR.matcher(command);
        while (m.find()) {
            String v = m.group(1);
            if (seen.add(v)) out.add(v);
        }
        return out;
    }

    /** True when {@code command} contains at least one ${varName} placeholder. */
    public static boolean hasInput(String command) {
        return command != null && !command.isBlank() && VAR.matcher(command).find();
    }

    /** True when this action requires user input (command has ${varName} placeholders). */
    public boolean hasInput() {
        return hasInput(command);
    }

    /**
     * Replaces every ${varName} in {@code command} with the matching value from
     * {@code values}. Placeholders without a provided value are left untouched.
     */
    public static String substitute(String command, Map<String, String> values) {
        if (command == null || command.isBlank() || values == null || values.isEmpty()) {
            return command;
        }
        Matcher m = VAR.matcher(command);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String repl = values.containsKey(key) ? values.get(key) : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
