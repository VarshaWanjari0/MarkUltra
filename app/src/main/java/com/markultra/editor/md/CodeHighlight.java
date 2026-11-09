package com.markultra.editor.md;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Syntax highlighting for fenced code, in the spirit of GitHub's highlight pass:
 * keywords, strings, numbers, comments, functions and types get the classes
 * names github.com itself emits - {@code pl-k}, {@code pl-s}, {@code pl-c1},
 * {@code pl-c}, {@code pl-en}, {@code pl-v} and {@code pl-smi} - so the same
 * stylesheet rules that colour github.com colour this preview.
 *
 * <p>A small hand written scanner rather than a grammar library: fences have to render
 * in a few milliseconds on a phone and unknown languages fall back to plain text.</p>
 */
final class CodeHighlight {

    private CodeHighlight() {
    }

    private static final Set<String> MATH = new HashSet<String>(Arrays.asList(
            "math", "katex", "tex", "latex"));

    static boolean isMathLanguage(String lang) {
        return lang != null && MATH.contains(lang.trim().toLowerCase(Locale.ROOT));
    }

    private static final Set<String> C_KEYWORDS = words(
            "abstract assert async await boolean break byte case catch char class const continue "
            + "debugger default delete do double else enum export extends final finally float for "
            + "from function goto if implements import in instanceof int interface internal is let "
            + "long namespace native new of operator override package private protected public "
            + "readonly return sealed short sizeof static struct super switch synchronized "
            + "template this throw throws trait transient try typedef typeof union unsigned using "
            + "val var virtual void volatile while with yield implements fun object companion init "
            + "data when typealias inline suspend lateinit open actual expect reified where "
            + "composite asm inline restrict self Self mut impl dyn pub crate mod match loop ref "
            + "unsafe extern macro_rules derive echo require include define ifdef ifndef endif "
            + "defined pragma nullptr constexpr decltype noexcept alignas auto register extern "
            + "dynamic_cast static_cast reinterpret_cast const_cast wchar_t");

    /** Storage types: github.com colours these with {@code pl-smi}, like {@code String}. */
    private static final Set<String> TYPE_KEYWORDS = words(
            "void int long short byte char float double boolean var unsigned signed "
            + "wchar_t auto constexpr");

    private static final Set<String> C_TYPES = words(
            "String Integer Long Double Float Boolean Character Object List Map Set Array "
            + "HashMap ArrayList HashSet Optional Stream Exception RuntimeException "
            + "Thread Runnable Class Void Byte Short Number StringBuilder CharSequence "
            + "true false null undefined NaN Infinity");

    private static final Set<String> PY_KEYWORDS = words(
            "and as assert async await break class continue def del elif else except finally for "
            + "from global if import in is lambda nonlocal not or pass raise return try while with "
            + "yield match case print self None True False");

    private static final Set<String> SH_KEYWORDS = words(
            "if then else elif fi for while do done case esac function return exit export local "
            + "read echo cd set unset shift source alias break continue until select in sudo "
            + "apt apt-get yum dnf brew npm npx yarn pnpm pip pip3 python python3 java javac "
            + "gradle gradlew git curl wget make cmake docker");

    private static final Set<String> SQL_KEYWORDS = words(
            "select from where and or not insert into values update set delete create table drop "
            + "alter add column index primary key foreign references join left right inner outer "
            + "on group by order having limit offset union all distinct as case when then else end "
            + "null is like between exists count sum avg min max begin commit rollback");

    private static final Set<String> CSS_KEYWORDS = words(
            "@media @import @font-face @keyframes @supports @charset important");

    private static Set<String> words(String source) {
        Set<String> set = new HashSet<String>();
        for (String w : source.split(" ")) {
            if (w.length() > 0) set.add(w);
        }
        return set;
    }

    private enum Family { C_LIKE, PYTHON, SHELL, JSON, SQL, MARKUP, CSS, DIFF, PLAIN }

    private static Family family(String lang) {
        String l = lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT);
        int dot = l.lastIndexOf('.');
        if (dot >= 0) l = l.substring(dot + 1);
        if (l.equals("json") || l.equals("json5") || l.equals("jsonc")) return Family.JSON;
        if (l.equals("html") || l.equals("xml") || l.equals("xhtml") || l.equals("svg")
                || l.equals("vue") || l.equals("plist") || l.equals("xaml")) return Family.MARKUP;
        if (l.equals("css") || l.equals("scss") || l.equals("sass") || l.equals("less")) return Family.CSS;
        if (l.equals("py") || l.equals("python") || l.equals("python3") || l.equals("ruby")
                || l.equals("rb") || l.equals("yaml") || l.equals("yml") || l.equals("toml")
                || l.equals("ini") || l.equals("r") || l.equals("perl")) return Family.PYTHON;
        if (l.equals("sh") || l.equals("bash") || l.equals("shell") || l.equals("zsh")
                || l.equals("console") || l.equals("dockerfile") || l.equals("makefile")
                || l.equals("properties") || l.equals("conf")) return Family.SHELL;
        if (l.equals("sql") || l.equals("postgres") || l.equals("mysql") || l.equals("sqlite"))
            return Family.SQL;
        if (l.equals("diff") || l.equals("patch") || l.equals("udiff")) return Family.DIFF;
        // a regular expression is mostly backslashes and braces: a C scanner would split
        // every \d and \s into pieces, so the block is left exactly as written
        if (l.length() == 0 || l.equals("text") || l.equals("txt") || l.equals("plain")
                || l.equals("log") || l.equals("regex") || l.equals("regexp")
                || l.equals("re")) return Family.PLAIN;
        return Family.C_LIKE;
    }

    static String highlight(String code, String lang) {
        if (code == null) return "";
        Family f = family(lang);
        switch (f) {
            case MARKUP:
                return markup(code);
            case JSON:
                return cLike(code, true);
            case CSS:
                return css(code);
            case PYTHON:
                return cLike(code, false, PY_KEYWORDS, true);
            case SHELL:
                return cLike(code, false, SH_KEYWORDS, true);
            case SQL:
                return sql(code);
            case DIFF:
                return diff(code);
            case C_LIKE:
                return cLike(code, false, C_KEYWORDS, false);
            default:
                return HtmlSanitize.escape(code);
        }
    }

    // =================================================================================

    /**
     * A unified diff: whole lines are coloured by their first character, the way a diff
     * reader expects, instead of running a code scanner over both sides of the change.
     */
    private static String diff(String code) {
        String[] lines = code.split("\n", -1);
        StringBuilder out = new StringBuilder(code.length() + lines.length * 32);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String cls = null;
            if (line.startsWith("+++") || line.startsWith("---")) cls = "mu-diff-meta";
            else if (line.startsWith("@@")) cls = "mu-diff-meta";
            else if (line.startsWith("+")) cls = "mu-diff-add";
            else if (line.startsWith("-")) cls = "mu-diff-del";
            if (cls == null) {
                out.append(HtmlSanitize.escape(line));
            } else {
                out.append("<span class=\"").append(cls).append("\">")
                        .append(HtmlSanitize.escape(line)).append("</span>");
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    private static String cLike(String code, boolean strictJson) {
        return cLike(code, strictJson, C_KEYWORDS, false);
    }

    /**
     * One scanner covers the C family, Python, shell and JSON: the differences are only
     * which comment marker starts a comment and which words count as keywords.
     */
    private static String cLike(String code, boolean strictJson, Set<String> keywords,
                                boolean hashComments) {
        StringBuilder out = new StringBuilder(code.length() + code.length() / 4);
        int i = 0;
        int n = code.length();
        while (i < n) {
            char c = code.charAt(i);
            char next = i + 1 < n ? code.charAt(i + 1) : '\0';

            if (c == '/' && next == '*') {
                int end = code.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                span(out, "pl-c", code, i, end);
                i = end;
                continue;
            }
            if (c == '/' && next == '/' && !strictJson) {
                int end = code.indexOf('\n', i);
                end = end < 0 ? n : end;
                span(out, "pl-c", code, i, end);
                i = end;
                continue;
            }
            if (hashComments && c == '#') {
                int end = code.indexOf('\n', i);
                end = end < 0 ? n : end;
                span(out, "pl-c", code, i, end);
                i = end;
                continue;
            }
            if (c == '"' || c == '\'' || (c == '`' && !strictJson)) {
                int end = i + 1;
                while (end < n) {
                    char e = code.charAt(end);
                    if (e == '\\') {
                        end += 2;
                        continue;
                    }
                    if (e == c) {
                        end++;
                        break;
                    }
                    if (e == '\n' && c != '`') break;
                    end++;
                }
                if (end > n) end = n;
                // JSON object keys read as types, the way GitHub shows them
                boolean key = strictJson && c == '"' && nextNonSpace(code, end) == ':';
                span(out, key ? "pl-smi" : "pl-s", code, i, Math.min(end, n));
                i = Math.min(end, n);
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && Character.isDigit(next))) {
                int end = i;
                while (end < n) {
                    char d = code.charAt(end);
                    if (Character.isLetterOrDigit(d) || d == '.' || d == '_'
                            || ((d == '+' || d == '-') && end > i
                            && (code.charAt(end - 1) == 'e' || code.charAt(end - 1) == 'E'))) {
                        end++;
                    } else {
                        break;
                    }
                }
                span(out, "pl-c1", code, i, end);
                i = end;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int end = i;
                while (end < n && Character.isJavaIdentifierPart(code.charAt(end))) end++;
                String word = code.substring(i, end);
                if (keywords.contains(word)) {
                    // storage types ("void", "int") are coloured as types by github.com
                    span(out, TYPE_KEYWORDS.contains(word) ? "pl-smi" : "pl-k", code, i, end);
                } else if (C_TYPES.contains(word)) {
                    span(out, "pl-smi", code, i, end);
                } else if (nextNonSpaceChar(code, end) == '(') {
                    span(out, "pl-en", code, i, end);
                } else if (Character.isUpperCase(c) && word.length() > 1) {
                    span(out, "pl-smi", code, i, end);
                } else {
                    // local names are variables in GitHub's palette
                    span(out, "pl-s1", code, i, end);
                }
                i = end;
                continue;
            }
            if (c == '$' && i + 1 < n && (Character.isLetter(next) || next == '{')) {
                int end = i + 1;
                if (next == '{') {
                    int close = code.indexOf('}', i);
                    end = close < 0 ? n : close + 1;
                } else {
                    while (end < n && (Character.isLetterOrDigit(code.charAt(end))
                            || code.charAt(end) == '_')) end++;
                }
                span(out, "pl-v", code, i, end);
                i = end;
                continue;
            }
            out.append(HtmlSanitize.escape(String.valueOf(c)));
            i++;
        }
        return out.toString();
    }

    private static String sql(String code) {
        StringBuilder out = new StringBuilder(code.length() + 64);
        int i = 0;
        int n = code.length();
        while (i < n) {
            char c = code.charAt(i);
            char next = i + 1 < n ? code.charAt(i + 1) : '\0';
            if (c == '-' && next == '-') {
                int end = code.indexOf('\n', i);
                end = end < 0 ? n : end;
                span(out, "pl-c", code, i, end);
                i = end;
                continue;
            }
            if (c == '\'' || c == '"') {
                int end = i + 1;
                while (end < n && code.charAt(end) != c) end++;
                end = Math.min(end + 1, n);
                span(out, "pl-s", code, i, end);
                i = end;
                continue;
            }
            if (Character.isDigit(c)) {
                int end = i;
                while (end < n && (Character.isDigit(code.charAt(end)) || code.charAt(end) == '.')) end++;
                span(out, "pl-c1", code, i, end);
                i = end;
                continue;
            }
            if (Character.isLetter(c)) {
                int end = i;
                while (end < n && Character.isLetterOrDigit(code.charAt(end))) end++;
                String word = code.substring(i, end);
                if (SQL_KEYWORDS.contains(word.toLowerCase(Locale.ROOT))) {
                    span(out, "pl-k", code, i, end);
                } else {
                    out.append(HtmlSanitize.escape(word));
                }
                i = end;
                continue;
            }
            out.append(HtmlSanitize.escape(String.valueOf(c)));
            i++;
        }
        return out.toString();
    }

    /** Tags, attribute names and values, comments: enough for HTML, XML and Vue. */
    private static String markup(String code) {
        StringBuilder out = new StringBuilder(code.length() + 64);
        int i = 0;
        int n = code.length();
        while (i < n) {
            char c = code.charAt(i);
            if (c != '<') {
                out.append(HtmlSanitize.escape(String.valueOf(c)));
                i++;
                continue;
            }
            if (code.startsWith("<!--", i)) {
                int end = code.indexOf("-->", i + 4);
                end = end < 0 ? n : end + 3;
                span(out, "pl-c", code, i, end);
                i = end;
                continue;
            }
            out.append("&lt;");
            int j = i + 1;
            boolean nameDone = false;
            while (j < n) {
                char t = code.charAt(j);
                if (t == '>') {
                    out.append("&gt;");
                    j++;
                    break;
                }
                if (t == '"' || t == '\'') {
                    int close = code.indexOf(t, j + 1);
                    close = close < 0 ? n : close + 1;
                    span(out, "pl-s", code, j, close);
                    j = close;
                    continue;
                }
                if (Character.isWhitespace(t)) {
                    out.append(' ');
                    nameDone = true;
                    j++;
                    continue;
                }
                if (t == '=' || t == '/') {
                    out.append(HtmlSanitize.escape(String.valueOf(t)));
                    j++;
                    continue;
                }
                int wordEnd = j;
                while (wordEnd < n && (Character.isLetterOrDigit(code.charAt(wordEnd))
                        || code.charAt(wordEnd) == '-' || code.charAt(wordEnd) == '_'
                        || code.charAt(wordEnd) == ':' || code.charAt(wordEnd) == '!'
                        || code.charAt(wordEnd) == '?')) wordEnd++;
                if (wordEnd == j) {
                    out.append(HtmlSanitize.escape(String.valueOf(t)));
                    j++;
                    continue;
                }
                span(out, nameDone ? "pl-v" : "pl-smi", code, j, wordEnd);
                j = wordEnd;
            }
            i = j;
        }
        return out.toString();
    }

    /** Selectors stay plain, property names and at-rules are coloured. */
    private static String css(String code) {
        StringBuilder out = new StringBuilder(code.length() + 64);
        int i = 0;
        int n = code.length();
        boolean inBlock = false;
        while (i < n) {
            char c = code.charAt(i);
            if (c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
                int end = code.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                span(out, "pl-c", code, i, end);
                i = end;
                continue;
            }
            if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < n && code.charAt(end) != c) end++;
                end = Math.min(end + 1, n);
                span(out, "pl-s", code, i, end);
                i = end;
                continue;
            }
            if (c == '{') inBlock = true;
            if (c == '}') inBlock = false;
            if (c == '@') {
                int end = i + 1;
                while (end < n && (Character.isLetterOrDigit(code.charAt(end))
                        || code.charAt(end) == '-')) end++;
                span(out, "pl-k", code, i, end);
                i = end;
                continue;
            }
            if (inBlock && (Character.isLetter(c) || c == '-')) {
                int end = i;
                while (end < n && (Character.isLetterOrDigit(code.charAt(end))
                        || code.charAt(end) == '-')) end++;
                if (end < n && code.charAt(end) == ':') {
                    span(out, "pl-v", code, i, end);
                    i = end;
                    continue;
                }
            }
            if (Character.isDigit(c)) {
                int end = i;
                while (end < n && (Character.isLetterOrDigit(code.charAt(end))
                        || code.charAt(end) == '.' || code.charAt(end) == '%')) end++;
                span(out, "pl-c1", code, i, end);
                i = end;
                continue;
            }
            if (CSS_KEYWORDS.contains(String.valueOf(c))) {
                out.append(HtmlSanitize.escape(String.valueOf(c)));
                i++;
                continue;
            }
            out.append(HtmlSanitize.escape(String.valueOf(c)));
            i++;
        }
        return out.toString();
    }

    // =================================================================================

    private static void span(StringBuilder out, String cls, String code, int from, int to) {
        out.append("<span class=\"").append(cls).append("\">")
                .append(HtmlSanitize.escape(code.substring(from, to)))
                .append("</span>");
    }

    private static char nextNonSpace(String code, int from) {
        for (int i = from; i < code.length(); i++) {
            char c = code.charAt(i);
            if (!Character.isWhitespace(c)) return c;
        }
        return '\0';
    }

    private static char nextNonSpaceChar(String code, int from) {
        return nextNonSpace(code, from);
    }
}
