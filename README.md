# Android Syntax Highlighter (Compose)

An Android app that showcases two different **syntax highlighting** approaches for displaying code beautifully in Jetpack Compose:

1. ☁️ **Server-driven** — a hosted [Shiki Token Service](https://syntax-highlight.gohk.xyz) tokenizes code on the server and returns per-token colors; the app just renders them.
2. 📴 **On-device (offline)** — [kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) applies TextMate grammars and themes locally with no network required.

Both approaches produce a Compose `AnnotatedString` and share the same code samples and UI structure, making it easy to compare their output, performance, and trade-offs side by side.

## Features

- ☁️ **Server-driven highlighting** via [Shiki Token Service](https://syntax-highlight.gohk.xyz) — no grammar files on device
- 📴 **On-device highlighting** via [kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) — fully offline, zero network calls
- 🆚 **Side-by-side comparison** — compare Shiki and TextMate output, performance, and device footprint together
- 🌗 **Dark & light theme support** on all screens
- 🔤 **Multiple languages** — Kotlin, Python, JSON, JavaScript
- 🎭 **Multiple themes** — GitHub, One Dark Pro, Dracula (Shiki); VS Dark+/Light+, One Dark Pro/Quiet Light, Monokai/Solarized Light (TextMate)
- 📊 **Performance metrics** — network request time, total time, on-device tokenization time, line/char counts
- 📋 **Copy to clipboard** on all screens
- 🔄 **Refresh** (Shiki) — re-trigger the network request in one tap
- 📋 **Plain-text fallback** — raw code shown when the Shiki server is unavailable

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

## Tech Stack

| Layer | Library |
|---|---|
| UI | [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material 3 |
| Architecture | [Circuit UDF](https://github.com/slackhq/circuit) (presenter + UI composable) |
| Dependency Injection | [Metro](https://zacsweers.github.io/metro/) |
| Server highlighting | [Shiki Token Service SDK](https://github.com/hossain-khan/shiki-token-service) (Ktor-based) |
| On-device highlighting | [kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) (local JARs) |
| Networking | Ktor (via Shiki SDK) |
| Min SDK | 28 (Android 9) |

## Project Structure

```
app/src/main/java/dev/hossain/syntaxhighlight/
├── MainActivity.kt                      # Entry point; sets up Circuit navigation stack
├── SyntaxHighlightApp.kt                # Application class; creates Metro app graph and schedules background work
├── circuit/
│   ├── home/
│   │   └── HomeScreen.kt                # Home screen — lists the three highlighting approaches
│   ├── shiki/
│   │   ├── ShikiHighlightScreen.kt      # ☁️ Shiki demo: presenter + UI composable
│   │   └── ShikiRenderUtils.kt          # Builds AnnotatedString from Shiki dual-theme token response
│   ├── textmate/
│   │   └── TextMateHighlightScreen.kt   # 📴 TextMate demo: loads grammars/themes, tokenizes on-device
│   ├── comparison/
│   │   └── ComparisonScreen.kt          # Side-by-side Shiki vs TextMate comparison with metrics
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
2. Build and run — all three screens work out of the box:
   - **Shiki** uses the hosted service at `https://syntax-highlight.gohk.xyz`
   - **TextMate** runs fully on-device using bundled grammar/theme assets
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
