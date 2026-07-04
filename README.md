[![Android CI](https://github.com/hossain-khan/android-syntax-highlighter-compose/actions/workflows/android.yml/badge.svg)](https://github.com/hossain-khan/android-syntax-highlighter-compose/actions/workflows/android.yml) [![](https://androidweekly.net/issues/issue-725/badge)](https://androidweekly.net/issues/issue-725)

# Android Syntax Highlighter (Compose)

An Android app that showcases three different **syntax highlighting** approaches for displaying code beautifully in Jetpack Compose:

1. ☁️ **Server-driven** — a hosted [Shiki Token Service](https://github.com/hossain-khan/shiki-token-service) tokenizes code on the server and returns per-token colors; the app just renders them.
2. 📴 **On-device (TextMate)** — [kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) applies TextMate grammars and themes locally with no network required.
3. 🌐 **On-device (Highlight.js)** — [compose-highlight](https://github.com/hossain-khan/android-compose-highlight) runs [Highlight.js](https://highlightjs.org/) inside a hidden WebView and converts the tokenized HTML output to a native `AnnotatedString` — 190+ languages, no grammar files to maintain.

All approaches produce a Compose `AnnotatedString` and share the same code samples and UI structure, making it easy to compare their output, performance, and trade-offs side by side.

## Features

- 🆚 **Side-by-side comparison** — compare all three approaches (output, performance, device footprint) together
- 🌗 **Dark & light theme support** on all screens
- 🔤 **Multiple languages** — Kotlin, Python, JSON, JavaScript
- 🎭 **Multiple themes** — GitHub, One Dark Pro, Dracula (Shiki); VS Dark+/Light+, One Dark Pro/Quiet Light, Monokai/Solarized Light (TextMate)
- 📊 **Performance metrics** — network request time, total time, on-device tokenization time, line/char counts
- 📋 **Copy to clipboard** on all screens
- 🔄 **Refresh** (Shiki) — re-trigger the network request in one tap
- 📋 **Plain-text fallback** — raw code shown when the Shiki server is unavailable

### Demo Screenshots

| Shiki Token Service | Native Kotlin TextMate | Compose Highlight | Compare All |
| ---- | ---- | ---- | ---- |
| <img width="1008" height="2244" alt="Image" src="https://github.com/user-attachments/assets/cd0d4583-25be-4c74-9db0-afdc12872016" /> | <img width="1008" height="2244" alt="Image" src="https://github.com/user-attachments/assets/51968c3f-3feb-47cb-9746-0614c2cb5761" /> | <img width="1008" height="2244" alt="Screenshot_20260427_074442" src="https://github.com/user-attachments/assets/afdbb599-2846-4c2b-b50d-539cc3b78383" /> | <img width="1008" height="2244" alt="Image" src="https://github.com/user-attachments/assets/15d51596-7a58-4592-843d-350d246e8a07" /> |

## Highlighting Approaches

### ☁️ Shiki (Server-Driven)

Code is sent to a hosted [Shiki Token Service](https://github.com/hossain-khan/shiki-token-service) which returns per-token dark/light hex colors. The app builds a Compose `AnnotatedString` directly from the token list — no grammar files needed on device. Metrics include network round-trip time and total rendering time.

### 📴 TextMate (On-Device)

[kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) tokenizes code using `.tmLanguage.json` grammar files and VS Code-style theme files bundled in `assets/`. Fully offline — all tokenization runs on `Dispatchers.Default` with no network calls. Metrics show CPU tokenization time.

### 🌐 Highlight.js (On-Device WebView)

[compose-highlight](https://github.com/hossain-khan/android-compose-highlight) runs [Highlight.js](https://highlightjs.org/) inside a hidden `WebView`. Supports 190+ languages with no grammar files to maintain. A single JS round-trip produces both dark and light `AnnotatedString` outputs simultaneously. Metrics show the WebView JS round-trip time.

→ [Detailed implementation notes](HOW_IT_WORKS.md)

## Quick Comparison

| Aspect | Shiki REST API | TextMate | Compose Highlight |
|--------|-------|----------|-------------------|
| **APK Impact** | ~20 KB | **+1.6 MB** | ✅ **+628 KB** |
| **Grammer/Rendering** | <img src="microbenchmark/logos/api-interface.svg" height="18"> Server | <img src="microbenchmark/logos/jetpack-compose-logo.svg" height="18"> Pure Compose | <img src="microbenchmark/logos/chromium-logo.svg" height="18"> WebView |
| **Offline** | ❌ Network required | ✅ Fully offline | ✅ Fully offline |
| **Languages** | Unlimited* | ✅ **350+*** (any VS Code grammar) | 190+ (bundled) |
| **Compose-native** | ❌ | ✅ | ✅ |
| **Setup** | Add 1 lib + server URL | Add 1 lib <br> + theme & grammars ~1-3KB each | Add 1 lib <br> + theme ~0.2KB each |
| **Performance** (avg)¹ | **n/a** | ✅ ~8.4ms | ❌ ~120ms (cold) <br> ~6.4ms (warm) |

* TextMate can load any TextMate/VS Code grammar format; 250+ are available in the VS Code ecosystem.

¹ Median time to highlight small code sample (Pixel 9 Pro XL + Galaxy S24 Ultra average). Shiki performance depends on network latency; shown values are for on-device processing only (TextMate & Compose Highlight).

**→ Full details & benchmarks:** 
- [APK Size Impact Analysis](microbenchmark/apk-impact-analysis/)
- [Benchmark Results — Pixel 9 Pro XL](microbenchmark/results/BENCHMARK_RESULTS_P9PXL.md)
- [Benchmark Results — Galaxy S24 Ultra](microbenchmark/results/BENCHMARK_RESULTS_S24ULTRA.md)

## Tech Stack

| Layer | Library |
|---|---|
| UI | [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material 3 |
| Architecture | [Circuit UDF](https://github.com/slackhq/circuit) (presenter + UI composable) |
| Dependency Injection | [Metro](https://zacsweers.github.io/metro/) |
| Server highlighting | [Shiki Token Service SDK](https://github.com/hossain-khan/shiki-token-service) (Ktor-based) |
| On-device highlighting (TextMate) | [kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) (Maven Central) |
| On-device highlighting (Highlight.js) | [compose-highlight](https://github.com/hossain-khan/android-compose-highlight) (WebView + Highlight.js) |
| Networking | Ktor (via Shiki SDK) |
| Min SDK | 28 (Android 9) |

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

