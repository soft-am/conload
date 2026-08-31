package com.conload.ui.components;

import com.conload.ui.Icons;
import com.conload.ui.Theme;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class AboutPanel {

    private AboutPanel() {
    }

    public static ScrollPane build() {
        VBox root = new VBox(0);
        Theme.classes(root, Theme.CL_BG_APP);
        root.getChildren().addAll(buildAboutHero(), buildAboutContent(), buildAboutFooter());
        return UiFactory.scrollable(root);
    }

    private static VBox buildAboutHero() {
        VBox hero = new VBox(4);
        hero.setPadding(new Insets(16, 40, 12, 40));
        hero.getStyleClass().add("panel-border-bottom");

        Label heroIcon = new Label(Icons.HEXAGON);
        heroIcon.getStyleClass().addAll("title");
        heroIcon.getStyleClass().add("icon");
        Label heroTitle = new Label("CONLOAD ");
        heroTitle.getStyleClass().addAll("title");
        Label heroVersion = new Label("Version 1.0");
        heroVersion.getStyleClass().addAll("hint");

        Label summary = new Label(
                "Lädt Confluence-Seiten und Jira-Tickets als Markdown-Dateien herunter und stellt sie als "
                        + "strukturierten lokalen Knowledge Workspace bereit — optimiert für GitHub Copilot, "
                        + "AI-gestützte Suche und kontextbasierte Assistenz in der IDE.\n\n"
                        + "Confluence · Jira · GitHub Commits · Lokale Markdown-Suche · Eingebettetes Copilot-Terminal");
        summary.setWrapText(true);
        summary.getStyleClass().addAll("secondary", "small");

        hero.getChildren().addAll(heroIcon, heroTitle, heroVersion,
                new Separator(), summary);
        return hero;
    }

    private static VBox buildAboutContent() {
        VBox content = new VBox(0);
        content.setPadding(new Insets(16, 40, 20, 40));
        Theme.classes(content, Theme.CL_BG_APP);
        content.setSpacing(0);

        content.getChildren().add(sectionHeader("ANWENDUNGSFÄLLE"));
        content.getChildren().add(useCaseCard(
                "Entwickler",
                new String[]{
                        "Schnellere Einarbeitung in neue Projekte und Codebasen",
                        "Reverse Engineering von Legacy-Systemen durch strukturierte Kontextbereitstellung",
                        "Übergreifende Suche in Code, Confluence-Dokumentation und Jira-Tickets"
                }
        ));

        content.getChildren().add(useCaseCard(
                "Business Analysten / Requirement Engineers",
                new String[]{
                        "Schnellere Anforderungs- und Impact-Analyse dank zentralem Wissenszugriff",
                        "Einfachere Navigation durch umfangreiche Projektdokumentation",
                        "Verbindung zwischen Jira-Anforderungen, Confluence-Dokumentation und Implementierung verstehen"
                }
        ));

        content.getChildren().add(useCaseCard(
                "Manager / Team Leads",
                new String[]{
                        "Zentraler Zugriff auf Projektwissen in einer einzigen Desktop-Anwendung",
                        "Schnellere Projektübersicht und strukturiertes Onboarding neuer Mitarbeiter",
                        "Bessere Transparenz über Architektur, Entscheidungen und Dokumentationsstand"
                }
        ));

        content.getChildren().add(sectionHeader("VORTEILE"));
        content.getChildren().add(useCaseCard(
                "Sicherheit & Compliance",
                new String[]{
                        "Einheitlicher lokaler Knowledge Workspace — keine Daten verlassen das Unternehmen",
                        "Ausschließliche Nutzung freigegebener GitHub Copilot Tools und Unternehmens-Infrastruktur",
                        "Keine direkte Anbindung an externe LLM- oder ChatGPT-APIs",
                        "Strukturierte Markdown- und Diagramm-Kontexte für effiziente, reproduzierbare Wissenssuche"
                }
        ));

        content.getChildren().add(sectionHeader("SO FUNKTIONIERT ES"));
        VBox flowBox = new VBox(8);
        flowBox.setPadding(new Insets(4, 0, 20, 0));
        String[] steps = {
                "1  CONFIG          " + Icons.ARROW_RIGHT + "   Atlassian- und GitHub-Credentials einmalig eintragen und speichern",
                "2  DOWNLOAD        " + Icons.ARROW_RIGHT + "   Confluence-Seiten per URL/Suche auswählen und als Markdown herunterladen",
                "3  DOWNLOAD        " + Icons.ARROW_RIGHT + "   Jira-Tickets laden, filtern und ebenfalls als Markdown exportieren",
                "4  COPILOT         " + Icons.ARROW_RIGHT + "   Projektordner auswählen und GitHub Copilot CLI direkt starten",
                "5  SEARCH          " + Icons.ARROW_RIGHT + "   Alle Quellen (Confluence, Jira, GitHub, local .md) gleichzeitig durchsuchen"
        };
        for (String step : steps) {
            Label sl = new Label(step);
            sl.getStyleClass().addAll("hint");
            flowBox.getChildren().add(sl);
        }
        content.getChildren().add(flowBox);

        return content;
    }

    private static HBox buildAboutFooter() {
        HBox footer = new HBox();
        footer.setPadding(new Insets(12, 40, 12, 40));
        footer.getStyleClass().add("panel-border-top");
        Label footerLbl = new Label(
                "CONLOAD   ·  v1.0  ·  "
                        + " ·  Internal tool");
        footerLbl.getStyleClass().addAll("hint");
        footer.getChildren().add(footerLbl);
        return footer;
    }

    private static Label sectionHeader(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-header");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static VBox useCaseCard(String title, String[] bullets) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(14, 20, 14, 20));
        card.getStyleClass().addAll("card");
        VBox.setMargin(card, new Insets(8, 0, 4, 0));

        Label titleLbl = new Label(title);
        Theme.classes(titleLbl, Theme.CL_TITLE_SMALL);
        card.getChildren().add(titleLbl);

        for (String bullet : bullets) {
            HBox row = new HBox(8);
            Label dot = new Label(Icons.BULLET);
            dot.getStyleClass().addAll("small");
            dot.getStyleClass().add("icon");
            Label text = new Label(bullet);
            text.setWrapText(true);
            text.getStyleClass().addAll("secondary", "small");
            HBox.setHgrow(text, Priority.ALWAYS);
            row.getChildren().addAll(dot, text);
            card.getChildren().add(row);
        }
        return card;
    }
}
