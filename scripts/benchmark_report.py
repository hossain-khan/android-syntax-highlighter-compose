#!/usr/bin/env python3
"""
Parse AndroidX Benchmark output and generate a Markdown comparison report.

Usage:
    python3 scripts/benchmark_report.py [device_name_or_dir] [-o output_file] [-d results_dir]

If no arguments are given, searches for the latest benchmark results and auto-detects the device.
"""
import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path


SDK_TO_ANDROID = {
    37: "37 (Android 17)",
    36: "36 (Android 16)",
    35: "35 (Android 15)",
    34: "34 (Android 14)",
    33: "33 (Android 13)",
    32: "32 (Android 12L)",
    31: "31 (Android 12)",
    30: "30 (Android 11)",
    29: "29 (Android 10)",
    28: "28 (Android 9)",
}

KNOWN_SHORT_NAMES = {
    "pixel 9 pro xl": "P9PXL",
    "google pixel 9 pro xl": "P9PXL",
    "pixel 11 pro": "P11PRO",
    "google pixel 11 pro": "P11PRO",
    "galaxy s24 ultra": "S24ULTRA",
    "samsung galaxy s24 ultra": "S24ULTRA",
}


def find_benchmark_results_dir() -> Path | None:
    """Find the latest benchmark results directory."""
    base = Path("microbenchmark/build/outputs/connected_android_test_additional_output/releaseAndroidTest/connected")
    if not base.exists():
        return None
    # Get the most recent device directory (ignore files like .DS_Store)
    device_dirs = sorted([p for p in base.glob("*") if p.is_dir()], key=lambda p: p.stat().st_mtime, reverse=True)
    return device_dirs[0] if device_dirs else None


def get_device_info(results_dir: Path) -> dict:
    """Extract device info from benchmarkData.json or directory name."""
    device_info = {
        "device": "Unknown Device",
        "model": "",
        "brand": "",
        "api_level": "Unknown",
        "cpu_cores": "Unknown",
        "cpu_max_freq": "Unknown",
        "cpu_locked": None,
        "ram": "Unknown",
    }

    # Try to find benchmarkData.json
    json_files = list(results_dir.glob("*benchmarkData.json"))
    if not json_files:
        json_files = list(results_dir.glob("*.json"))

    if json_files:
        try:
            data = json.loads(json_files[0].read_text())
            context = data.get("context", {})
            build = context.get("build", {})

            brand = build.get("brand", "").capitalize()
            model = build.get("model", "")
            device_info["model"] = model
            device_info["brand"] = brand

            if brand and model and brand.lower() not in model.lower():
                device_info["device"] = f"{brand} {model}"
            elif model:
                device_info["device"] = model
            elif brand:
                device_info["device"] = brand

            sdk = build.get("version", {}).get("sdk")
            if sdk:
                device_info["api_level"] = SDK_TO_ANDROID.get(sdk, str(sdk))

            if "cpuCoreCount" in context:
                device_info["cpu_cores"] = str(context["cpuCoreCount"])

            if "cpuMaxFreqHz" in context and context["cpuMaxFreqHz"]:
                device_info["cpu_max_freq"] = f"{context['cpuMaxFreqHz'] / 1_000_000_000:.2f} GHz"

            if "cpuLocked" in context:
                device_info["cpu_locked"] = str(context["cpuLocked"])

            if "memTotalBytes" in context and context["memTotalBytes"]:
                device_info["ram"] = f"{context['memTotalBytes'] / (1024**3):.1f} GB"

            return device_info
        except Exception:
            pass

    # Fallback to directory name (e.g. "Pixel 11 Pro - 17" -> "Pixel 11 Pro")
    clean_name = re.sub(r'\s*-\s*\d+$', '', results_dir.name)
    if clean_name:
        device_info["device"] = clean_name
        device_info["model"] = clean_name

    return device_info


def get_report_filename(device_name: str, model_name: str = "") -> str:
    """Derive output filename from device name or model."""
    for key in (model_name.lower().strip(), device_name.lower().strip()):
        if key in KNOWN_SHORT_NAMES:
            return f"BENCHMARK_RESULTS_{KNOWN_SHORT_NAMES[key]}.md"

    # Fallback: clean alphanumeric slug
    target = model_name or device_name
    slug = re.sub(r'[^A-Za-z0-9]+', '_', target).strip('_').upper()
    return f"BENCHMARK_RESULTS_{slug}.md"


def parse_benchmark_results(results_dir: Path) -> tuple[list[dict], dict]:
    """Parse benchmark text files (or JSON) and return structured results."""
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

    # Fallback to JSON if no message text files exist
    if not results:
        json_files = list(results_dir.glob("*benchmarkData.json")) or list(results_dir.glob("*.json"))
        if json_files:
            try:
                data = json.loads(json_files[0].read_text())
                for bench in data.get("benchmarks", []):
                    time_ns = bench.get("metrics", {}).get("timeNs", {})
                    allocs = bench.get("metrics", {}).get("allocationCount", {})
                    results.append({
                        "class": bench["className"].split(".")[-1],
                        "name": bench["name"],
                        "median_ns": int(time_ns.get("median", 0)),
                        "allocs": int(allocs.get("median", 0)),
                        "iterations": bench.get("repeatIterations", 0),
                    })
            except Exception:
                pass

    context = get_device_info(results_dir)
    return results, context


def ns_to_ms(ns: float) -> float:
    return ns / 1_000_000


def generate_markdown(results: list[dict], context: dict) -> str:
    """Generate a Markdown report from benchmark results."""
    lines = []
    device_title = context.get("device", "Unknown Device")
    lines.append(f"# Benchmark Results — {device_title}\n")

    # Device info
    lines.append("## Device Info\n")
    lines.append("| Property | Value |")
    lines.append("|----------|-------|")
    lines.append(f"| Device | {context.get('device', 'Unknown Device')} |")
    lines.append(f"| API Level | {context.get('api_level', 'Unknown')} |")
    lines.append(f"| CPU Cores | {context.get('cpu_cores', 'Unknown')} |")
    if context.get("cpu_max_freq") and context["cpu_max_freq"] != "Unknown":
        lines.append(f"| CPU Max Freq | {context['cpu_max_freq']} |")
    if context.get("cpu_locked") is not None:
        lines.append(f"| CPU Locked | {context['cpu_locked']} |")
    lines.append(f"| RAM | {context.get('ram', 'Unknown')} |")
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
    parser = argparse.ArgumentParser(
        description="Parse AndroidX Benchmark output and generate a Markdown comparison report."
    )
    parser.add_argument(
        "device_name",
        nargs="?",
        default=None,
        help="Optional device name (e.g. 'Pixel 11 Pro', 'P11PRO') or results directory path",
    )
    parser.add_argument(
        "-o", "--output",
        help="Custom output file path (e.g., microbenchmark/results/BENCHMARK_RESULTS_P11PRO.md)",
    )
    parser.add_argument(
        "-d", "--dir",
        help="Explicit benchmark results directory path",
    )
    args = parser.parse_args()

    results_dir = None
    if args.dir:
        results_dir = Path(args.dir)
    elif args.device_name and Path(args.device_name).is_dir():
        results_dir = Path(args.device_name)
    else:
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

    # If device_name was explicitly passed as argument (and wasn't a directory)
    if args.device_name and not Path(args.device_name).is_dir():
        context["device"] = args.device_name
        context["model"] = args.device_name

    if not results:
        print("ERROR: No benchmark results found.", file=sys.stderr)
        sys.exit(1)

    print(f"Found {len(results)} benchmark results for {context.get('device')}", file=sys.stderr)
    report = generate_markdown(results, context)

    # Print to stdout
    print(report)

    # Determine output file path
    if args.output:
        output_path = Path(args.output)
    else:
        output_filename = get_report_filename(
            device_name=context.get("device", "Unknown"),
            model_name=context.get("model", ""),
        )
        output_path = Path("microbenchmark/results") / output_filename

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(report)
    print(f"\nReport written to {output_path}", file=sys.stderr)


if __name__ == "__main__":
    main()

