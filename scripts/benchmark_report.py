#!/usr/bin/env python3
"""
Parse AndroidX Benchmark JSON output and generate a Markdown comparison report.

Usage:
    python3 scripts/benchmark_report.py [path_to_json]

If no path is given, searches the default Gradle output directory.
"""
import json
import sys
from collections import defaultdict
from pathlib import Path


def find_benchmark_json() -> Path | None:
    """Find the benchmark JSON in the default Gradle output directory."""
    base = Path("microbenchmark/build/outputs/connected_android_test_additional_output")
    if not base.exists():
        return None
    # Look for files containing benchmark data
    for f in sorted(base.rglob("*benchmarkData.json"), reverse=True):
        try:
            data = json.loads(f.read_text())
            if "benchmarks" in data:
                return f
        except (json.JSONDecodeError, KeyError):
            continue
    # Fallback: any JSON with benchmarks key
    for f in sorted(base.rglob("*.json"), reverse=True):
        try:
            data = json.loads(f.read_text())
            if "benchmarks" in data:
                return f
        except (json.JSONDecodeError, KeyError):
            continue
    return None


def parse_benchmark_file(path: Path) -> tuple[list[dict], dict]:
    """Parse benchmark JSON and return structured results + context."""
    data = json.loads(path.read_text())

    results = []
    for bench in data.get("benchmarks", []):
        name = bench["name"]
        class_name = bench["className"].split(".")[-1]
        metrics = bench.get("metrics", {})
        time_ns = metrics.get("timeNs", {})

        results.append({
            "class": class_name,
            "name": name,
            "median_ns": time_ns.get("median", 0),
            "min_ns": time_ns.get("minimum", 0),
            "max_ns": time_ns.get("maximum", 0),
            "runs": time_ns.get("runs", []),
            "iterations": bench.get("repeatIterations", 0),
            "warmup": bench.get("warmupIterations", 0),
        })
    return results, data.get("context", {})


def ns_to_ms(ns: float) -> float:
    return ns / 1_000_000


def generate_markdown(results: list[dict], context: dict) -> str:
    """Generate a Markdown report from benchmark results."""
    lines = []
    lines.append("# Benchmark Results\n")

    # Device info
    build = context.get("build", {})
    lines.append("## Device Info\n")
    lines.append(f"| Property | Value |")
    lines.append(f"|----------|-------|")
    lines.append(f"| Device | {build.get('brand', '?')} {build.get('model', '?')} |")
    lines.append(f"| API Level | {build.get('version', {}).get('sdk', '?')} |")
    lines.append(f"| CPU Cores | {context.get('cpuCoreCount', '?')} |")
    lines.append(f"| CPU Max Freq | {context.get('cpuMaxFreqHz', 0) / 1_000_000_000:.2f} GHz |")
    lines.append(f"| CPU Locked | {context.get('cpuLocked', '?')} |")
    lines.append(f"| RAM | {context.get('memTotalBytes', 0) / (1024**3):.1f} GB |")
    lines.append("")

    # Group by class
    grouped = defaultdict(list)
    for r in results:
        grouped[r["class"]].append(r)

    # Friendly class names
    class_descriptions = {
        "TextMateHighlightBenchmark": "TextMate — Code Highlighting",
        "TextMateGrammarLoadBenchmark": "TextMate — Grammar & Theme Loading",
        "ShikiAnnotationBenchmark": "Shiki — AnnotatedString Building",
        "ComposeHighlightBenchmark": "Compose Highlight — WebView JS Bridge",
    }

    for class_name, benchmarks in grouped.items():
        title = class_descriptions.get(class_name, class_name)
        lines.append(f"## {title}\n")
        lines.append("| Benchmark | Median (ms) | Min (ms) | Max (ms) | Iterations |")
        lines.append("|-----------|:-----------:|:-------:|:-------:|:---------:|")
        for b in sorted(benchmarks, key=lambda x: x["median_ns"]):
            lines.append(
                f"| `{b['name']}` "
                f"| {ns_to_ms(b['median_ns']):.2f} "
                f"| {ns_to_ms(b['min_ns']):.2f} "
                f"| {ns_to_ms(b['max_ns']):.2f} "
                f"| {b['iterations']} |"
            )
        lines.append("")

    # Cross-library comparison summary (if all 3 have Kotlin benchmarks)
    kotlin_results = {}
    for r in results:
        if "kotlin" in r["name"].lower() and "small" in r["name"].lower():
            kotlin_results[r["class"]] = r
        elif "kotlin" in r["name"].lower() and "bothThemes" in r["name"] and "large" not in r["name"].lower():
            kotlin_results[r["class"]] = r

    if len(kotlin_results) >= 2:
        lines.append("## Cross-Library Comparison (Kotlin, small sample)\n")
        lines.append("| Library | Median (ms) | Notes |")
        lines.append("|---------|:-----------:|-------|")
        for cls, r in sorted(kotlin_results.items(), key=lambda x: x[1]["median_ns"]):
            lib_name = class_descriptions.get(cls, cls).split(" — ")[0]
            lines.append(f"| {lib_name} | {ns_to_ms(r['median_ns']):.2f} | `{r['name']}` |")
        lines.append("")

    lines.append("---\n")
    lines.append("*Generated by `scripts/benchmark_report.py`*\n")

    return "\n".join(lines)


def main():
    if len(sys.argv) > 1:
        path = Path(sys.argv[1])
    else:
        path = find_benchmark_json()

    if path is None or not path.exists():
        print("ERROR: No benchmark JSON found.", file=sys.stderr)
        print("", file=sys.stderr)
        print("Run benchmarks first:", file=sys.stderr)
        print("  ./gradlew :microbenchmark:connectedReleaseAndroidTest", file=sys.stderr)
        print("", file=sys.stderr)
        print("Or specify the JSON path directly:", file=sys.stderr)
        print("  python3 scripts/benchmark_report.py path/to/benchmarkData.json", file=sys.stderr)
        sys.exit(1)

    print(f"Reading: {path}", file=sys.stderr)
    results, context = parse_benchmark_file(path)

    if not results:
        print("ERROR: No benchmark results found in the JSON file.", file=sys.stderr)
        sys.exit(1)

    report = generate_markdown(results, context)

    # Print to stdout
    print(report)

    # Also write to file
    output_path = Path("BENCHMARK_RESULTS.md")
    output_path.write_text(report)
    print(f"\nReport written to {output_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
