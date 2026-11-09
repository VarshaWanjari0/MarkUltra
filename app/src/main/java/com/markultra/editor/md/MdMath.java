package com.markultra.editor.md;

/**
 * Turns LaTeX maths into something a TextView can show without a typesetter:
 * greek letters, operators, superscripts and subscripts are mapped to their
 * Unicode equivalents and the remaining structure is parenthesised.
 *
 * This is deliberately not TeX. It covers what shows up in READMEs (powers,
 * fractions, roots, sums, greek) and leaves anything unknown readable.
 */
public final class MdMath {

    private MdMath() {
    }

    /** Converts a LaTeX fragment to readable Unicode text. */
    public static String toUnicode(String tex) {
        if (tex == null) return "";
        StringBuilder out = new StringBuilder(tex.length());
        int i = 0;
        int n = tex.length();
        while (i < n) {
            char c = tex.charAt(i);
            if (c == '\\') {
                int j = i + 1;
                StringBuilder name = new StringBuilder();
                while (j < n && Character.isLetter(tex.charAt(j))) {
                    name.append(tex.charAt(j));
                    j++;
                }
                String cmd = name.toString();
                if (cmd.length() == 0) {
                    if (j < n) out.append(symbol(tex.charAt(j)));
                    i = j + 1;
                    continue;
                }
                if (cmd.equals("frac") || cmd.equals("dfrac") || cmd.equals("tfrac")) {
                    int[] p = group(tex, j);
                    String num = p == null ? "" : toUnicode(tex.substring(p[0], p[1]));
                    int[] q = p == null ? null : group(tex, p[2]);
                    String den = q == null ? "" : toUnicode(tex.substring(q[0], q[1]));
                    if (num.length() > 0 && den.length() > 0) {
                        out.append(num).append('/').append(den);
                    } else {
                        out.append(num).append(den);
                    }
                    i = q == null ? n : q[2];
                    continue;
                }
                if (cmd.equals("sqrt")) {
                    int[] p = group(tex, j);
                    if (p != null) {
                        out.append('\u221A').append('(').append(toUnicode(tex.substring(p[0], p[1])))
                                .append(')');
                        i = p[2];
                    } else {
                        out.append('\u221A');
                        i = j;
                    }
                    continue;
                }
                if (cmd.equals("text") || cmd.equals("mathrm") || cmd.equals("mathbf")
                        || cmd.equals("mathit") || cmd.equals("operatorname")) {
                    int[] p = group(tex, j);
                    if (p != null) {
                        out.append(tex, p[0], p[1]);
                        i = p[2];
                    } else {
                        i = j;
                    }
                    continue;
                }
                if (cmd.equals("left") || cmd.equals("right") || cmd.equals("big")
                        || cmd.equals("Big") || cmd.equals("bigl") || cmd.equals("bigr")) {
                    i = j;
                    while (i < n && tex.charAt(i) == ' ') i++;
                    if (i < n && tex.charAt(i) == '.') i++;
                    continue;
                }
                String mapped = command(cmd);
                if (mapped != null) {
                    out.append(mapped);
                } else {
                    out.append(cmd);
                }
                i = j;
                continue;
            }
            if (c == '^' || c == '_') {
                boolean sup = c == '^';
                int j = i + 1;
                String body;
                int next;
                if (j < n && tex.charAt(j) == '{') {
                    int[] p = group(tex, j);
                    if (p == null) {
                        body = "";
                        next = j;
                    } else {
                        body = toUnicode(tex.substring(p[0], p[1]));
                        next = p[2];
                    }
                } else if (j < n) {
                    body = String.valueOf(tex.charAt(j));
                    next = j + 1;
                } else {
                    body = "";
                    next = j;
                }
                out.append(sup ? superscript(body) : subscript(body));
                i = next;
                continue;
            }
            if (c == '{' || c == '}') {
                i++;
                continue;
            }
            if (c == ' ') {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') out.append(' ');
                i++;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString().trim();
    }

    /** True when {@code tex} is worth rendering as maths rather than as literal text. */
    public static boolean looksLikeMath(String tex) {
        if (tex == null) return false;
        String t = tex.trim();
        if (t.length() == 0 || t.length() > 600) return false;
        if (Character.isDigit(t.charAt(0)) && t.indexOf('$') < 0 && t.indexOf('\\') < 0
                && t.indexOf('^') < 0 && t.indexOf('_') < 0 && t.indexOf('=') < 0) {
            return false;
        }
        return true;
    }

    private static int[] group(String tex, int from) {
        int i = from;
        while (i < tex.length() && tex.charAt(i) == ' ') i++;
        if (i >= tex.length() || tex.charAt(i) != '{') return null;
        int depth = 0;
        int start = i + 1;
        while (i < tex.length()) {
            char c = tex.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return new int[]{start, i, i + 1};
            }
            i++;
        }
        return null;
    }

    private static String command(String cmd) {
        if (cmd.equals("alpha")) return "\u03B1";
        if (cmd.equals("beta")) return "\u03B2";
        if (cmd.equals("gamma")) return "\u03B3";
        if (cmd.equals("delta")) return "\u03B4";
        if (cmd.equals("epsilon")) return "\u03B5";
        if (cmd.equals("zeta")) return "\u03B6";
        if (cmd.equals("eta")) return "\u03B7";
        if (cmd.equals("theta")) return "\u03B8";
        if (cmd.equals("iota")) return "\u03B9";
        if (cmd.equals("kappa")) return "\u03BA";
        if (cmd.equals("lambda")) return "\u03BB";
        if (cmd.equals("mu")) return "\u03BC";
        if (cmd.equals("nu")) return "\u03BD";
        if (cmd.equals("xi")) return "\u03BE";
        if (cmd.equals("pi")) return "\u03C0";
        if (cmd.equals("rho")) return "\u03C1";
        if (cmd.equals("sigma")) return "\u03C3";
        if (cmd.equals("tau")) return "\u03C4";
        if (cmd.equals("upsilon")) return "\u03C5";
        if (cmd.equals("phi")) return "\u03C6";
        if (cmd.equals("chi")) return "\u03C7";
        if (cmd.equals("psi")) return "\u03C8";
        if (cmd.equals("omega")) return "\u03C9";
        if (cmd.equals("Gamma")) return "\u0393";
        if (cmd.equals("Delta")) return "\u0394";
        if (cmd.equals("Theta")) return "\u0398";
        if (cmd.equals("Lambda")) return "\u039B";
        if (cmd.equals("Xi")) return "\u039E";
        if (cmd.equals("Pi")) return "\u03A0";
        if (cmd.equals("Sigma")) return "\u03A3";
        if (cmd.equals("Phi")) return "\u03A6";
        if (cmd.equals("Psi")) return "\u03A8";
        if (cmd.equals("Omega")) return "\u03A9";
        if (cmd.equals("times")) return "\u00D7";
        if (cmd.equals("div")) return "\u00F7";
        if (cmd.equals("cdot")) return "\u22C5";
        if (cmd.equals("pm")) return "\u00B1";
        if (cmd.equals("mp")) return "\u2213";
        if (cmd.equals("leq") || cmd.equals("le")) return "\u2264";
        if (cmd.equals("geq") || cmd.equals("ge")) return "\u2265";
        if (cmd.equals("neq") || cmd.equals("ne")) return "\u2260";
        if (cmd.equals("approx")) return "\u2248";
        if (cmd.equals("equiv")) return "\u2261";
        if (cmd.equals("infty")) return "\u221E";
        if (cmd.equals("sum")) return "\u2211";
        if (cmd.equals("prod")) return "\u220F";
        if (cmd.equals("int")) return "\u222B";
        if (cmd.equals("partial")) return "\u2202";
        if (cmd.equals("nabla")) return "\u2207";
        if (cmd.equals("to") || cmd.equals("rightarrow")) return "\u2192";
        if (cmd.equals("leftarrow")) return "\u2190";
        if (cmd.equals("Rightarrow")) return "\u21D2";
        if (cmd.equals("Leftarrow")) return "\u21D0";
        if (cmd.equals("leftrightarrow")) return "\u2194";
        if (cmd.equals("in")) return "\u2208";
        if (cmd.equals("notin")) return "\u2209";
        if (cmd.equals("subset")) return "\u2282";
        if (cmd.equals("supset")) return "\u2283";
        if (cmd.equals("cup")) return "\u222A";
        if (cmd.equals("cap")) return "\u2229";
        if (cmd.equals("forall")) return "\u2200";
        if (cmd.equals("exists")) return "\u2203";
        if (cmd.equals("angle")) return "\u2220";
        if (cmd.equals("degree")) return "\u00B0";
        if (cmd.equals("perp")) return "\u22A5";
        if (cmd.equals("parallel")) return "\u2225";
        if (cmd.equals("dots") || cmd.equals("ldots")) return "\u2026";
        if (cmd.equals("cdots")) return "\u22EF";
        if (cmd.equals("quad") || cmd.equals("qquad")) return " ";
        if (cmd.equals("sin")) return "sin";
        if (cmd.equals("cos")) return "cos";
        if (cmd.equals("tan")) return "tan";
        if (cmd.equals("log")) return "log";
        if (cmd.equals("ln")) return "ln";
        if (cmd.equals("lim")) return "lim";
        if (cmd.equals("mod")) return " mod ";
        return null;
    }

    private static String symbol(char c) {
        switch (c) {
            case ',': case ';': case ':': case ' ':
                return " ";
            case '$': case '%': case '&': case '#': case '_': case '{': case '}':
                return String.valueOf(c);
            default:
                return String.valueOf(c);
        }
    }

    private static final String SUP = "0123456789+-=()ni\u00B2\u00B3\u00B9";
    private static final String SUP_OUT = "\u2070\u00B9\u00B2\u00B3\u2074\u2075\u2076\u2077\u2078\u2079\u207A\u207B\u207C\u207D\u207E\u207F";

    private static String superscript(String body) {
        if (body.length() == 1) {
            int i = SUP.indexOf(body.charAt(0));
            if (i >= 0 && i < SUP_OUT.length()) return String.valueOf(SUP_OUT.charAt(i));
        }
        if (body.length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            int k = SUP.indexOf(body.charAt(i));
            sb.append(k >= 0 && k < SUP_OUT.length() ? SUP_OUT.charAt(k) : body.charAt(i));
        }
        return sb.toString();
    }

    private static String subscript(String body) {
        if (body.length() == 0) return "";
        final String subs = "\u2080\u2081\u2082\u2083\u2084\u2085\u2086\u2087\u2088\u2089";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c >= '0' && c <= '9') sb.append(subs.charAt(c - '0'));
            else if (c == '+') sb.append('\u208A');
            else if (c == '-') sb.append('\u208B');
            else if (c == '=') sb.append('\u208C');
            else if (c == '(') sb.append('\u208D');
            else if (c == ')') sb.append('\u208E');
            else sb.append(c);
        }
        return sb.toString();
    }
}
