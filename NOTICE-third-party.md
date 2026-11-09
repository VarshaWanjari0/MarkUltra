# Third-party components bundled in MarkUltra

Everything below ships inside the APK as an asset or a jar and runs offline. Nothing is
downloaded at runtime.

## JavaScript, in `app/src/main/assets/`

| File | Project | Version | Licence |
|---|---|---|---|
| `katex.min.js`, `katex.css`, `fonts/*.woff2` | [KaTeX](https://katex.org) | 0.16.9 | MIT |
| `mhchem.min.js` (KaTeX contrib extension) | [mhchem for KaTeX](https://github.com/mhchem/mhchem?tab=readme-ov-file#katex) | 0.16.9 | MIT |
| `mermaid.min.js` | [Mermaid](https://mermaid.js.org) | 10.9.1 | MIT |
| `viz-global.js` | [Viz.js](https://github.com/hpcc-systems/visualization) (Graphviz compiled to WebAssembly) | @viz-js/viz as shipped by @plantuml/core | MIT; Graphviz itself is EPL-1.0 |
| `plantuml.js` | [PlantUML](https://plantuml.com) compiled to JavaScript with [TeaVM](https://teavm.org) (`@plantuml/core` 1.2026.9) | 1.2026.9 | MIT |
| `vega.min.js` | [Vega](https://vega.github.io/vega/) | 5.x | BSD-3-Clause |
| `vega-lite.min.js` | [Vega-Lite](https://vega.github.io/vega-lite/) | 5.x | BSD-3-Clause |
| `diagrams.js` | written for MarkUltra | - | same licence as the project |

`plantuml.js` is the package's ES module with its single `export { render, renderToString }`
statement replaced by `window.PlantUML = { render, renderToString }`, so it can be loaded
from `file:///android_asset/` as a classic script: a WebView does not allow module
imports over `file:`.

### Transpilation

The five engine bundles above are transpiled with Babel (`@babel/preset-env`, target
`chrome: 74`, the WebView that shipped with Android 10) before being placed in `assets/`.
The libraries are unmodified in behaviour and version; only modern syntax that an older
WebView cannot parse - optional chaining, nullish coalescing, class fields, logical
assignment - is lowered. No polyfill is added: every runtime feature these engines need
(Promise, Map, Set, Proxy, WebAssembly, async functions) exists in that engine already.

Unlowered, Vega-Lite 5 requires ES2022 and Viz requires ES2021; a WebView older than
Chrome 94 rejects the whole file with a syntax error and draws nothing, which is what a
"diagram that does not work" actually was.

## Java, resolved from Maven Central and Google Maven

flexmark-java 0.64.8 and its extensions (BSD-2-Clause), AndroidX appcompat / material /
core (Apache-2.0), jsoup (MIT), jsr305 and error_prone_annotations (Apache-2.0), Expo /
emoji2 (Apache-2.0). See `app/build.gradle` for the exact coordinates and `README.md`
section 4 for the measured download size.
