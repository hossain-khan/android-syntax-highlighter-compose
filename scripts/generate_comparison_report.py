#!/usr/bin/env python3
"""
Generate a comprehensive HTML report comparing benchmark results across all available devices.

Usage:
    python3 scripts/generate_comparison_report.py [-o output.html]
"""
import argparse
import html
import json
import re
import sys
from pathlib import Path


DEVICE_DISPLAY_NAMES = {
    "P9PXL": "Pixel 9 Pro XL",
    "S24ULTRA": "Galaxy S24 Ultra",
    "P11PRO": "Pixel 11 Pro",
}

DEVICE_COLORS = {
    "Pixel 9 Pro XL": {"border": "#4285F4", "bg": "rgba(66, 133, 244, 0.7)"},
    "Galaxy S24 Ultra": {"border": "#9C27B0", "bg": "rgba(156, 39, 176, 0.7)"},
    "Pixel 11 Pro": {"border": "#0F9D58", "bg": "rgba(15, 157, 88, 0.7)"},
}


def parse_markdown_results(file_path: Path) -> dict:
    """Extract device info and benchmark tables from a BENCHMARK_RESULTS_*.md file."""
    content = file_path.read_text()

    device_info = {}
    sections = {}
    current_section = None

    lines = content.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i].strip()

        # Parse Device Info table
        if line == "## Device Info":
            i += 1
            while i < len(lines) and not lines[i].startswith("## "):
                row = lines[i].strip()
                if row.startswith("|") and not row.startswith("|--"):
                    parts = [p.strip() for p in row.split("|")[1:-1]]
                    if len(parts) == 2 and parts[0] != "Property":
                        device_info[parts[0]] = parts[1]
                i += 1
            continue

        # Parse Section Headers
        if line.startswith("## ") and not line.startswith("## Cross-Library Comparison"):
            current_section = line.replace("## ", "").strip()
            sections[current_section] = {}
            i += 1
            continue

        # Parse Table Rows
        if current_section and line.startswith("| `"):
            parts = [p.strip() for p in line.split("|")[1:-1]]
            test_name = parts[0].replace("`", "").strip()
            try:
                median_ms = float(parts[1])
            except ValueError:
                median_ms = None

            allocs = None
            if len(parts) >= 3 and parts[2] != "":
                clean_allocs = parts[2].replace(",", "").strip()
                if clean_allocs.isdigit():
                    allocs = int(clean_allocs)

            sections[current_section][test_name] = {
                "median_ms": median_ms,
                "allocs": allocs,
            }

        i += 1

    # Extract device short name from filename: BENCHMARK_RESULTS_<SLUG>.md
    slug = file_path.stem.replace("BENCHMARK_RESULTS_", "")
    display_name = DEVICE_DISPLAY_NAMES.get(slug, device_info.get("Device", slug))
    if display_name.startswith("google ") or display_name.startswith("Google "):
        display_name = display_name.replace("google ", "").replace("Google ", "")

    return {
        "slug": slug,
        "device_name": display_name,
        "info": device_info,
        "sections": sections,
    }


HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="en" data-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AndroidX Benchmark: Multi-Device Performance Comparison</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4"></script>
    <style>
        :root {
            --bg: #f8fafc;
            --bg-card: #ffffff;
            --text: #0f172a;
            --text-muted: #64748b;
            --border: #e2e8f0;
            --border-light: #f1f5f9;
            --th-bg: #f1f5f9;
            --shadow: 0 4px 6px -1px rgb(0 0 0 / 0.07), 0 2px 4px -2px rgb(0 0 0 / 0.05);
            --toggle-bg: #cbd5e1;
            --toggle-knob: #ffffff;
            --fastest-bg: #ecfdf5;
            --fastest-text: #047857;
            --fastest-border: #a7f3d0;
            --callout-bg: #f0f9ff;
            --callout-border: #0284c7;
        }
        [data-theme="dark"] {
            --bg: #0f172a;
            --bg-card: #1e293b;
            --text: #f8fafc;
            --text-muted: #94a3b8;
            --border: #334155;
            --border-light: #1e293b;
            --th-bg: #1e293b;
            --shadow: 0 4px 6px -1px rgb(0 0 0 / 0.3);
            --toggle-bg: #475569;
            --toggle-knob: #f8fafc;
            --fastest-bg: #064e3b;
            --fastest-text: #6ee7b7;
            --fastest-border: #059669;
            --callout-bg: #082f49;
            --callout-border: #38bdf8;
        }
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            background: var(--bg);
            color: var(--text);
            padding: 2.5rem 1.5rem;
            line-height: 1.5;
            transition: background 0.25s, color 0.25s;
        }
        .container {
            max-width: 1240px;
            margin: 0 auto;
        }
        .header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 2rem;
            flex-wrap: wrap;
            gap: 1rem;
        }
        .header-title h1 {
            font-size: 2rem;
            font-weight: 800;
            letter-spacing: -0.025em;
            margin-bottom: 0.35rem;
        }
        .header-title p {
            color: var(--text-muted);
            font-size: 1.05rem;
        }
        .theme-toggle {
            width: 56px;
            height: 30px;
            background: var(--toggle-bg);
            border-radius: 15px;
            cursor: pointer;
            border: none;
            position: relative;
            transition: background 0.25s;
            flex-shrink: 0;
        }
        .theme-toggle .knob {
            position: absolute;
            top: 3px;
            left: 3px;
            width: 24px;
            height: 24px;
            background: var(--toggle-knob);
            border-radius: 50%;
            transition: transform 0.25s;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 1px 3px rgba(0,0,0,0.25);
            font-size: 13px;
        }
        [data-theme="dark"] .theme-toggle .knob {
            transform: translateX(26px);
        }
        .callout {
            background: var(--callout-bg);
            border-left: 4px solid var(--callout-border);
            padding: 1.15rem 1.35rem;
            border-radius: 0 10px 10px 0;
            margin-bottom: 2rem;
            font-size: 0.95rem;
        }
        .device-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 1.25rem;
            margin-bottom: 2rem;
        }
        .device-card {
            background: var(--bg-card);
            border-radius: 12px;
            padding: 1.4rem;
            box-shadow: var(--shadow);
            border: 1px solid var(--border);
            border-top: 4px solid var(--card-accent, #3b82f6);
            position: relative;
        }
        .device-badge {
            position: absolute;
            top: 1.4rem;
            right: 1.4rem;
            color: #ffffff;
            font-size: 0.72rem;
            font-weight: 700;
            padding: 0.2rem 0.6rem;
            border-radius: 9999px;
            letter-spacing: 0.05em;
        }
        .device-card h3 {
            font-size: 1.3rem;
            font-weight: 700;
            margin-bottom: 1rem;
            padding-right: 5rem;
        }
        .specs-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.88rem;
        }
        .specs-table td {
            padding: 0.45rem 0;
            border-bottom: 1px solid var(--border-light);
        }
        .specs-table td:last-child {
            text-align: right;
        }
        .card {
            background: var(--bg-card);
            border-radius: 14px;
            padding: 1.6rem;
            margin-bottom: 2rem;
            box-shadow: var(--shadow);
            border: 1px solid var(--border);
        }
        .card h2 {
            font-size: 1.35rem;
            font-weight: 700;
            margin-bottom: 1.2rem;
        }
        .section-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 1.25rem;
        }
        .section-header h2 {
            margin-bottom: 0;
        }
        .badge-count {
            background: var(--th-bg);
            color: var(--text-muted);
            font-size: 0.75rem;
            font-weight: 600;
            padding: 0.25rem 0.65rem;
            border-radius: 9999px;
        }
        .chart-box {
            margin: 1.5rem 0;
            position: relative;
        }
        .table-responsive {
            overflow-x: auto;
            margin-top: 1rem;
        }
        .benchmark-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.92rem;
        }
        .benchmark-table th, .benchmark-table td {
            padding: 0.75rem 1rem;
            text-align: left;
            border-bottom: 1px solid var(--border);
        }
        .benchmark-table th {
            background: var(--th-bg);
            font-weight: 600;
            color: var(--text-muted);
            text-transform: uppercase;
            font-size: 0.75rem;
            letter-spacing: 0.05em;
        }
        .benchmark-table td:nth-child(n+2), .benchmark-table th:nth-child(n+2) {
            text-align: right;
        }
        .benchmark-table code {
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
            background: var(--th-bg);
            padding: 0.15rem 0.4rem;
            border-radius: 4px;
            font-size: 0.84rem;
        }
        .fastest-cell {
            background: var(--fastest-bg);
        }
        .fastest-badge {
            display: inline-block;
            background: var(--fastest-text);
            color: #ffffff;
            font-size: 0.68rem;
            font-weight: 700;
            padding: 0.1rem 0.4rem;
            border-radius: 4px;
            margin-left: 0.4rem;
            vertical-align: middle;
        }
        .alloc-sub {
            font-size: 0.75rem;
            color: var(--text-muted);
            margin-top: 0.15rem;
        }
        .na {
            color: var(--text-muted);
            font-style: italic;
        }
        .footer {
            text-align: center;
            color: var(--text-muted);
            font-size: 0.85rem;
            margin-top: 3rem;
            padding-top: 1.5rem;
            border-top: 1px solid var(--border);
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <div class="header">
            <div class="header-title">
                <h1>AndroidX Benchmark Multi-Device Comparison</h1>
                <p>Performance comparison across Pixel 9 Pro XL, Galaxy S24 Ultra, and Pixel 11 Pro</p>
            </div>
            <button class="theme-toggle" onclick="toggleTheme()" aria-label="Toggle theme">
                <div class="knob"><span id="themeIcon">&#9788;</span></div>
            </button>
        </div>

        <!-- Callout -->
        <div class="callout">
            <strong>Benchmark Overview:</strong> Real-device benchmarks executed with AndroidX Microbenchmark in release mode with R8 minification. Timings represent median execution times over multiple measurement iterations. Green highlights indicate the fastest execution time for each test across devices.
        </div>

        <!-- Device Cards -->
        <div class="device-grid">
            __DEVICE_GRID__
        </div>

        <!-- Cross-Library Comparison Card -->
        <div class="card">
            <h2>Cross-Library Comparison (Small Samples)</h2>
            <p style="color: var(--text-muted); margin-bottom: 1rem; font-size: 0.92rem;">
                Comparing median highlighting execution times across all three libraries on small code samples.
            </p>
            <div class="chart-box">
                <canvas id="crossLibraryChart" height="100"></canvas>
            </div>
        </div>

        <!-- Detailed Sections -->
        __SECTIONS_HTML__

        <!-- Footer -->
        <div class="footer">
            <p>Generated by <code>scripts/generate_comparison_report.py</code> &bull; Source benchmarks in <code>microbenchmark/results/</code></p>
        </div>
    </div>

    <script>
        // Theme Management
        function applyTheme(theme) {
            document.documentElement.setAttribute('data-theme', theme);
            const icon = document.getElementById('themeIcon');
            icon.innerHTML = theme === 'dark' ? '&#9790;' : '&#9788;';
            localStorage.setItem('benchmark-theme', theme);
            updateChartTheme(theme);
        }

        function toggleTheme() {
            const current = document.documentElement.getAttribute('data-theme') || 'light';
            applyTheme(current === 'light' ? 'dark' : 'light');
        }

        const savedTheme = localStorage.getItem('benchmark-theme') || 
            (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
        applyTheme(savedTheme);

        // Chart.js Configuration
        const chartInstances = [];
        function getChartColors(theme) {
            const isDark = theme === 'dark';
            return {
                grid: isDark ? '#334155' : '#e2e8f0',
                text: isDark ? '#94a3b8' : '#64748b',
            };
        }

        function updateChartTheme(theme) {
            const colors = getChartColors(theme);
            chartInstances.forEach(c => {
                if (c.options.scales.x) {
                    c.options.scales.x.grid.color = colors.grid;
                    c.options.scales.x.ticks.color = colors.text;
                }
                if (c.options.scales.y) {
                    c.options.scales.y.grid.color = colors.grid;
                    c.options.scales.y.ticks.color = colors.text;
                }
                c.update();
            });
        }

        // Render Cross-Library Chart
        const crossData = __CROSS_CHART_JSON__;
        const colors = getChartColors(savedTheme);

        const crossCtx = document.getElementById('crossLibraryChart').getContext('2d');
        const crossChart = new Chart(crossCtx, {
            type: 'bar',
            data: crossData,
            options: {
                responsive: true,
                plugins: {
                    legend: { position: 'top', labels: { color: colors.text } },
                    tooltip: {
                        callbacks: {
                            label: function(ctx) {
                                return ctx.dataset.label + ': ' + ctx.parsed.y.toFixed(2) + ' ms';
                            }
                        }
                    }
                },
                scales: {
                    x: { grid: { color: colors.grid }, ticks: { color: colors.text } },
                    y: {
                        grid: { color: colors.grid },
                        ticks: { color: colors.text },
                        title: { display: true, text: 'Median Duration (ms)', color: colors.text }
                    }
                }
            }
        });
        chartInstances.push(crossChart);

        // Render Section Charts
        const sectionDataMap = __SECTION_CHARTS_JSON__;
        const sectionKeys = Object.keys(sectionDataMap);
        sectionKeys.forEach((sec, idx) => {
            const canvas = document.getElementById('chart-' + idx);
            if (!canvas) return;
            const secData = sectionDataMap[sec];
            const chart = new Chart(canvas.getContext('2d'), {
                type: 'bar',
                data: secData,
                options: {
                    responsive: true,
                    plugins: {
                        legend: { position: 'top', labels: { color: colors.text } },
                        tooltip: {
                            callbacks: {
                                label: function(ctx) {
                                    return ctx.dataset.label + ': ' + ctx.parsed.y.toFixed(2) + ' ms';
                                }
                            }
                        }
                    },
                    scales: {
                        x: { grid: { color: colors.grid }, ticks: { color: colors.text } },
                        y: {
                            grid: { color: colors.grid },
                            ticks: { color: colors.text },
                            title: { display: true, text: 'Median (ms)', color: colors.text }
                        }
                    }
                }
            });
            chartInstances.push(chart);
        });
    </script>
</body>
</html>
"""


def generate_html(devices: list[dict]) -> str:
    """Generate modern, responsive HTML report."""
    preferred_sections = [
        "Compose Highlight — WebView JS Bridge",
        "Shiki — AnnotatedString Building",
        "TextMate — Code Highlighting",
        "TextMate — Grammar & Theme Loading",
    ]
    all_sections = []
    for ps in preferred_sections:
        if any(ps in d["sections"] for d in devices):
            all_sections.append(ps)
    for d in devices:
        for sec in d["sections"]:
            if sec not in all_sections:
                all_sections.append(sec)

    # Prepare chart datasets for Cross-Library small sample
    cross_library_tests = [
        ("Compose Highlight", "Compose Highlight — WebView JS Bridge", "highlightJson_bothThemes"),
        ("Shiki", "Shiki — AnnotatedString Building", "buildAnnotatedString_small_light"),
        ("TextMate", "TextMate — Code Highlighting", "highlightJavaScript_small"),
    ]
    cross_chart_labels = [item[0] for item in cross_library_tests]
    cross_chart_datasets = []
    for dev in devices:
        color = DEVICE_COLORS.get(dev["device_name"], {"border": "#888", "bg": "rgba(136,136,136,0.7)"})
        data_points = []
        for label, sec_name, test_name in cross_library_tests:
            val = None
            if sec_name in dev["sections"] and test_name in dev["sections"][sec_name]:
                val = dev["sections"][sec_name][test_name]["median_ms"]
            data_points.append(val if val is not None else 0)
        cross_chart_datasets.append({
            "label": dev["device_name"],
            "data": data_points,
            "backgroundColor": color["bg"],
            "borderColor": color["border"],
            "borderWidth": 2,
            "borderRadius": 6,
        })

    # Prepare Section Comparison Charts
    section_charts = {}
    for sec in all_sections:
        test_set = []
        for dev in devices:
            if sec in dev["sections"]:
                for t in dev["sections"][sec]:
                    if t not in test_set:
                        test_set.append(t)

        def sort_key(t):
            for dev in devices:
                if sec in dev["sections"] and t in dev["sections"][sec] and dev["sections"][sec][t]["median_ms"] is not None:
                    return dev["sections"][sec][t]["median_ms"]
            return 999999
        test_set.sort(key=sort_key)

        chart_datasets = []
        for dev in devices:
            color = DEVICE_COLORS.get(dev["device_name"], {"border": "#888", "bg": "rgba(136,136,136,0.7)"})
            points = []
            for t in test_set:
                val = None
                if sec in dev["sections"] and t in dev["sections"][sec]:
                    val = dev["sections"][sec][t]["median_ms"]
                points.append(val if val is not None else 0)
            chart_datasets.append({
                "label": dev["device_name"],
                "data": points,
                "backgroundColor": color["bg"],
                "borderColor": color["border"],
                "borderWidth": 2,
                "borderRadius": 6,
            })
        section_charts[sec] = {
            "labels": [f"`{t}`" for t in test_set],
            "raw_labels": test_set,
            "datasets": chart_datasets,
        }

    cross_chart_json = json.dumps({
        "labels": cross_chart_labels,
        "datasets": cross_chart_datasets,
    })

    section_charts_json = json.dumps({
        sec: {
            "labels": section_charts[sec]["raw_labels"],
            "datasets": section_charts[sec]["datasets"],
        } for sec in all_sections
    })

    # Build Device Cards HTML
    device_cards_html = []
    for d in devices:
        info = d["info"]
        dev_name = d["device_name"]
        color = DEVICE_COLORS.get(dev_name, {"border": "#666", "bg": "#eee"})
        device_cards_html.append(f"""
        <div class="device-card" style="--card-accent: {color['border']};">
            <div class="device-badge" style="background: {color['border']};">{d['slug']}</div>
            <h3>{html.escape(dev_name)}</h3>
            <table class="specs-table">
                <tr><td>API Level</td><td><strong>{html.escape(info.get('API Level', 'Unknown'))}</strong></td></tr>
                <tr><td>CPU Cores</td><td>{html.escape(info.get('CPU Cores', 'Unknown'))} cores</td></tr>
                <tr><td>CPU Max Freq</td><td>{html.escape(info.get('CPU Max Freq', 'N/A'))}</td></tr>
                <tr><td>RAM</td><td>{html.escape(info.get('RAM', 'Unknown'))}</td></tr>
            </table>
        </div>
        """)

    # Build Section Tables HTML
    sections_html = []
    for sec_idx, sec in enumerate(all_sections):
        test_data = section_charts[sec]
        raw_labels = test_data["raw_labels"]

        table_rows = []
        for t in raw_labels:
            row_cells = [f"<td><code>{html.escape(t)}</code></td>"]

            times = []
            for dev in devices:
                if sec in dev["sections"] and t in dev["sections"][sec]:
                    ms = dev["sections"][sec][t]["median_ms"]
                    if ms is not None and ms > 0:
                        times.append(ms)
            min_time = min(times) if times else None

            for dev in devices:
                if sec in dev["sections"] and t in dev["sections"][sec]:
                    item = dev["sections"][sec][t]
                    ms = item["median_ms"]
                    allocs = item["allocs"]

                    is_fastest = (ms is not None and min_time is not None and abs(ms - min_time) < 1e-6)
                    badge = '<span class="fastest-badge">Fastest</span>' if (is_fastest and len(times) > 1) else ''

                    val_str = f"<strong>{ms:.2f} ms</strong> {badge}" if ms is not None else '<span class="na">&mdash;</span>'
                    alloc_str = f'<div class="alloc-sub">{allocs:,} allocs</div>' if allocs is not None else ''
                    cell_class = ' class="fastest-cell"' if is_fastest and len(times) > 1 else ''
                    row_cells.append(f"<td{cell_class}>{val_str}{alloc_str}</td>")
                else:
                    row_cells.append('<td><span class="na">&mdash;</span></td>')

            table_rows.append(f"<tr>{''.join(row_cells)}</tr>")

        th_headers = "".join([f"<th>{html.escape(d['device_name'])}</th>" for d in devices])
        sections_html.append(f"""
        <div class="card section-card" id="section-{sec_idx}">
            <div class="section-header">
                <h2>{html.escape(sec)}</h2>
                <span class="badge-count">{len(raw_labels)} benchmarks</span>
            </div>

            <div class="chart-box">
                <canvas id="chart-{sec_idx}" height="100"></canvas>
            </div>

            <div class="table-responsive">
                <table class="benchmark-table">
                    <thead>
                        <tr>
                            <th>Benchmark</th>
                            {th_headers}
                        </tr>
                    </thead>
                    <tbody>
                        {''.join(table_rows)}
                    </tbody>
                </table>
            </div>
        </div>
        """)

    html_out = HTML_TEMPLATE
    html_out = html_out.replace("__DEVICE_GRID__", "".join(device_cards_html))
    html_out = html_out.replace("__CROSS_CHART_JSON__", cross_chart_json)
    html_out = html_out.replace("__SECTION_CHARTS_JSON__", section_charts_json)
    html_out = html_out.replace("__SECTIONS_HTML__", "".join(sections_html))
    return html_out


def main():
    parser = argparse.ArgumentParser(description="Generate multi-device comparison HTML report.")
    parser.add_argument(
        "-o", "--output",
        default="microbenchmark/results/benchmark-comparison.html",
        help="Path to output HTML file (default: microbenchmark/results/benchmark-comparison.html)",
    )
    args = parser.parse_args()

    results_dir = Path("microbenchmark/results")
    md_files = sorted(results_dir.glob("BENCHMARK_RESULTS_*.md"))
    if not md_files:
        print("No benchmark result markdown files found.", file=sys.stderr)
        sys.exit(1)

    order = {"P9PXL": 1, "S24ULTRA": 2, "P11PRO": 3}
    md_files.sort(key=lambda p: order.get(p.stem.replace("BENCHMARK_RESULTS_", ""), 99))

    devices = [parse_markdown_results(f) for f in md_files]
    html_content = generate_html(devices)

    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(html_content)
    print(f"Generated comparison report with {len(devices)} devices: {out_path}")


if __name__ == "__main__":
    main()
