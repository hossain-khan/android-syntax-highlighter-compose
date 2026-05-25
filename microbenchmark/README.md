# Microbenchmark Module

Performance benchmarks for the three syntax highlighting approaches using [AndroidX Benchmark](https://developer.android.com/studio/profile/benchmark).

## Benchmarks Included

- **Compose Highlight** — WebView JS bridge performance (both themes)
- **Shiki** — AnnotatedString building from token responses (light/dark)
- **TextMate** — Grammar/theme loading and code highlighting (multiple grammars/themes)

## Running Benchmarks

### Prerequisites

- Android device or emulator (API 28+)
- Device should be in a stable state (no other apps running)
- For accurate results, **use a physical device**; emulator results can be skewed

### Run All Benchmarks

```bash
./gradlew :microbenchmark:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

### Run Specific Benchmark

```bash
./gradlew :microbenchmark:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.hossain.benchmark.SyntaxHighlightBenchmark
```

### Real Device (Recommended)

For production-quality results, connect a physical device and run:

```bash
./gradlew :microbenchmark:connectedAndroidTest
```

## Output

Results are generated as:
- **JSON profile** — `microbenchmark/build/outputs/connected_android_test/...`
- **Studio Profiler traces** — Available in Android Studio's Profiler integration
- **Markdown report** — Generate manually from JSON (see scripts/)

### View Results in Android Studio

1. Run benchmarks: `./gradlew :microbenchmark:connectedAndroidTest`
2. Android Studio → Profiler → Select the benchmark run
3. Examine flame charts and method traces

## Benchmark Results

Latest results are stored in:
- [BENCHMARK_RESULTS_P9PXL.md](results/BENCHMARK_RESULTS_P9PXL.md) — Pixel 9 Pro XL results

## Notes

- Tests use `release` buildType for accurate timing (debuggable builds include debug symbols and instrumentation)
- `androidx.benchmark.suppressErrors=EMULATOR` allows dry-runs on emulators (remove for real device runs)
- Each benchmark runs multiple iterations and reports median/min/max timings

## References

- [AndroidX Benchmark Documentation](https://developer.android.com/studio/profile/benchmark)
- [Benchmark Migration Guide](https://developer.android.com/studio/profile/benchmark/migration)
