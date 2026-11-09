package com.markultra.editor.md;

import java.util.ArrayList;
import java.util.List;

/** Plain text plus the {@link MdSpan}s that style it. */
public final class InlineResult {

    public final String text;
    public final List<MdSpan> spans;

    InlineResult(String text, List<MdSpan> spans) {
        this.text = text;
        this.spans = spans;
    }

    public static InlineResult plain(String text) {
        return new InlineResult(text, new ArrayList<MdSpan>(0));
    }

    /** Visible text of the rendered inline run (markers removed). */
    public String plainText() {
        return text;
    }
}
