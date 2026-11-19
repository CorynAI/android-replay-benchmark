"""Summarize matched repetitions; CPU percentages use one logical core as 100%."""
import csv
import html
import json
import statistics
from pathlib import Path

MODES = ("off", "recording", "capture")
ORDERS = (
    ("off", "recording", "capture"), ("capture", "off", "recording"), ("recording", "capture", "off"),
    ("capture", "recording", "off"), ("recording", "off", "capture"), ("off", "capture", "recording"),
)
FIELDS = {"cpuPercentOfOneCore": 1, "pssMiB": 1024, "javaHeapMiB": 1048576, "nativeHeapMiB": 1048576}


def values(row):
    return dict(cpuPercentOfOneCore=row["cpuPercentOfOneCore"], pssMiB=row["end"]["pssKiB"] / 1024,
                javaHeapMiB=row["end"]["javaHeapBytes"] / 1048576, nativeHeapMiB=row["end"]["nativeHeapBytes"] / 1048576)


def summarize(rows):
    output = {}
    modes = MODES
    if rows and all(not row.get("sdkIncluded", True) for row in rows):
        modes = ("off",)
    for scene in sorted({r["scenario"] for r in rows}):
        groups = {mode: {r["repeat"]: values(r) for r in rows if r["scenario"] == scene and r["mode"] == mode} for mode in modes}
        pairs = set.intersection(*(set(group) for group in groups.values()))
        if not pairs:
            continue
        scene_result = {"pairedRepetitions": len(pairs), "modes": {}, "pairedDeltasVsOff": {}}
        for mode in modes:
            scene_result["modes"][mode] = {field: statistics.median(groups[mode][rep][field] for rep in pairs) for field in FIELDS}
            if mode == "off":
                continue
            scene_result["pairedDeltasVsOff"][mode] = {}
            for field in FIELDS:
                deltas = [groups[mode][rep][field] - groups["off"][rep][field] for rep in sorted(pairs)]
                scene_result["pairedDeltasVsOff"][mode][field] = dict(median=statistics.median(deltas), minimum=min(deltas), maximum=max(deltas))
        output[scene] = scene_result
    return output


def write_reports(directory, rows):
    directory = Path(directory)
    summary = summarize(rows)
    (directory / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    with (directory / "measurements.csv").open("w", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=["runId", "scenario", "mode", "repeat", "wallMs", "cpuMs", *FIELDS])
        writer.writeheader()
        for row in rows:
            writer.writerow({**{field: row[field] for field in ("runId", "scenario", "mode", "repeat", "wallMs", "cpuMs")}, **values(row)})
    lines = ["<!doctype html><meta charset='utf-8'><title>Coryn benchmark</title>",
             "<style>body{font:16px system-ui;margin:40px;max-width:1100px}table{border-collapse:collapse;width:100%}td,th{border:1px solid #ddd;padding:10px;text-align:left}</style>",
             "<h1>Coryn benchmark</h1><p>CPU: 100% = one logical core. RAM: PSS. Heap values are included in RAM; do not add them.</p>",
             "<p>Fresh processes; matched repetitions; identical APK and workload. The live meter runs in every mode. Memory snapshots use no forced GC.</p>"]
    if rows:
        row = rows[0]
        build = f"SDK {row.get('sdkVersion', '?')}" if row.get("sdkIncluded", True) else "No Coryn dependencies"
        description = f"{row.get('device', 'Unknown device')} · Android API {row.get('androidApi', '?')} · {build}"
        lines.append("<p>" + html.escape(description) + "</p>")
        if row.get("networkMode") == "mock":
            lines.append("<p><strong>In-process HTTP simulation.</strong> SDK request bodies are serialized and discarded locally. "
                         "Mock handling is included; sockets, DNS, TLS exchanges and production network/server costs are excluded.</p>")
        if row.get("availableProcessors"):
            lines.append(f"<p>One-core CPU basis; {int(row['availableProcessors'])} logical processors available on this phone.</p>")
        if row.get("sdkIncluded", True):
            lines.append("<p>CPU covers the active workload after warm-up. Cold initialization, recording finalization and post-stop upload CPU are excluded.</p>")
        if any(row.get("thermalStatusBefore") is None or row.get("thermalStatusAfter") is None for row in rows):
            lines.append("<p>Thermal status was unavailable for one or more runs; thermal stability was not verified for those runs.</p>")
    metadata_path = directory / "metadata.json"
    if metadata_path.exists():
        metadata = json.loads(metadata_path.read_text())
        configuration = {key: metadata[key] for key in ("apkSha256", "warmupMs", "measureMs", "fps", "networkMode", "schemaVersion") if key in metadata}
        lines.append("<details><summary>Test configuration and APK fingerprint</summary><pre>" +
                     html.escape(json.dumps(configuration, indent=2)) + "</pre></details>")
    for scene, result in summary.items():
        lines.append(f"<h2>{html.escape(scene)} · {result['pairedRepetitions']} matched repetitions</h2><table><tr><th>Mode</th><th>CPU %</th><th>PSS MiB</th><th>Java MiB</th><th>Native MiB</th></tr>")
        for mode, metrics in result["modes"].items():
            lines.append("<tr><td>" + mode + "</td>" + "".join(f"<td>{metrics[field]:.2f}</td>" for field in FIELDS) + "</tr>")
        if not result["pairedDeltasVsOff"]:
            lines.append("</table><p>Baseline only: this dataset contains no SDK comparison.</p>")
            continue
        lines.append("</table><p>Paired differences versus SDK uninitialized (median [min, max]):</p><ul>")
        for mode, deltas in result["pairedDeltasVsOff"].items():
            cpu = deltas["cpuPercentOfOneCore"]
            ram = deltas["pssMiB"]
            lines.append(f"<li>{mode}: CPU {cpu['median']:+.2f} percentage points [{cpu['minimum']:+.2f}, {cpu['maximum']:+.2f}]; "
                         f"PSS {ram['median']:+.2f} MiB [{ram['minimum']:+.2f}, {ram['maximum']:+.2f}].</li>")
            if cpu["minimum"] <= 0:
                lines.append("<li>CPU comparison needs caution: negative differences or a range crossing zero do not demonstrate an SDK speedup.</li>")
        lines.append("</ul>")
    lines.append("<p>These are short-window measurements, not peak memory, battery consumption, a CPU ceiling, or a guarantee for other apps/devices.</p>")
    (directory / "report.html").write_text("\n".join(lines))


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    write_reports(args.directory, json.loads((args.directory / "results.json").read_text()))
