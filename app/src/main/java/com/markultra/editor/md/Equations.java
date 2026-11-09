package com.markultra.editor.md;

/**
 * Ready-made equations for the maths sheet.
 *
 * <p>Each entry is the TeX the editor inserts verbatim, so what the user sees in the sheet
 * is what lands in the document: no template language, no placeholder the KaTeX pass has
 * to guess at. {@link #display} says whether the snippet goes in as a fenced
 * {@code $$} block or as inline {@code $...$} maths.</p>
 */
public final class Equations {

    /** One equation: a name, the TeX, and whether it is a display block. */
    public static final class Equation {
        public final String title;
        public final String hint;
        public final String tex;
        public final boolean display;

        Equation(String title, String hint, String tex, boolean display) {
            this.title = title;
            this.hint = hint;
            this.tex = tex;
            this.display = display;
        }

        /** The Markdown this equation becomes, including its delimiters. */
        public String markdown() {
            return display ? "$$\n" + tex + "\n$$\n" : "$" + tex + "$";
        }
    }

    private Equations() {
    }

    private static Equation e(String title, String hint, String tex, boolean display) {
        return new Equation(title, hint, tex, display);
    }

    /** Display equations, in the order the sheet shows them. */
    public static final Equation[] DISPLAY = {
            e("Quadratic formula", "Roots of ax^2 + bx + c",
                    "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}", true),
            e("Binomial theorem", "Expansion with sum notation",
                    "(x + y)^n = \\sum_{k=0}^{n} \\binom{n}{k} x^{n-k} y^{k}", true),
            e("Definite integral", "An integral with limits",
                    "\\int_{a}^{b} f(x)\\,\\mathrm{d}x = F(b) - F(a)", true),
            e("Summation", "A series",
                    "\\sum_{k=1}^{n} k = \\frac{n(n+1)}{2}", true),
            e("Limit", "A limit to infinity",
                    "\\lim_{x \\to \\infty} \\left(1 + \\frac{1}{x}\\right)^{x} = e", true),
            e("Derivative", "Definition of the derivative",
                    "f'(x) = \\lim_{h \\to 0} \\frac{f(x+h) - f(x)}{h}", true),
            e("Matrix", "A 2x2 matrix",
                    "A = \\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}", true),
            e("Determinant", "A 2x2 determinant",
                    "\\det A = \\begin{vmatrix} a & b \\\\ c & d \\end{vmatrix} = ad - bc", true),
            e("Cases", "A piecewise function",
                    "f(x) = \\begin{cases} x^2 & x \\ge 0 \\\\ -x & x < 0 \\end{cases}", true),
            e("Aligned equations", "Two lines aligned on =",
                    "\\begin{aligned}\n a + b &= c \\\\\n d + e &= f\n\\end{aligned}", true),
            e("Bayes' theorem", "Posterior probability",
                    "P(A \\mid B) = \\frac{P(B \\mid A)\\,P(A)}{P(B)}", true),
            e("Maxwell's equations", "Differential form, four lines",
                    "\\begin{aligned}\n \\nabla \\cdot \\mathbf{E} &= \\frac{\\rho}{\\varepsilon_0} \\\\\n"
                            + " \\nabla \\cdot \\mathbf{B} &= 0 \\\\\n"
                            + " \\nabla \\times \\mathbf{E} &= -\\frac{\\partial \\mathbf{B}}{\\partial t} \\\\\n"
                            + " \\nabla \\times \\mathbf{B} &= \\mu_0 \\mathbf{J}"
                            + " + \\mu_0 \\varepsilon_0 \\frac{\\partial \\mathbf{E}}{\\partial t}\n"
                            + "\\end{aligned}", true),
            e("Fourier transform", "The pair",
                    "\\hat{f}(\\xi) = \\int_{-\\infty}^{\\infty} f(x)\\,e^{-2\\pi i x \\xi}\\,\\mathrm{d}x",
                    true),
            e("Euler's identity", "The short one",
                    "e^{i\\pi} + 1 = 0", true),
            e("Taylor series", "Expansion about a",
                    "f(x) = \\sum_{n=0}^{\\infty} \\frac{f^{(n)}(a)}{n!}(x - a)^n", true),
            e("Physical units", "A quantity with units",
                    "E = mc^2 \\quad [\\mathrm{J}]", true),
    };

    /** Inline snippets: they land inside a sentence. */
    public static final Equation[] INLINE = {
            e("Fraction", "a over b", "\\frac{a}{b}", false),
            e("Square root", "Root of x", "\\sqrt{x}", false),
            e("Power and subscript", "x squared, sub i", "x_i^2", false),
            e("Vector", "Bold v", "\\mathbf{v}", false),
            e("Real numbers", "Blackboard R", "\\R", false),
            e("Absolute value", "|x|", "\\abs{x}", false),
            e("Norm", "||x||", "\\norm{x}", false),
            e("Partial derivative", "d f / d x", "\\frac{\\partial f}{\\partial x}", false),
            e("Approximately", "x is about y", "x \\approx y", false),
            e("Times ten to the power", "Scientific notation", "3.0 \\times 10^{8}", false),
    };

    /** Every snippet that contains {@code query}, display and inline together. */
    public static java.util.List<Equation> search(String query) {
        java.util.ArrayList<Equation> out = new java.util.ArrayList<Equation>();
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        add(out, DISPLAY, q);
        add(out, INLINE, q);
        return out;
    }

    private static void add(java.util.List<Equation> out, Equation[] source, String q) {
        for (int i = 0; i < source.length; i++) {
            Equation equation = source[i];
            if (q.length() == 0
                    || equation.title.toLowerCase(java.util.Locale.ROOT).contains(q)
                    || equation.hint.toLowerCase(java.util.Locale.ROOT).contains(q)) {
                out.add(equation);
            }
        }
    }
}
