package com.conload.ui.projects;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Tree item model and filesystem child-building rules for the project pane. */
public final class ProjectTreeItems {
    private ProjectTreeItems() {}

    public static List<TreeItem<File>> codeChildren(File dir) {
        if (dir == null || !dir.isDirectory() || dir.listFiles() == null) return List.of();
        File[] files = dir.listFiles();
        Arrays.sort(files, ProjectTreeItems::compareFiles);
        return Arrays.stream(files).map(FileTreeItem::new).collect(Collectors.toList());
    }

    private static int compareFiles(File first, File second) {
        if (first.isDirectory() && !second.isDirectory()) return -1;
        if (!first.isDirectory() && second.isDirectory()) return 1;
        return first.getName().compareToIgnoreCase(second.getName());
    }

    public static final class ProjectRootTreeItem extends TreeItem<File> {
        private final String projectName;
        public ProjectRootTreeItem(File workDir, String projectName, TreeItem<File> code,
                                   TreeItem<File> context, TreeItem<File> sessions) {
            super(workDir);
            this.projectName = projectName == null || projectName.isBlank()
                    ? (workDir != null ? workDir.getName() : "Project") : projectName;
            getChildren().setAll(code, context, sessions);
        }
        public String getProjectName() { return projectName; }
        @Override public boolean isLeaf() { return false; }
    }

    public static class SectionHeaderTreeItem extends TreeItem<File> {
        private final String label;
        private final List<TreeItem<File>> sectionChildren;
        public SectionHeaderTreeItem(String label, List<TreeItem<File>> children) {
            super(null); this.label = label; this.sectionChildren = children;
            super.getChildren().setAll(FXCollections.observableArrayList(children));
        }
        public String getLabel() { return label; }
        public int getCount() { return sectionChildren.size(); }
        @Override public boolean isLeaf() { return false; }
    }

    public static final class ContextFolderTreeItem extends TreeItem<File> {
        private boolean loaded;
        private boolean leafChecked, leaf;
        public ContextFolderTreeItem(File folder) { super(folder); }
        @Override public ObservableList<TreeItem<File>> getChildren() {
            if (!loaded) { loaded = true; super.getChildren().setAll(buildContextChildren()); }
            return super.getChildren();
        }
        @Override public boolean isLeaf() {
            if (!leafChecked) { leafChecked = true; File folder = getValue();
                leaf = folder == null || !folder.isDirectory() || isEmptyDir(folder); }
            return leaf;
        }
        private List<TreeItem<File>> buildContextChildren() {
            File folder = getValue();
            if (folder == null || !folder.isDirectory() || folder.listFiles() == null) return List.of();
            List<File> rootMd = new java.util.ArrayList<>(), other = new java.util.ArrayList<>();
            File media = null, jira = null, github = null;
            for (File file : folder.listFiles()) {
                String name = file.getName();
                if (file.isDirectory()) {
                    if ("media".equalsIgnoreCase(name)) media = file;
                    else if ("jira".equalsIgnoreCase(name)) jira = file;
                    else if ("github".equalsIgnoreCase(name)) github = file;
                    else other.add(file);
                } else if (file.getName().toLowerCase().endsWith(".md")) rootMd.add(file);
            }
            if (rootMd.isEmpty() && media == null && jira == null && github == null)
                return sorted(Arrays.asList(folder.listFiles()));
            List<TreeItem<File>> result = new java.util.ArrayList<>(sorted(rootMd));
            result.addAll(sorted(other));
            if (media != null) result.addAll(children(media));
            if (jira != null) result.addAll(children(jira));
            if (github != null) result.addAll(children(github));
            return result;
        }
    }

    public static final class SourceGroupTreeItem extends TreeItem<File> {
        private final String label, iconResource;
        private final List<TreeItem<File>> sourceChildren;
        private boolean loaded;
        public SourceGroupTreeItem(String label, String iconResource, List<TreeItem<File>> children) {
            super(null); this.label = label; this.iconResource = iconResource; this.sourceChildren = children;
        }
        public String getLabel() { return label; }
        public String getIconResource() { return iconResource; }
        public int getCount() { return sourceChildren.size(); }
        @Override public ObservableList<TreeItem<File>> getChildren() {
            if (!loaded) { loaded = true; super.getChildren().setAll(FXCollections.observableArrayList(sourceChildren)); }
            return super.getChildren();
        }
        @Override public boolean isLeaf() { return sourceChildren.isEmpty(); }
    }

    public static class SessionLeafItem extends TreeItem<File> {
        public SessionLeafItem(File file) { super(file); }
        @Override public boolean isLeaf() { return true; }
        @Override public ObservableList<TreeItem<File>> getChildren() { return FXCollections.emptyObservableList(); }
    }

    public static class FileTreeItem extends TreeItem<File> {
        private boolean firstChildren = true, firstLeaf = true, leaf;
        public FileTreeItem(File file) { super(file); }
        @Override public ObservableList<TreeItem<File>> getChildren() {
            if (firstChildren) { firstChildren = false; super.getChildren().setAll(children(getValue())); }
            return super.getChildren();
        }
        @Override public boolean isLeaf() {
            if (firstLeaf) { firstLeaf = false; File f = getValue();
                leaf = f == null || f.isFile() || isEmptyDir(f); }
            return leaf;
        }
    }

    private static List<TreeItem<File>> children(File dir) {
        File[] files = dir.listFiles();
        return files == null ? List.of() : sorted(Arrays.asList(files));
    }
    private static List<TreeItem<File>> sorted(List<File> files) {
        return files.stream().sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .map(FileTreeItem::new).collect(Collectors.toList());
    }
    private static boolean isEmptyDir(File f) {
        if (!f.isDirectory()) return false;
        String[] names = f.list();
        return names == null || names.length == 0;
    }
}
