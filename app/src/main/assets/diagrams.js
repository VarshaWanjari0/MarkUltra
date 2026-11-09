/*
 * MarkUltra diagram runner.
 *
 * Graphviz, PlantUML and Vega-Lite are not Mermaid, so they need engines of their own.
 * Every engine is vendored in the APK and loaded by a plain <script> tag in the page -
 * assets/viz-global.js (Graphviz compiled to WebAssembly), assets/plantuml.js (the real
 * PlantUML engine compiled to JavaScript) and assets/vega*.js. The page only emits those
 * tags when the document actually contains such a block, so a README that has none pays
 * nothing for them.
 *
 * This file therefore has one job: draw. It does not fetch anything, it does not wait
 * for scripts it hopes will arrive, and it does not depend on the WebView's file access
 * rules - it uses the globals the tags above it define. That is the difference between a
 * diagram that renders on a phone and one that silently does not.
 *
 * Contract with the renderer (GfmHtml):
 *
 *   <div class="mu-diagram mu-nocopy" data-lang="graphviz">
 *     <pre class="mu-src">...source...</pre>
 *   </div>
 *
 * On success the source is hidden and the SVG is inserted in front of it. On failure the
 * source stays on screen with a one-line reason underneath: a diagram the engine rejected
 * is still information, and a blank block is not.
 */
(function () {
    'use strict';

    /* A block that failed once can be tried again: the page may have fetched the engine
       over the bridge after the first attempt, which is exactly the case this runner
       exists for. */
    function reset(box) {
        var notes = box.querySelectorAll('.mu-diagram-error-note');
        for (var i = 0; i < notes.length; i++) notes[i].parentNode.removeChild(notes[i]);
        box.className = String(box.className).replace(/\s*mu-diagram-error/g, '');
        box.removeAttribute('data-rendered');
    }

    function message(box, lang, text) {
        if (box.getAttribute('data-rendered') === 'failed') return;
        box.className += ' mu-diagram-error';
        box.setAttribute('data-rendered', 'failed');
        var note = document.createElement('p');
        note.className = 'mu-diagram-error-note';
        note.textContent = lang + ': ' + (text || 'could not be rendered');
        box.appendChild(note);
    }

    function finish(box, svg, lang) {
        if (!svg || typeof svg !== 'string') {
            message(box, lang, 'the engine returned nothing');
            return;
        }
        var holder = document.createElement('div');
        holder.className = 'mu-rendered';
        holder.innerHTML = svg;
        var pre = box.querySelector('.mu-src');
        box.insertBefore(holder, pre || null);
        if (pre) pre.style.display = 'none';
        box.setAttribute('data-rendered', 'ok');
    }

    /* The page records why an engine never arrived (a script the WebView refused, a
       bundle that threw on this JavaScript version, a bridge that returned nothing).
       Appending it turns "did not load" into something a reader can act on. */
    function why(file) {
        try {
            var detail = window.muEngineError ? window.muEngineError(file) : '';
            return detail ? ' (' + detail + ')' : '';
        } catch (e) {
            return '';
        }
    }

    function reason(e) {
        if (!e) return 'unknown error';
        return String(e.message || e.str || e).slice(0, 300);
    }

    /* ---------------------------------------------------------------- Graphviz */

    function drawGraphviz(box, src, lang) {
        if (!window.Viz || typeof window.Viz.instance !== 'function') {
            message(box, lang, 'the Graphviz engine did not load' + why('viz-global.js'));
            return;
        }
        window.Viz.instance().then(function (viz) {
            var svg;
            try {
                svg = viz.renderString(src, { format: 'svg' });
            } catch (e) {
                message(box, lang, reason(e));
                return;
            }
            finish(box, svg, lang);
        }, function (e) {
            message(box, lang, reason(e));
        });
    }

    /* ---------------------------------------------------------------- PlantUML */

    function drawPlantuml(box, src, lang) {
        if (!window.PlantUML || typeof window.PlantUML.renderToString !== 'function') {
            message(box, lang, 'the PlantUML engine did not load' + why('plantuml.js'));
            return;
        }
        var lines = src.split('\n');
        // A block pasted without its @startuml wrapper is still a diagram
        var wrapped = false;
        for (var i = 0; i < lines.length; i++) {
            if (lines[i].indexOf('@start') === 0) { wrapped = true; break; }
        }
        if (!wrapped) lines = ['@startuml'].concat(lines).concat(['@enduml']);
        try {
            window.PlantUML.renderToString(lines, function (svg) {
                finish(box, svg, lang);
            }, function (err) {
                message(box, lang, reason(err));
            });
        } catch (e) {
            message(box, lang, reason(e));
        }
    }

    /* ---------------------------------------------------------------- Vega */

    function drawVega(box, src, lang) {
        if (!window.vega || typeof window.vega.parse !== 'function') {
            message(box, lang, 'the Vega engine did not load' + why('vega.min.js'));
            return;
        }
        var spec;
        try {
            spec = JSON.parse(src);
        } catch (e) {
            message(box, lang, 'the block is not valid JSON');
            return;
        }
        var schema = String(spec.$schema || '');
        var lite = schema.indexOf('vega-lite') >= 0
            || (schema.length === 0 && (spec.encoding || spec.mark));
        // A bar chart with two categories is 90 pixels wide on its own, which is
        // unreadable on a phone: give it the width it has to fill.
        if (lite && spec.width == null) {
            var room = (box.clientWidth || window.innerWidth || 360) - 32;
            spec.width = Math.max(200, Math.min(720, room));
        }
        var full;
        try {
            full = (lite && window.vegaLite) ? window.vegaLite.compile(spec).spec : spec;
        } catch (e2) {
            full = spec;
        }
        var view;
        try {
            view = new window.vega.View(window.vega.parse(full), { renderer: 'none' });
        } catch (e3) {
            message(box, lang, reason(e3));
            return;
        }
        view.toSVG().then(function (svg) {
            finish(box, svg, lang);
            try { view.finalize(); } catch (e4) { }
        }, function (e5) {
            message(box, lang, reason(e5));
        });
    }

    /* ---------------------------------------------------------------- driver */

    function run() {
        var nodes = document.querySelectorAll('.mu-diagram[data-lang]');
        for (var i = 0; i < nodes.length; i++) {
            var box = nodes[i];
            if (box.getAttribute('data-rendered')) {
                // a previous attempt failed before its engine was there: try again
                if (box.getAttribute('data-rendered') !== 'failed') continue;
                reset(box);
            }
            var lang = String(box.getAttribute('data-lang') || '').toLowerCase();
            var pre = box.querySelector('.mu-src');
            var src = pre ? pre.textContent : '';
            if (!src || !/\S/.test(src)) {
                box.setAttribute('data-rendered', 'empty');
                continue;
            }
            box.setAttribute('data-rendered', 'pending');
            if (lang === 'graphviz') drawGraphviz(box, src, lang);
            else if (lang === 'plantuml') drawPlantuml(box, src, lang);
            else if (lang === 'vega') drawVega(box, src, lang);
            else box.setAttribute('data-rendered', 'ignored');
        }
    }

    /* The page decides when to draw: its bootstrap first makes sure every engine it needs
       is actually present (a script tag can fail to load, and the page then gets the file
       over the JavaScript bridge instead), and only then calls this. Running on our own
       would draw with whatever happened to be loaded and leave a permanent error note
       behind. The variables are exposed so a caller can draw again after a change. */
    window.muDiagrams = run;
    if (window.MU_DIAGRAMS_AUTORUN !== false && document.querySelector('.mu-diagram')) {
        run();
    }
})();
