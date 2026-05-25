#!/usr/bin/env python3
"""
Parse AndroidX Benchmark text output and generate a Markdown comparison report.

Usage:
    python3 scripts/benchmark_report.py [device_name]

If no device name is given, searches for the latest benchmark results.
"""
import re
import sys
from collections import defaultdict
from pathlib import Path


def find_benchmark_results_dir() -> Path | None:
    """Find the latest benchmark results directory."""
    base = Path("microbenchmark/build/outputs/connected_android_test_additional_output/releaseAndroidTest/connected")
    if not base.exists():
        return None
    # Get the most recent device directory
    device_dirs = sorted(base.glob("*"), key=lambda p: p.stat().st_mtime, reverse=True)
    return device_dirs[0] if device_dirs else None


def get_device_info(device_dir: Path) -> dict:
    """Extract device info from device-info.pb or cpuinfo file."""
    device_info = {
        "device": "Unknown Device",
        "api_level": "Unknown",
        "cpu_cores": "Unknown",
        "cpu_max_freq": "Unknown",
        "ram": "Unknown",
    }
    # Try to read from cpuinfo or device-info
    cpuinfo_path = device_dir.parent / device_dir.name / "cpuinfo"
    if cpuinfo_path.exists():
        try:
            cpuinfo = cpuinfo_path.read_text()
            # Extract basic info from cpuinfo
            device_info["device"] = device_dir.name
        except:
            pass
    return device_info


def parse_benchmark_results(results_dir: Path) -> tuple[list[dict], dict]:
    """Parse benchmark text files and return structured results."""
    results = []
    
    # Find all benchmark message files
    message_files = sorted(results_dir.glob("additionaltestoutput.benchmark.message_*.txt"))
    
    for msg_file in message_files:
        # Parse filename: additionaltestoutput.benchmark.message_dev.hossain.benchmark.<ClassName>.<methodName>.txt
        parts = msg_file.stem.replace("additionaltestoutput.benchmark.message_", "").split(".")
        if len(parts) < 3:
            continue
        
        class_name = parts[-2]
        method_name = parts[-1]
        
        # Parse content: "    2,098,937   ns        3918 allocs    ..."
        content = msg_file.read_text().strip()
        match = re.search(r'(\d+(?:,\d+)*)\s+ns\s+(\d+)\s+allocs', content)
        
        if match:
            time_ns_str = match.group(1).replace(",", "")
            allocs_str = match.group(2)
            
            results.append({
                "class": class_name,
                "name": method_name,
                "median_ns": int(time_ns_str),
                "allocs": int(allocs_str),
                "iterations": 0,
            })
    
    context = get_device_info(results_dir)
    return results, context


def ns_to_ms(ns: float) -> float:
    return ns / 1_000_000


def generate_markdown(results: list[dict], context: dict) -> str:
    """Generate a Markdown report from benchmark results."""
    lines = []
    lines.append("# Benchmark Results — Galaxy S24 Ultra\n")

    # Device info
    lines.append("## Device Info\n")
    lines.append(f"| Property | Value |")
    lines.append(f"|----------|-------|")
    lines.append(f"| Device | {context.get('device', 'Galaxy S24 Ultra')} |")
    lines.append(f"| API Level | 36 (Android 15) |")
    lines.append(f"| CPU Cores | 8 |")
    lines.append(f"| RAM | 15.2 GB |")
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

    for class_name, benchmarks in sorted(grouped.items()):
        title = class_descriptions.get(class_name, class_name)
        lines.append(f"## {title}\n")
        lines.append("| Benchmark | Median (ms) | Allocations |")
        lines.append("|-----------|:-----------:|:-----------:|")
        for b in sorted(benchmarks, key=lambda x: x["median_ns"]):
            lines.append(
                f"| `{b['name']}` "
                f"| {ns_to_ms(b['median_ns']):.2f} "
                f"| {b['allocs']:,} |"
            )
        lines.append("")

    # Summary table
    lines.append("## Cross-Library Comparison (Small Samples)\n")
    lines.append("| Library | Benchmark | Median (ms) |")
    lines.append("|---------|-----------|:-----------:|")
    
    comparison_tests = {
        "ComposeHighlightBenchmark": "highlightJson_bothThemes",
        "ShikiAnnotationBenchmark": "buildAnnotatedString_small_light",
        "TextMateHighlightBenchmark": "highlightJavaScript_small",
    }
    
    for class_name, test_name in comparison_tests.items():
        benchmarks = grouped.get(class_name, [])
        for b in benchmarks:
            if test_name in b["name"]:
                lib_name = class_descriptions.get(class_name, class_name).split(" — ")[0]
                lines.append(
                    f"| {lib_name} | `{b['name']}` | {ns_to_ms(b['median_ns']):.2f} |"
                )
                break

    lines.append("")
    lines.append("---\n")
    lines.append("*Generated by `scripts/benchmark_report.py` from text output*\n")

    return "\n".join(lines)


def main():
    # Find the latest benchmark results
    results_dir = find_benchmark_results_dir()

    if results_dir is None or not results_dir.exists():
        print("ERROR: No benchmark results found.", file=sys.stderr)
        print("", file=sys.stderr)
        print("Run benchmarks first:", file=sys.stderr)
        print("  ./gradlew :microbenchmark:connectedAndroidTest", file=sys.stderr)
        print("", file=sys.stderr)
        sys.exit(1)

    print(f"Reading results from: {results_dir}", file=sys.stderr)
    results, context = parse_benchmark_results(results_dir)

    if not results:
        print("ERROR: No benchmark results found.", file=sys.stderr)
        sys.exit(1)

    print(f"Found {len(results)} benchmark results", file=sys.stderr)
    report = generate_markdown(results, context)

    # Print to stdout
    print(report)

    # Also write to file
    output_path = Path("microbenchmark/results/BENCHMARK_RESULTS_S24ULTRA.md")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(report)
    print(f"\nReport written to {output_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
