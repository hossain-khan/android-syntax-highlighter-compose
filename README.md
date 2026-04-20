# Android Syntax Highlighter (Compose)

An Android app that showcases two different **syntax highlighting** approaches for displaying code beautifully in Jetpack Compose:

1. ☁️ **Server-driven** — a hosted [Shiki Token Service](https://syntax-highlight.gohk.xyz) tokenizes code on the server and returns per-token colors; the app just renders them.
2. 📴 **On-device (offline)** — [kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) applies TextMate grammars and themes locally with no network required.

Both approaches produce a Compose `AnnotatedString` and share the same code samples and UI structure, making it easy to compare their output, performance, and trade-offs side by side.

## Features

- ☁️ **Server-driven highlighting** via [Shiki Token Service](https://syntax-highlight.gohk.xyz) — no grammar files on device
- 📴 **On-device highlighting** via [kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) — fully offline, zero network calls
- 🌗 **Dark & light theme support** on both screens
- 🔤 **Multiple languages** — Kotlin, Python, JSON, JavaScript
- 🎭 **Multiple themes** — GitHub, One Dark Pro, Dracula (Shiki); VS Dark/Light (TextMate)
- 📊 **Performance metrics** — network request time, total time, on-device tokenization time, line/char counts
- 📋 **Copy to clipboard** on both screens
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
ThemeReader.readTheme(assetStreams)      ← dark_vs + dark_plus / light_vs + light_plus
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
| Grammars | `kotlin.tmLanguage.json`, `JSON.tmLanguage.json`, `JavaScript.tmLanguage.json` |
| Dark themes | `dark_vs.json` + `dark_plus.json` |
| Light themes | `light_vs.json` + `light_plus.json` |

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
├── MainActivity.kt                      # Entry point, sets up Circuit
├── circuit/
│   ├── home/
│   │   └── HomeScreen.kt                # Home screen — lists both highlighting approaches
│   ├── shiki/
│   │   └── ShikiHighlightScreen.kt      # ☁️ Shiki demo: presenter + UI + AnnotatedString builder
│   └── textmate/
│       └── TextMateHighlightScreen.kt   # 📴 TextMate demo: loads grammars, tokenizes on-device
├── data/
│   ├── samples/
│   │   └── CodeSamples.kt               # Hardcoded Kotlin/Python/JSON/JS snippets
│   └── shiki/
│       ├── ShikiRepository.kt           # Interface
│       └── ShikiRepositoryImpl.kt       # Wraps ShikiClient, calls /highlight/dual
└── di/                                  # Metro DI components

app/src/main/assets/
├── grammars/                            # TextMate grammar files (.tmLanguage.json)
└── themes/                              # TextMate theme files (dark_vs, dark_plus, light_vs, light_plus)

app/libs/
├── core.jar                             # kotlin-textmate core library
└── compose-ui-release.aar               # kotlin-textmate Compose UI bindings
```

## Getting Started

1. Clone the repo and open in Android Studio
2. Build and run — both screens work out of the box:
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

For production releases, configure these GitHub secrets:
- `KEYSTORE_BASE64` — base64-encoded keystore file
- `KEYSTORE_PASSWORD` — keystore password
- `KEY_ALIAS` — key alias
