# How It Works

Deep-dive into how each of the three syntax highlighting approaches is implemented.

---

## ☁️ Approach 1: Server-Driven Highlighting (Shiki)

The app calls the `/highlight/dual` endpoint, sending source code, language, and a dark+light theme pair. The service returns a 2D array of tokens, each with:

- `text` — the token's text content
- `darkColor` — hex color for dark theme (e.g. `#F97583`)
- `lightColor` — hex color for light theme (e.g. `#D73A49`)

The app builds a Compose `AnnotatedString` by applying `SpanStyle(color = …)` to each token. No grammar files are needed on the device.

```
Source code
    │
    ▼
POST /highlight/dual  ──► Shiki Token Service
    │
    ▼
List<List<DualToken>>  (text + darkColor + lightColor per token)
    │
    ▼
buildAnnotatedString { SpanStyle per token }
    │
    ▼
Text(annotated, fontFamily = Monospace)
```

**Metrics shown:** ☁️ network request time · ⏱ total time (network + AnnotatedString build)

#### SDK

```kotlin
// gradle/libs.versions.toml
shiki-sdk = { group = "com.github.hossain-khan.shiki-token-service", name = "sdk-android", version = "sdk-1.0.5" }
```

Distributed via [JitPack](https://jitpack.io/#hossain-khan/shiki-token-service).

---

## 📴 Approach 2: On-Device Highlighting (TextMate)

[kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) is distributed via Maven Central. Grammar files (`.tmLanguage.json`) and theme files are shipped in `assets/`. At runtime:

1. Grammar and theme files are loaded from assets on a background thread (`Dispatchers.IO`)
2. `CodeHighlighter(grammar, theme).highlight(code)` tokenizes the source entirely on-device
3. The resulting `AnnotatedString` is rendered in a `Text` composable

No network connection is ever needed.

```
Source code
    │
    ▼
GrammarReader.readGrammar(assetStream)   ← .tmLanguage.json from assets/
ThemeReader.readTheme(assetStreams)      ← base theme (dark_vs / light_vs) + overlay theme
    │
    ▼
CodeHighlighter(grammar, theme).highlight(code)   ← pure CPU, no I/O
    │
    ▼
AnnotatedString (SpanStyle per token)
    │
    ▼
Text(annotated, fontFamily = Monospace)
```

**Metrics shown:** ⏱ on-device tokenization time (CPU only)

**Bundled assets:**

| Type | Files |
|---|---|
| Grammars | `kotlin.tmLanguage.json`, `python.tmLanguage.json`, `JSON.tmLanguage.json`, `JavaScript.tmLanguage.json` |
| Dark themes (base) | `dark_vs.json` |
| Dark themes (overlays) | `dark_plus.json`, `one_dark_pro.json`, `monokai.json` |
| Light themes (base) | `light_vs.json` |
| Light themes (overlays) | `light_plus.json`, `quiet_light.json`, `solarized_light.json` |

---

## 🌐 Approach 3: On-Device Highlighting (compose-highlight / Highlight.js)

[compose-highlight](https://github.com/hossain-khan/android-compose-highlight) embeds [Highlight.js](https://highlightjs.org/) in a hidden `WebView`. At runtime:

1. The library initializes a single `HighlightEngine` (shared across the app)
2. Source code is passed to the engine which runs a Highlight.js tokenization call inside the WebView
3. The tokenized HTML output is parsed and converted into a native Compose `AnnotatedString`
4. Theme switching between light and dark is instant — both variants are produced in a single JS round-trip via `rememberHighlightedCodeBothThemes`

No grammar files or theme assets need to be bundled. Highlight.js ships inside the library with support for 190+ languages.

```
Source code
    │
    ▼
HighlightEngine  ──► hidden WebView (Highlight.js)
    │
    ▼
Tokenized HTML  →  parse to token list (text + color)
    │
    ▼
buildAnnotatedString { SpanStyle per token }
    │
    ▼
Text(annotated, fontFamily = Monospace)
```

**Metrics shown:** ⏱ WebView JS round-trip time (ms)

#### SDK

```kotlin
// gradle/libs.versions.toml
compose-highlight = { group = "dev.hossain", name = "compose-highlight", version = "0.31.0" }
```

Distributed via [Maven Central](https://central.sonatype.com/artifact/dev.hossain/compose-highlight).
