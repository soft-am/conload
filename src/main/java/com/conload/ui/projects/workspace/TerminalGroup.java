package com.conload.ui.projects.workspace;

import com.conload.ui.terminal.CopilotTerminalPane;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Holds N terminals for one workspace key (projectId[\u0001]worktreePath).
 *  Tracks which sub-terminal is active so the subtab strip and terminalHost
 *  know which one to display. */
public final class TerminalGroup {
    private final ObservableList<CopilotTerminalPane> terminals = FXCollections.observableArrayList();
    private final SimpleIntegerProperty activeIndex = new SimpleIntegerProperty(0);

    public ObservableList<CopilotTerminalPane> terminals() { return terminals; }
    public SimpleIntegerProperty activeIndexProperty() { return activeIndex; }
    public int activeIndex() { return Math.min(activeIndex.get(), terminals.size() - 1); }

    /** The currently active terminal, or null if the group is empty. */
    public CopilotTerminalPane active() {
        if (terminals.isEmpty()) return null;
        int i = Math.min(activeIndex.get(), terminals.size() - 1);
        return terminals.get(i);
    }

    /** Adds a terminal to the group and makes it the active one. */
    public void add(CopilotTerminalPane terminal) {
        terminals.add(terminal);
        activeIndex.set(terminals.size() - 1);
    }

    /** Removes the terminal at the given index. Adjusts activeIndex to keep
     *  a valid terminal selected (or 0 if the group becomes empty). */
    public void remove(int index) {
        if (index < 0 || index >= terminals.size()) return;
        terminals.remove(index);
        if (activeIndex.get() >= terminals.size()) activeIndex.set(Math.max(0, terminals.size() - 1));
    }

    /** Sets the active terminal by index (clamped to valid range). */
    public void setActive(int index) {
        if (index >= 0 && index < terminals.size()) activeIndex.set(index);
    }

    public int size() { return terminals.size(); }
}
