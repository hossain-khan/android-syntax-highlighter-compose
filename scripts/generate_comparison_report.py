#!/usr/bin/env python3
"""
Generate a comprehensive, self-contained HTML report comparing benchmark results across all available devices.

Usage:
    python3 scripts/generate_comparison_report.py [-o output.html]
"""
import argparse
import html
import json
import math
import re
import sys
from pathlib import Path


DEVICE_DISPLAY_NAMES = {
    "P9PXL": "Pixel 9 Pro XL",
    "S24ULTRA": "Galaxy S24 Ultra",
    "P11PRO": "Pixel 11 Pro",
}

DEVICE_COLORS = {
    "Pixel 9 Pro XL": {"border": "#3b82f6", "bg": "rgba(59, 130, 246, 0.85)"},
    "Galaxy S24 Ultra": {"border": "#a855f7", "bg": "rgba(168, 85, 247, 0.85)"},
    "Pixel 11 Pro": {"border": "#10b981", "bg": "rgba(16, 185, 129, 0.85)"},
}


def format_test_name(raw_name: str) -> str:
    """Format benchmark method name into a clean, concise, human-readable label."""
    name = raw_name
    # Compose Highlight
    name = re.sub(r'^highlight', '', name)
    name = re.sub(r'_bothThemes$', ' (both)', name)
    # Shiki
    name = re.sub(r'^buildAnnotatedString_', '', name)
    # TextMate Grammar
    name = re.sub(r'^load', '', name)
    name = re.sub(r'Grammar$', ' Grammar', name)
    name = re.sub(r'Theme$', ' Theme', name)
    # TextMate Highlight
    name = re.sub(r'_(small|medium|large)', r' \1', name)
    name = re.sub(r'_(lightTheme|oneDarkPro)', r' \1', name)

    name = name.replace("JavaScript", "JS")
    name = name.replace("lightTheme", "Light")
    name = name.replace("oneDarkPro", "OneDark")
    name = name.replace("DarkPlus", "Dark+")
    name = name.replace("OneDarkPro", "OneDark")
    name = name.replace("small", "Small")
    name = name.replace("medium", "Med")
    name = name.replace("large", "Large")
    name = name.replace("dark", "Dark")
    name = name.replace("light", "Light")
    name = name.replace("_", " ").strip()
    return name


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

    slug = file_path.stem.replace("BENCHMARK_RESULTS_", "")
    display_name = DEVICE_DISPLAY_NAMES.get(slug, device_info.get("Device", slug))
    if display_name.startswith("google ") or display_name.startswith("Google "):
        display_name = display_name.replace("google ", "").replace("Google ", "")

    api = device_info.get("API Level", "")
    if api == "36":
        device_info["API Level"] = "36 (Android 15)"
    elif api == "37":
        device_info["API Level"] = "37 (Android 17)"

    return {
        "slug": slug,
        "device_name": display_name,
        "info": device_info,
        "sections": sections,
    }


def generate_svg_bar_chart(categories: list[str], device_names: list[str], data_matrix: list[list[float | None]], height: int = 290, width: int = 960) -> str:
    """Generate self-contained, responsive SVG grouped bar chart with non-overlapping angled labels."""
    all_vals = [v for dev_data in data_matrix for v in dev_data if v is not None and v > 0]
    if not all_vals:
        return '<div class="chart-empty">No chart data available</div>'

    max_val = max(all_vals)
    if max_val <= 0.1:
        nice_max = 0.1
    elif max_val <= 1.0:
        nice_max = math.ceil(max_val * 10) / 10
    elif max_val <= 10:
        nice_max = math.ceil(max_val)
    elif max_val <= 50:
        nice_max = math.ceil(max_val / 5) * 5
    elif max_val <= 100:
        nice_max = math.ceil(max_val / 10) * 10
    else:
        nice_max = math.ceil(max_val / 50) * 50

    pad_left = 65
    pad_right = 30
    pad_top = 45
    pad_bottom = 85
    chart_w = width - pad_left - pad_right
    chart_h = height - pad_top - pad_bottom

    num_cats = len(categories)
    num_devs = len(device_names)
    group_w = chart_w / num_cats
    bar_w = max(10, min(24, (group_w * 0.7) / num_devs))
    bar_gap = 3

    svg_parts = []
    svg_parts.append(f'<svg viewBox="0 0 {width} {height}" class="svg-chart" xmlns="http://www.w3.org/2000/svg">')

    # Horizontal grid lines (4 intervals)
    for step in range(5):
        frac = step / 4
        y_val = nice_max * frac
        y_pos = pad_top + chart_h - (chart_h * frac)
        svg_parts.append(f'<line x1="{pad_left}" y1="{y_pos:.1f}" x2="{width - pad_right}" y2="{y_pos:.1f}" class="grid-line" />')
        if nice_max < 1:
            lbl = f"{y_val:.2f} ms"
        elif nice_max < 10:
            lbl = f"{y_val:.1f} ms"
        else:
            lbl = f"{y_val:.0f} ms"
        svg_parts.append(f'<text x="{pad_left - 8}" y="{y_pos + 4:.1f}" text-anchor="end" class="axis-lbl">{lbl}</text>')

    # Grouped bars
    for cat_idx, cat in enumerate(categories):
        group_center = pad_left + (cat_idx + 0.5) * group_w
        total_bars_w = num_devs * bar_w + (num_devs - 1) * bar_gap
        group_start_x = group_center - total_bars_w / 2

        friendly_cat = format_test_name(cat)

        for dev_idx, dev_name in enumerate(device_names):
            val = data_matrix[dev_idx][cat_idx]
            x_pos = group_start_x + dev_idx * (bar_w + bar_gap)
            color = DEVICE_COLORS[dev_name]["border"]

            if val is not None and val > 0:
                bar_h = max(2.0, (val / nice_max) * chart_h)
                y_pos = pad_top + chart_h - bar_h
                tooltip = f"{dev_name} • {cat}: {val:.2f} ms"

                svg_parts.append('<g class="bar-group">')
                svg_parts.append(f'<title>{html.escape(tooltip)}</title>')
                svg_parts.append(f'<rect x="{x_pos:.1f}" y="{y_pos:.1f}" width="{bar_w:.1f}" height="{bar_h:.1f}" fill="{color}" rx="3" class="bar-rect" />')

                # Angled value label above bar to prevent horizontal overlap
                val_text = f"{val:.2f}" if val < 10 else f"{val:.1f}"
                vx = x_pos + bar_w / 2
                vy = y_pos - 4
                if num_cats <= 4:
                    # Straight horizontal for wide spacing (e.g. Cross-Library)
                    svg_parts.append(f'<text x="{vx:.1f}" y="{vy:.1f}" text-anchor="middle" class="bar-val-lbl">{val_text}</text>')
                else:
                    # Angled at -45° to eliminate horizontal collision
                    svg_parts.append(f'<text x="{vx:.1f}" y="{vy:.1f}" transform="rotate(-45, {vx:.1f}, {vy:.1f})" text-anchor="start" class="bar-val-lbl">{val_text}</text>')
                svg_parts.append('</g>')
            else:
                vx = x_pos + bar_w / 2
                svg_parts.append(f'<text x="{vx:.1f}" y="{pad_top + chart_h - 4:.1f}" text-anchor="middle" class="bar-na-lbl">&mdash;</text>')

        # Category label under group (angled at -30° with text-anchor="end" for 100% collision-free layout)
        lx = group_center
        ly = pad_top + chart_h + 14
        if num_cats <= 4:
            svg_parts.append(f'<text x="{lx:.1f}" y="{ly + 10:.1f}" text-anchor="middle" class="cat-lbl">{html.escape(friendly_cat)}</text>')
        else:
            svg_parts.append(f'<text x="{lx:.1f}" y="{ly:.1f}" transform="rotate(-30, {lx:.1f}, {ly:.1f})" text-anchor="end" class="cat-lbl">{html.escape(friendly_cat)}</text>')

    svg_parts.append('</svg>')
    return "".join(svg_parts)


HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="en" data-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AndroidX Benchmark: Multi-Device Performance Comparison</title>
    <style>
        :root {
            --bg: #f8fafc;
            --bg-card: #ffffff;
            --text: #0f172a;
            --text-muted: #64748b;
            --border: #e2e8f0;
            --border-light: #f1f5f9;
            --th-bg: #f8fafc;
            --shadow: 0 4px 6px -1px rgb(0 0 0 / 0.07), 0 2px 4px -2px rgb(0 0 0 / 0.05);
            --toggle-bg: #cbd5e1;
            --toggle-knob: #ffffff;
            --fastest-bg: #ecfdf5;
            --fastest-text: #047857;
            --fastest-border: #a7f3d0;
            --callout-bg: #f0f9ff;
            --callout-border: #0284c7;
            --grid-line: #e2e8f0;
            --axis-text: #64748b;
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
            --grid-line: #334155;
            --axis-text: #94a3b8;
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
            margin-bottom: 0.5rem;
        }
        .section-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 0.75rem;
        }
        .badge-count {
            background: var(--th-bg);
            color: var(--text-muted);
            font-size: 0.75rem;
            font-weight: 600;
            padding: 0.25rem 0.65rem;
            border-radius: 9999px;
            border: 1px solid var(--border);
        }
        .chart-legend {
            display: flex;
            gap: 1.25rem;
            margin: 1rem 0 0.5rem;
            flex-wrap: wrap;
            font-size: 0.85rem;
        }
        .legend-item {
            display: flex;
            align-items: center;
            gap: 0.45rem;
        }
        .legend-color {
            width: 14px;
            height: 14px;
            border-radius: 3px;
        }
        .chart-box {
            margin: 1rem 0 1.5rem;
            overflow-x: auto;
            border: 1px solid var(--border);
            border-radius: 8px;
            background: var(--bg-card);
            padding: 0.75rem 0.5rem;
        }
        .svg-chart {
            width: 100%;
            height: auto;
            min-height: 250px;
            display: block;
        }
        .grid-line {
            stroke: var(--grid-line);
            stroke-width: 1;
            stroke-dasharray: 3 3;
        }
        .axis-lbl {
            fill: var(--axis-text);
            font-size: 11px;
            font-family: inherit;
        }
        .cat-lbl {
            fill: var(--text);
            font-size: 11px;
            font-weight: 600;
            font-family: inherit;
        }
        .bar-val-lbl {
            fill: var(--text-muted);
            font-size: 9.5px;
            font-weight: 600;
            font-family: inherit;
        }
        .bar-na-lbl {
            fill: var(--text-muted);
            font-size: 12px;
            font-weight: 400;
        }
        .bar-rect {
            transition: opacity 0.2s;
            cursor: pointer;
        }
        .bar-group:hover .bar-rect {
            opacity: 0.82;
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
            border: 1px solid var(--border);
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
            <strong>Benchmark Overview:</strong> Real-device benchmarks executed with AndroidX Microbenchmark in release mode with R8 minification. Timings represent median execution times over measurement iterations. Green highlights indicate the fastest device for each test (minimum 2 comparable results, excluding ties). A dash (&mdash;) indicates tests not included in that device's specific benchmark run.
        </div>

        <!-- Device Cards -->
        <div class="device-grid">
            __DEVICE_GRID__
        </div>

        <!-- Cross-Library Comparison Card -->
        <div class="card">
            <h2>Cross-Library Comparison (Small Samples)</h2>
            <p style="color: var(--text-muted); font-size: 0.92rem;">
                Comparing median execution times across all three highlighting libraries on small code samples.
            </p>
            <div class="chart-legend">
                __LEGEND_HTML__
            </div>
            <div class="chart-box">
                __CROSS_CHART_SVG__
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
        function applyTheme(theme) {
            document.documentElement.setAttribute('data-theme', theme);
            const icon = document.getElementById('themeIcon');
            if (icon) {
                icon.innerHTML = theme === 'dark' ? '&#9790;' : '&#9788;';
            }
            localStorage.setItem('benchmark-theme', theme);
        }

        function toggleTheme() {
            const current = document.documentElement.getAttribute('data-theme') || 'light';
            applyTheme(current === 'light' ? 'dark' : 'light');
        }

        const savedTheme = localStorage.getItem('benchmark-theme') || 
            (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
        applyTheme(savedTheme);
    </script>
</body>
</html>
"""


def generate_html(devices: list[dict]) -> str:
    """Generate self-contained HTML report with SVG charts."""
    device_names = [d["device_name"] for d in devices]

    legend_items = []
    for dname in device_names:
        c = DEVICE_COLORS[dname]["border"]
        legend_items.append(f'<div class="legend-item"><div class="legend-color" style="background: {c};"></div><span>{html.escape(dname)}</span></div>')
    legend_html = "".join(legend_items)

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

    # Cross-Library SVG chart
    cross_library_tests = [
        ("Compose Highlight", "Compose Highlight — WebView JS Bridge", "highlightJson_bothThemes"),
        ("Shiki", "Shiki — AnnotatedString Building", "buildAnnotatedString_small_light"),
        ("TextMate", "TextMate — Code Highlighting", "highlightJavaScript_small"),
    ]
    cross_categories = [item[0] for item in cross_library_tests]
    cross_matrix = []
    for dev in devices:
        dev_vals = []
        for label, sec_name, test_name in cross_library_tests:
            val = None
            if sec_name in dev["sections"] and test_name in dev["sections"][sec_name]:
                val = dev["sections"][sec_name][test_name]["median_ms"]
            dev_vals.append(val)
        cross_matrix.append(dev_vals)

    cross_chart_svg = generate_svg_bar_chart(cross_categories, device_names, cross_matrix, height=270, width=960)

    # Device Cards HTML
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

    # Detailed Sections HTML with SVG charts
    sections_html = []
    for sec_idx, sec in enumerate(all_sections):
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

        sec_matrix = []
        for dev in devices:
            dev_vals = []
            for t in test_set:
                val = None
                if sec in dev["sections"] and t in dev["sections"][sec]:
                    val = dev["sections"][sec][t]["median_ms"]
                dev_vals.append(val)
            sec_matrix.append(dev_vals)

        sec_chart_svg = generate_svg_bar_chart(test_set, device_names, sec_matrix, height=290, width=960)

        table_rows = []
        for cat_idx, t in enumerate(test_set):
            row_cells = [f"<td><code>{html.escape(t)}</code></td>"]

            times = []
            for dev in devices:
                if sec in dev["sections"] and t in dev["sections"][sec]:
                    ms = dev["sections"][sec][t]["median_ms"]
                    if ms is not None and ms > 0:
                        times.append(ms)

            min_time = min(times) if times else None
            max_time = max(times) if times else None
            is_tie = (min_time is not None and max_time is not None and abs(max_time - min_time) < 0.001)

            for dev in devices:
                if sec in dev["sections"] and t in dev["sections"][sec]:
                    item = dev["sections"][sec][t]
                    ms = item["median_ms"]
                    allocs = item["allocs"]

                    is_fastest = (ms is not None and min_time is not None and abs(ms - min_time) < 0.001 and not is_tie and len(times) > 1)
                    badge = '<span class="fastest-badge">Fastest</span>' if is_fastest else ''

                    val_str = f"<strong>{ms:.2f} ms</strong> {badge}" if ms is not None else '<span class="na">&mdash;</span>'
                    alloc_str = f'<div class="alloc-sub">{allocs:,} allocs</div>' if allocs is not None else ''
                    cell_class = ' class="fastest-cell"' if is_fastest else ''
                    row_cells.append(f"<td{cell_class}>{val_str}{alloc_str}</td>")
                else:
                    row_cells.append('<td><span class="na">&mdash;</span></td>')

            table_rows.append(f"<tr>{''.join(row_cells)}</tr>")

        th_headers = "".join([f"<th>{html.escape(d['device_name'])}</th>" for d in devices])
        sections_html.append(f"""
        <div class="card section-card" id="section-{sec_idx}">
            <div class="section-header">
                <h2>{html.escape(sec)}</h2>
                <span class="badge-count">{len(test_set)} benchmarks</span>
            </div>

            <div class="chart-legend">
                {legend_html}
            </div>

            <div class="chart-box">
                {sec_chart_svg}
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
    html_out = html_out.replace("__LEGEND_HTML__", legend_html)
    html_out = html_out.replace("__CROSS_CHART_SVG__", cross_chart_svg)
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
