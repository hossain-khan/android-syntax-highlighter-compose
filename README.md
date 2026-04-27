# Android Syntax Highlighter (Compose)

An Android app that showcases three different **syntax highlighting** approaches for displaying code beautifully in Jetpack Compose:

1. ☁️ **Server-driven** — a hosted [Shiki Token Service](https://syntax-highlight.gohk.xyz) tokenizes code on the server and returns per-token colors; the app just renders them.
2. 📴 **On-device (TextMate)** — [kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) applies TextMate grammars and themes locally with no network required.
3. 🌐 **On-device (Highlight.js)** — [compose-highlight](https://github.com/hossain-khan/android-compose-highlight) runs [Highlight.js](https://highlightjs.org/) inside a hidden WebView and converts the tokenized HTML output to a native `AnnotatedString` — 190+ languages, no grammar files to maintain.

All approaches produce a Compose `AnnotatedString` and share the same code samples and UI structure, making it easy to compare their output, performance, and trade-offs side by side.

## Features

- ☁️ **Server-driven highlighting** via [Shiki Token Service](https://syntax-highlight.gohk.xyz) — no grammar files on device
- 📴 **On-device highlighting** via [kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) — fully offline, zero network calls
- 🌐 **On-device highlighting** via [compose-highlight](https://github.com/hossain-khan/android-compose-highlight) — Highlight.js in a hidden WebView, 190+ languages, no grammar files to maintain
- 🆚 **Side-by-side comparison** — compare Shiki and TextMate output, performance, and device footprint together
- 🌗 **Dark & light theme support** on all screens
- 🔤 **Multiple languages** — Kotlin, Python, JSON, JavaScript
- 🎭 **Multiple themes** — GitHub, One Dark Pro, Dracula (Shiki); VS Dark+/Light+, One Dark Pro/Quiet Light, Monokai/Solarized Light (TextMate)
- 📊 **Performance metrics** — network request time, total time, on-device tokenization time, line/char counts
- 📋 **Copy to clipboard** on all screens
- 🔄 **Refresh** (Shiki) — re-trigger the network request in one tap
- 📋 **Plain-text fallback** — raw code shown when the Shiki server is unavailable

### Demo Screenshots

| Shiki Token Service | Native Kotlin TextMate | Compare Both | Compose Highlight (Highlight.js) |
| ---- | ---- | ---- | ---- |
| <img width="1008" height="2244" alt="Image" src="https://github.com/user-attachments/assets/cd0d4583-25be-4c74-9db0-afdc12872016" /> | <img width="1008" height="2244" alt="Image" src="https://github.com/user-attachments/assets/51968c3f-3feb-47cb-9746-0614c2cb5761" /> | <img width="1008" height="2244" alt="Image" src="https://github.com/user-attachments/assets/15d51596-7a58-4592-843d-350d246e8a07" /> | <img width="1008" height="2244" alt="Image" src="https://github.com/user-attachments/assets/fc4a7bc4-dfd8-4f04-a8e5-de0b11a55da2" /> |




## How it Works

### ☁️ Approach 1: Server-Driven Highlighting (Shiki)

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

### 📴 Approach 2: On-Device Highlighting (TextMate)

[kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) is bundled as local JARs. Grammar files (`.tmLanguage.json`) and theme files are shipped in `assets/`. At runtime:

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

### 🌐 Approach 3: On-Device Highlighting (compose-highlight / Highlight.js)

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
compose-highlight = { group = "com.github.hossain-khan", name = "android-compose-highlight", version = "0.4.0" }
```

Distributed via [JitPack](https://jitpack.io/#hossain-khan/android-compose-highlight).

---

## Tech Stack

| Layer | Library |
|---|---|
| UI | [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material 3 |
| Architecture | [Circuit UDF](https://github.com/slackhq/circuit) (presenter + UI composable) |
| Dependency Injection | [Metro](https://zacsweers.github.io/metro/) |
| Server highlighting | [Shiki Token Service SDK](https://github.com/hossain-khan/shiki-token-service) (Ktor-based) |
| On-device highlighting (TextMate) | [kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) (local JARs) |
| On-device highlighting (Highlight.js) | [compose-highlight](https://github.com/hossain-khan/android-compose-highlight) (WebView + Highlight.js) |
| Networking | Ktor (via Shiki SDK) |
| Min SDK | 28 (Android 9) |

## Project Structure

```
app/src/main/java/dev/hossain/syntaxhighlight/
├── MainActivity.kt                      # Entry point; sets up Circuit navigation stack
├── SyntaxHighlightApp.kt                # Application class; creates Metro app graph and schedules background work
├── circuit/
│   ├── home/
│   │   └── HomeScreen.kt                # Home screen — lists the four highlighting approaches
│   ├── shiki/
│   │   ├── ShikiHighlightScreen.kt      # ☁️ Shiki demo: presenter + UI composable
│   │   └── ShikiRenderUtils.kt          # Builds AnnotatedString from Shiki dual-theme token response
│   ├── textmate/
│   │   └── TextMateHighlightScreen.kt   # 📴 TextMate demo: loads grammars/themes, tokenizes on-device
│   ├── comparison/
│   │   └── ComparisonScreen.kt          # Side-by-side Shiki vs TextMate comparison with metrics
│   ├── composehighlight/
│   │   └── ComposeHighlightScreen.kt    # 🌐 Compose Highlight demo: Highlight.js via WebView, 190+ languages
│   └── overlay/
│       └── AppInfoOverlay.kt            # Bottom-sheet overlay showing app information (example)
├── data/
│   ├── samples/
│   │   └── CodeSamples.kt               # Hardcoded Kotlin/Python/JSON/JS snippets
│   └── shiki/
│       ├── ShikiRepository.kt           # Interface
│       └── ShikiRepositoryImpl.kt       # Wraps ShikiClient, calls /highlight/dual
├── di/
│   ├── AppGraph.kt                      # Root Metro dependency graph (AppScope)
│   ├── AppWorkerFactory.kt              # Custom WorkerFactory for Metro-injected workers
│   ├── ActivityKey.kt                   # Map key annotation for Activity multibinding
│   ├── CircuitProviders.kt              # Metro bindings for Circuit (presenter/UI factories)
│   ├── ComposeAppComponentFactory.kt    # AppComponentFactory enabling Activity constructor injection
│   └── WorkerKey.kt                     # Map key annotation for Worker multibinding
└── work/
    └── SampleWorker.kt                  # Example CoroutineWorker with assisted injection

app/src/main/assets/
├── grammars/                            # TextMate grammar files (.tmLanguage.json)
└── themes/                              # TextMate theme files
                                         #   dark_vs.json (base) + dark_plus.json, one_dark_pro.json, monokai.json (overlays)
                                         #   light_vs.json (base) + light_plus.json, quiet_light.json, solarized_light.json (overlays)

app/libs/
├── core.jar                             # kotlin-textmate core library
└── compose-ui-release.aar               # kotlin-textmate Compose UI bindings
```

## Getting Started

1. Clone the repo and open in Android Studio
2. Build and run — all four screens work out of the box:
   - **Shiki** uses the hosted service at `https://syntax-highlight.gohk.xyz`
   - **TextMate** runs fully on-device using bundled grammar/theme assets
   - **Compose Highlight** runs fully on-device using Highlight.js inside a hidden WebView
3. To point Shiki at your own service instance, update `SHIKI_BASE_URL` in `ShikiRepositoryImpl.kt`

```bash
# Build debug APK
./gradlew assembleDebug

# Format Kotlin code
./gradlew formatKotlin
```

## CI / GitHub Actions

| Workflow | Trigger |
|---|---|
| `android.yml` | Push / PR to `main` — compiles and runs lint |
| `android-lint.yml` | Push / PR — Android lint checks |
| `android-release.yml` | Tag push — builds signed release APK |
| `diffuse.yml` | PR — APK size diff report |
| `test-keystore-apk-signing.yml` | Manual (`workflow_dispatch`) — validates keystore configuration |

For production releases, configure these GitHub secrets:
- `KEYSTORE_BASE64` — base64-encoded keystore file
- `KEYSTORE_PASSWORD` — keystore password
- `KEY_ALIAS` — key alias


---

> [!NOTE]
> This project was developed with the assistance of AI coding agents (GitHub Copilot).
> Code, architecture, tests, and documentation were generated or refined through
> AI-assisted pair programming. Review accordingly before using in production.
