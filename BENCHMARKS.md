# Benchmarks

This project includes an [AndroidX Microbenchmark](https://developer.android.com/jetpack/androidx/releases/benchmark) module (`:microbenchmark`) that measures the performance of all three syntax highlighting libraries.

## What's Benchmarked

| Class | Library | What it Measures |
|-------|---------|-----------------|
| `TextMateHighlightBenchmark` | kotlin-textmate | `CodeHighlighter.highlight(code)` — CPU tokenization per language and code size |
| `TextMateGrammarLoadBenchmark` | kotlin-textmate | Grammar parsing and theme loading from assets (one-time startup cost) |
| `ShikiAnnotationBenchmark` | Shiki SDK | `buildAnnotatedString` from token data — the CPU cost of converting tokens to `AnnotatedString` |
| `ComposeHighlightBenchmark` | compose-highlight | `HighlightEngine.highlightBothThemes()` — full WebView JS round-trip |

### Benchmark Dimensions

- **Language**: Kotlin, Python, JSON, JavaScript
- **Code size**: small (~25 lines), medium (~100 lines), large (~1000 lines)
- **Theme**: Dark (VS Dark+, One Dark Pro, Monokai), Light (VS Light+)

## Running Benchmarks

### On a Physical Device (recommended)

For accurate results, benchmarks must run on a physical device with a non-debuggable build:

```bash
./gradlew :microbenchmark:connectedReleaseAndroidTest
```

### Dry-Run on Emulator

To verify benchmarks compile and execute (results will not be accurate):

```bash
./gradlew :microbenchmark:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true
```

### Run a Single Benchmark Class

```bash
./gradlew :microbenchmark:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.hossain.benchmark.TextMateHighlightBenchmark
```

## Generating the Report

After running benchmarks, generate a Markdown comparison table:

```bash
python3 scripts/benchmark_report.py
```

This reads the JSON output from:
```
microbenchmark/build/outputs/connected_android_test_additional_output/
```

And writes `BENCHMARK_RESULTS.md` at the project root.

You can also pass a specific JSON file:
```bash
python3 scripts/benchmark_report.py path/to/benchmarkData.json
```

## Output Format

The AndroidX Benchmark library produces a JSON file with per-benchmark timing stats (median, min, max, iterations, warmup). The report script parses this and produces a table grouped by library.

## Notes

- **Physical device**: Emulator results are unreliable and suppressed by default. Use a real device for publishable numbers.
- **CPU clocks**: For best stability on rooted devices, lock clocks with `./gradlew :microbenchmark:lockClocks`.
- **WebView benchmarks** (`ComposeHighlightBenchmark`): These involve WebView JS bridge calls and are inherently noisier than pure CPU benchmarks. The first run includes WebView initialization; subsequent iterations reflect steady-state performance.
- **Battery**: Ensure at least 25% battery to avoid throttling warnings.
