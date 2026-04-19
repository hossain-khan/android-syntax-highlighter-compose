# Android Syntax Highlighter (Compose)

An Android app that showcases **syntax highlighting** approaches for displaying code beautifully in Jetpack Compose. The primary approach uses a **server-driven Shiki token service** — the backend tokenizes source code and returns per-token colors, and the app renders them as Compose `AnnotatedString`.

## Features

- 🎨 **Server-driven syntax highlighting** via [Shiki Token Service](https://syntax-highlight.gohk.xyz)
- 🌗 **Dark & light theme support** — dual-theme API call applies the right colors automatically
- 🔤 **Multiple languages** — Kotlin, Python, JSON, JavaScript
- 🎭 **Multiple themes** — GitHub, One Dark Pro, Dracula, and more
- 📊 **Request metrics** — shows API latency, source line count, and character count
- 📋 **Plain-text fallback** — raw code is always shown even when offline or the server is unavailable
- 🔄 **Retry on failure** with one tap

## How it Works

### Server-Driven Highlighting

The app calls the `/highlight/dual` endpoint on the Shiki Token Service, sending the source code, language, and a pair of themes (dark + light). The service returns a 2D array of tokens, each carrying:

- `text` — the token's text content
- `darkColor` — hex color for dark theme (e.g. `#F97583`)
- `lightColor` — hex color for light theme (e.g. `#D73A49`)

The app builds a Compose `AnnotatedString` by applying `SpanStyle(color = …)` to each token, then renders it in a monospace `Text` composable. No local parsing or grammar files are needed.

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
buildAnnotatedString { ... }
    │
    ▼
Text(annotated, fontFamily = Monospace)
```

### SDK

The app uses the official Android SDK for the Shiki Token Service:

```kotlin
// gradle/libs.versions.toml
shiki-sdk = { group = "com.github.hossain-khan.shiki-token-service", name = "sdk-android", version = "sdk-1.0.5" }
```

Distributed via [JitPack](https://jitpack.io/#hossain-khan/shiki-token-service).

## Tech Stack

| Layer | Library |
|---|---|
| UI | [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material 3 |
| Architecture | [Circuit UDF](https://github.com/slackhq/circuit) (presenter + UI composable) |
| Dependency Injection | [Metro](https://zacsweers.github.io/metro/) |
| Highlighting API | [Shiki Token Service SDK](https://github.com/hossain-khan/shiki-token-service) |
| Networking | OkHttp + Retrofit (via SDK) |
| Min SDK | 28 (Android 9) |

## Project Structure

```
app/src/main/java/dev/hossain/syntaxhighlight/
├── MainActivity.kt                  # Entry point, sets up Circuit
├── circuit/
│   ├── home/
│   │   └── HomeScreen.kt            # Home screen listing available approaches
│   └── shiki/
│       └── ShikiHighlightScreen.kt  # Shiki demo: presenter + UI + AnnotatedString builder
├── data/
│   ├── samples/
│   │   └── CodeSamples.kt           # Hardcoded Kotlin/Python/JSON/JS snippets
│   └── shiki/
│       ├── ShikiRepository.kt       # Interface
│       └── ShikiRepositoryImpl.kt   # Wraps ShikiClient, calls /highlight/dual
└── di/                              # Metro DI components
```

## Getting Started

1. Clone the repo and open in Android Studio
2. Build and run — the app works out of the box using the hosted Shiki service at `https://syntax-highlight.gohk.xyz`
3. To point at your own Shiki service instance, update `baseUrl` in `ShikiRepositoryImpl.kt`

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

