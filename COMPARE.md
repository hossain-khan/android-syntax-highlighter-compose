# Syntax Highlighting Approaches — Comparison

This app demonstrates three distinct syntax highlighting approaches for Jetpack Compose.
Each has a dedicated screen and a shared side-by-side [ComparisonScreen].

---

## How They Work

**Shiki Token Service** (`com.github.hossain-khan.shiki-token-service:sdk-android:sdk-1.0.5`)  
Server-driven highlighting via the [Shiki Token Service](https://syntax-highlight.gohk.xyz).
Code is sent in a single network request to the `/highlight/dual` endpoint, which runs
[Shiki](https://shiki.style/) on the server and returns per-token RGB colors for both a dark
and a light theme. The client builds an `AnnotatedString` from those tokens and renders it with
a monospace font — no grammar files or WebView needed on the device.

**kotlin-textmate** (`io.github.ivan-magda:kotlin-textmate-compose:0.1.0`)  
Ports Microsoft's vscode-textmate engine to Kotlin/JVM. Loads `.tmLanguage.json` grammar files
and VS Code JSON theme files from assets, then tokenizes code line-by-line using the Joni
(Java Oniguruma) regex engine — entirely in-process, no WebView involved. Output is an
`AnnotatedString` built directly from token scopes matched against theme rules.

**compose-highlight** (`dev.hossain:compose-highlight:0.24.0`)  
Runs [Highlight.js](https://highlightjs.org/) inside a single hidden WebView. Code is sent
over a JS bridge, tokenized in JavaScript, the resulting HTML is parsed via jsoup, and CSS
theme selectors are mapped to `SpanStyle`s to produce an `AnnotatedString`. All WebView
operations are suspend functions behind a `Mutex`.

---

## Side-by-Side Comparison

| Dimension | **Shiki Token Service** | **kotlin-textmate** | **compose-highlight** |
|---|---|---|---|
| **Engine location** | Remote server (Shiki on Node.js) | On-device Kotlin/JVM port of vscode-textmate | On-device Highlight.js in a hidden WebView |
| **Network required** | Yes (HTTP to token service) | No | No |
| **Grammar format** | Handled server-side | `.tmLanguage.json` (VS Code standard) | Built-in to Highlight.js bundle |
| **Language support** | 200+ (Shiki's built-in grammars) | 600+ (any VS Code grammar) | 190+ (bundled Highlight.js) |
| **Theme format** | Shiki/VS Code themes (server-side) | VS Code JSON (`tokenColors`) | Highlight.js CSS (any community theme) |
| **Custom themes** | Server configuration only | Load `.json` from assets | Load `.css` from assets, raw CSS, or `Map<selector, SpanStyle>` (Material 3 dynamic color support) |
| **Thread model** | Network I/O on `Dispatchers.IO` | CPU-bound; runs on `Dispatchers.Default` | WebView JS bridge; suspend + `Mutex` on main thread |
| **First-call latency** | Network RTT (variable; depends on connectivity) | Grammar load ~0.2–1.6 ms + highlight time (see benchmarks below) | WebView warm-up (~200 ms cold; pre-warmable) |
| **Offline support** | No | Yes | Yes |
| **APK size impact** | Minimal (SDK + HTTP client) | Grammar + theme JSON assets (user-controlled) | ~0.5 MB (Highlight.js bundle) |
| **WebView dependency** | None | None | Required (minSdk 24+) |
| **Top-level composable** | None (manual `Text` from `AnnotatedString`) | `CodeBlock` | `SyntaxHighlightedCode` (inside `HighlightThemeProvider`) |
| **Single-theme helper** | None | `rememberHighlightedCode(code, grammar, theme)` | `rememberHighlightedCode(code, language, theme)` |
| **Dual-theme helper** | `highlightDual(code, language, darkTheme, lightTheme)` (suspend) | None (manual: two `CodeHighlighter` calls) | `rememberHighlightedCodeBothThemes(code, language, lightTheme, darkTheme)` |
| **Engine-only API** | `ShikiClient.highlightDual(...)` | `CodeHighlighter(grammar, theme).highlight(code)` | `HighlightEngine.highlight(code, language, theme)` / `highlightBothThemes(...)` |
| **Line numbers** | Not built-in | Not built-in | `showLineNumbers = true` on `SyntaxHighlightedCode` |
| **Shared engine** | `ShikiClient` (stateless HTTP) | No provider concept; one `Grammar` per use | `HighlightThemeProvider` shares one WebView across the subtree |
| **Thread-safety** | Inherently safe (stateless HTTP) | `Grammar` is **not** thread-safe | `HighlightEngine` is safe via `Mutex` |
| **Timing metrics exposed** | `requestDurationMs`, `annotationDurationMs` | `measureTimedValue` wraps `CodeHighlighter.highlight()` | `HighlightTimings` (`jsBridge`, `jsonUnescape`, `htmlParse`, `treeWalk`, `themeParse`, `total`) |
| **Benchmarks published** | `buildAnnotatedString` only (AndroidX Microbenchmark; network RTT excluded) | Yes (AndroidX Microbenchmark, ms/snippet) | Yes (AndroidX Microbenchmark, ms/snippet) |
| **Current version (this app)** | `sdk-1.0.5` | `0.1.0` ✓ latest | `0.19.0` ✓ latest |

---

## Performance Benchmarks

Measured on a **Pixel 9 Pro XL (API 36, 8-core 3.10 GHz)** using AndroidX Microbenchmark. Full results: [`BENCHMARK_RESULTS_P9PXL.md`](microbenchmark/results/BENCHMARK_RESULTS_P9PXL.md).

### Highlighting Time by Code Size (Kotlin)

| Code Size | **kotlin-textmate** | **compose-highlight** | **Shiki** (`buildAnnotatedString` only) |
|-----------|:-------------------:|:---------------------:|:---------------------------------------:|
| Small (~25 lines) | 2.18 ms | 7.32 ms | 0.04 ms |
| Medium (~100 lines) | 10.11 ms | 14.33 ms | - |
| Large (~1000 lines) | 78.79 ms | 40.96 ms | 0.60 ms |

> **Shiki note**: the `buildAnnotatedString` step is measured client-side only; total latency includes network RTT to the token service.

> **compose-highlight note**: times reflect the full WebView JS bridge round-trip (`highlightBothThemes`); the WebView must be pre-warmed for steady-state numbers.

### Grammar & Theme Load Time (kotlin-textmate, one-time startup cost)

| Asset | Median (ms) |
|-------|:-----------:|
| JSON grammar | 0.16 |
| Kotlin grammar | 0.23 |
| Dark+ theme | 0.56 |
| Monokai theme | 0.64 |
| One Dark Pro theme | 1.42 |
| Python grammar | 1.53 |
| JavaScript grammar | 1.59 |

---

## When to Use Which

- **Shiki Token Service** — best when you control a backend and want zero on-device grammar/theme management. Server handles all Shiki versions and grammar updates automatically. Not suitable for offline or latency-sensitive use cases.
- **kotlin-textmate** — best for large files, CPU-bound workloads, or when you need the exact same grammar/theme fidelity as VS Code. No WebView overhead, no warm-up, fully offline.
- **compose-highlight** — best for quick drop-in integration, built-in line numbers, Material 3 dynamic color themes, and the breadth of the Highlight.js ecosystem with zero grammar file management. The dual-theme helper and per-stage timing metrics are first-class APIs.
