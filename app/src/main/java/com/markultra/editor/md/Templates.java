package com.markultra.editor.md;

import java.util.ArrayList;
import java.util.List;

/**
 * Ready made starting documents.
 *
 * <p>Every template is plain Markdown, rendered by the same pipeline as the rest of the
 * app, and carries a {@link #CURSOR} token where the caret should land so a new document
 * is never just an empty page.</p>
 */
public final class Templates {

    /** Marker the insertion code replaces with the caret position. */
    public static final String CURSOR = "{{cursor}}";

    /** One template: what it is called, what it is for, and its Markdown. */
    public static final class Template {
        public final String name;
        public final String hint;
        public final String body;

        Template(String name, String hint, String body) {
            this.name = name;
            this.hint = hint;
            this.body = body;
        }
    }

    private Templates() {
    }

    private static final List<Template> ALL = new ArrayList<Template>();

    static {
        ALL.add(new Template("Blank", "An empty document", CURSOR));

        ALL.add(new Template("README", "Project front page with badges and install steps",
                "# Project name\n\n"
                + "One sentence that says what this is and who it is for.\n\n"
                + "<p align=\"center\">\n"
                + "  <img src=\"https://img.shields.io/badge/build-passing-2ea043\" alt=\"build\">\n"
                + "  <img src=\"https://img.shields.io/badge/licence-MIT-blue\" alt=\"licence\">\n"
                + "</p>\n\n"
                + "## Features\n\n"
                + "- [x] Works offline\n"
                + "- [ ] Everything else\n\n"
                + "## Install\n\n"
                + "```bash\n"
                + "git clone https://github.com/you/project.git\n"
                + "```\n\n"
                + "## Usage\n\n"
                + "```java\n"
                + "Project p = new Project();\n"
                + "p.run();\n"
                + "```\n\n"
                + "## Licence\n\n"
                + "MIT - see [LICENCE](LICENCE).\n\n"
                + CURSOR));

        ALL.add(new Template("Changelog", "Keep a running list of releases",
                "# Changelog\n\n"
                + "All notable changes to this project are recorded here.\n"
                + "The format follows [Keep a Changelog](https://keepachangelog.com/).\n\n"
                + "## [Unreleased]\n\n"
                + "### Added\n\n"
                + "- " + CURSOR + "\n\n"
                + "### Changed\n\n"
                + "### Fixed\n\n"
                + "## [1.0.0] - YYYY-MM-DD\n\n"
                + "### Added\n\n"
                + "- First release\n"));

        ALL.add(new Template("Meeting notes", "Agenda, decisions, action items",
                "# Meeting - YYYY-MM-DD\n\n"
                + "**Attendees:** \n\n"
                + "## Agenda\n\n"
                + "1. \n"
                + "2. \n\n"
                + "## Notes\n\n"
                + CURSOR + "\n\n"
                + "## Decisions\n\n"
                + "- \n\n"
                + "## Action items\n\n"
                + "- [ ] Owner - task (due YYYY-MM-DD)\n"));

        ALL.add(new Template("Decision record", "Architecture decision record",
                "# ADR-0001: Decision title\n\n"
                + "- **Status:** proposed\n"
                + "- **Date:** YYYY-MM-DD\n"
                + "- **Deciders:** \n\n"
                + "## Context\n\n"
                + "What is the problem, and what constraints apply?\n\n"
                + CURSOR + "\n\n"
                + "## Decision\n\n"
                + "What we are doing, in one paragraph.\n\n"
                + "## Consequences\n\n"
                + "- Good: \n"
                + "- Bad: \n\n"
                + "## Alternatives considered\n\n"
                + "| Option | Why not |\n"
                + "|:-------|:--------|\n"
                + "|        |         |\n"));

        ALL.add(new Template("Issue", "Bug report or feature request",
                "## What happened\n\n"
                + CURSOR + "\n\n"
                + "## What I expected\n\n"
                + "## Steps to reproduce\n\n"
                + "1. \n"
                + "2. \n\n"
                + "## Environment\n\n"
                + "| Item | Value |\n"
                + "|:-----|:------|\n"
                + "| App version | |\n"
                + "| Android | |\n"
                + "| Device | |\n\n"
                + "## Evidence\n\n"
                + "```text\n"
                + "paste logs here\n"
                + "```\n"));

        ALL.add(new Template("Blog post", "Title, intro, body, conclusion",
                "# Post title\n\n"
                + "_A one line subtitle that makes people want to read on._\n\n"
                + "Intro paragraph.\n\n"
                + "## Section\n\n"
                + CURSOR + "\n\n"
                + "> Pull quote or aside.\n\n"
                + "## Conclusion\n\n"
                + "What the reader should take away.\n"));

        ALL.add(new Template("Checklist", "A task list with sections",
                "## Checklist\n\n"
                + "- [ ] " + CURSOR + "\n"
                + "- [ ] \n\n"
                + "### Later\n\n"
                + "- [ ] \n"));

        ALL.add(new Template("Documentation page", "Reference page with TOC and table",
                "# Page title\n\n"
                + "What this page covers, in two sentences.\n\n"
                + "## Contents\n\n"
                + "- [Install](#install)\n"
                + "- [Configuration](#configuration)\n\n"
                + "## Install\n\n"
                + CURSOR + "\n\n"
                + "## Configuration\n\n"
                + "| Key | Type | Default | Meaning |\n"
                + "|:----|:----:|--------:|:--------|\n"
                + "|     |      |         |         |\n\n"
                + "## See also\n\n"
                + "- [Related page](related.md)\n"));
    }

    public static List<Template> all() {
        return ALL;
    }

    public static Template byName(String name) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).name.equalsIgnoreCase(name)) return ALL.get(i);
        }
        return ALL.get(0);
    }
}
