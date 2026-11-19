"""Run counterbalanced, same-APK overhead comparisons on one attached physical device."""
import argparse
import datetime
import hashlib
import json
import queue
import shutil
import subprocess
import threading
import time
from pathlib import Path

from report import ORDERS, write_reports

PACKAGE = "ai.coryn.replay.benchmark"


def is_benchmark_foreground(state, package):
    components = (package + "/.BenchmarkActivity", package + "/" + PACKAGE + ".BenchmarkActivity")
    return any(any(component in line for component in components) for line in state.splitlines()
               if "ResumedActivity" in line or "topResumedActivity" in line)


def thermal_status(output):
    for line in output.splitlines():
        if line.strip().startswith("Thermal Status:"):
            return int(line.split(":", 1)[1].strip().split()[0])
    return None


def validate_transport(row, mode):
    counts = row["transport"]
    if mode == "off":
        if any(counts.values()):
            raise RuntimeError("Uninitialized mode used the SDK transport")
    elif not counts["installed"] or not counts["sessions"] or not counts["recordings"] or not counts["bytes"]:
        raise RuntimeError(f"No validated mock recording upload: {counts}")
    if mode == "capture" and not (counts["captures"] and counts["imageFiles"]):
        raise RuntimeError("No validated mock capture upload")
    if mode != "capture" and (counts["captures"] or counts["imageFiles"]):
        raise RuntimeError("Unexpected visual captures")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--plain", action="store_true", help="Measure the app without Coryn dependencies (off mode only)")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--adb", default=shutil.which("adb") or "adb")
    parser.add_argument("--repeats", type=int, default=6)
    parser.add_argument("--warmup-ms", type=int, default=15000)
    parser.add_argument("--measure-ms", type=int, default=20000)
    parser.add_argument("--fps", type=float, default=5.0)
    parser.add_argument("--scenarios", nargs="+", choices=("static", "list", "images", "animation"), default=["static", "list", "images", "animation"])
    args = parser.parse_args()
    package = PACKAGE + ".plain" if args.plain else PACKAGE
    sdk_included = not args.plain
    if args.serial.startswith("emulator-") or args.repeats < 1 or not 0 <= args.warmup_ms <= 600000 or not 1000 <= args.measure_ms <= 600000:
        parser.error("Select a physical device, positive repetitions and valid measurement durations")
    if not 0.5 <= args.fps <= 10:
        parser.error("FPS must be between 0.5 and 10")
    if len(set(args.scenarios)) != len(args.scenarios):
        parser.error("Duplicate scenarios")
    args.output.mkdir(parents=True, exist_ok=True)

    def adb(*values, timeout=90):
        result = subprocess.run([args.adb, "-s", args.serial, *map(str, values)], capture_output=True, text=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        return result.stdout

    def write(name, value):
        temporary = args.output / (name + ".tmp")
        temporary.write_text(json.dumps(value, indent=2) + "\n")
        temporary.replace(args.output / name)

    def thermal():
        return thermal_status(adb("shell", "dumpsys", "thermalservice"))

    def available():
        state = adb("shell", "dumpsys", "activity")
        if "ActiveInstrumentation{" in state:
            raise RuntimeError("Another instrumentation test is using the device")

    def foreground():
        state = adb("shell", "dumpsys", "activity", "activities")
        return is_benchmark_foreground(state, package)

    apk_hash = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    settings = dict(serial=args.serial, apkSha256=apk_hash, warmupMs=args.warmup_ms, measureMs=args.measure_ms, fps=args.fps, networkMode="mock", schemaVersion=2)
    metadata_path = args.output / "metadata.json"
    if metadata_path.exists():
        saved = json.loads(metadata_path.read_text())
        if any(saved.get(key) != value for key, value in settings.items()):
            raise RuntimeError("Existing results have different build/device/settings; use a new output directory")
    else:
        write("metadata.json", {**settings, "fingerprint": adb("shell", "getprop", "ro.build.fingerprint").strip(),
                                "startedAtUtc": datetime.datetime.now(datetime.timezone.utc).isoformat()})
        shutil.copyfile(args.apk, args.output / "measured.apk")
    rows = json.loads((args.output / "results.json").read_text()) if (args.output / "results.json").exists() else []
    completed = {(row["scenario"], row["mode"], row["repeat"]) for row in rows}
    available()
    installation = adb("install", "-r", args.apk, timeout=180)
    if "Success" not in installation:
        raise RuntimeError(installation)
    installed_paths = adb("shell", "pm", "path", package).splitlines()
    base_apk = next((line.removeprefix("package:") for line in installed_paths if line.endswith("/base.apk")), None)
    if base_apk is None or adb("shell", "sha256sum", base_apk).split()[0] != apk_hash:
        raise RuntimeError("The installed benchmark package does not match --apk; select the correct variant and --plain flag")
    compilation = adb("shell", "cmd", "package", "compile", "-m", "speed", "-f", package, timeout=180)
    if "Success" not in compilation:
        raise RuntimeError(compilation)
    try:
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        adb("shell", "wm", "dismiss-keyguard")
        for repeat in range(1, args.repeats + 1):
            for scenario in args.scenarios:
                for mode in ORDERS[(repeat - 1) % len(ORDERS)]:
                    if args.plain and mode != "off":
                        continue
                    if (scenario, mode, repeat) in completed:
                        continue
                    available()
                    for _ in range(60):
                        before_thermal = thermal()
                        if before_thermal in (None, 0):
                            break
                        print("Waiting for thermal status NONE", flush=True)
                        time.sleep(10)
                    if before_thermal not in (None, 0):
                        raise RuntimeError("Device remains thermally constrained")
                    run_id = f"{scenario}-{mode}-{repeat}-{int(time.time())}"
                    adb("shell", "am", "force-stop", package)
                    configured = adb("shell", "am", "broadcast", "--include-stopped-packages", "--receiver-foreground", "-n", package + "/" + PACKAGE + ".ModeReceiver",
                                     "--es", "mode", mode, "--es", "networkMode", "mock", "--ed", "fps", args.fps)
                    if f"CONFIGURED:{mode}" not in configured:
                        raise RuntimeError(configured)
                    adb("shell", "am", "force-stop", package)
                    messages = queue.Queue()
                    stream = subprocess.Popen([args.adb, "-s", args.serial, "logcat", "-T", "1", "-v", "raw", "-s", "CorynBenchmark:I"],
                                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)

                    def read_messages():
                        for message in stream.stdout:
                            messages.put(message.strip())

                    reader = threading.Thread(target=read_messages, daemon=True)
                    reader.start()
                    try:
                        adb("shell", "am", "start", "-W", "-n", package + "/" + PACKAGE + ".BenchmarkActivity", "--es", "scenario", scenario,
                            "--es", "runId", run_id, "--el", "warmupMs", args.warmup_ms, "--el", "measureMs", args.measure_ms)
                        print(f"{repeat}/{args.repeats} {scenario} {mode}: warming up / measuring", flush=True)
                        deadline = time.monotonic() + (args.warmup_ms + args.measure_ms) / 1000 + 95
                        started = False
                        finished = False
                        while time.monotonic() < deadline:
                            try:
                                message = messages.get(timeout=1)
                            except queue.Empty:
                                continue
                            if message == "STARTED:" + run_id:
                                started = True
                                if not foreground():
                                    raise RuntimeError("App lost foreground")
                            if message.startswith("FAILED:" + run_id):
                                raise RuntimeError(message)
                            if message == "COMPLETE:" + run_id:
                                finished = True
                                break
                        if not started or not finished:
                            raise RuntimeError("Missing completed measurement: " + run_id)
                        path = f"/sdcard/Android/data/{package}/files/results/{run_id}.json"
                        row = json.loads(adb("shell", "cat", path))
                        if row.get("schemaVersion") != 2 or row.get("networkMode") != "mock":
                            raise RuntimeError("Use the current serverless benchmark APK (schema 2)")
                        if abs(row["captureFpsTarget"] - args.fps) > 0.00001:
                            raise RuntimeError("Capture policy mismatch")
                        if not row["releaseBuild"] or row["sdkIncluded"] != sdk_included or row["mode"] != mode or row["scenario"] != scenario:
                            raise RuntimeError("Wrong benchmark build/mode/scenario")
                        # Android's uptime deadline and elapsed-realtime readings can round to adjacent milliseconds.
                        if row["wallMs"] < args.measure_ms - 5 or row["wallMs"] > args.measure_ms + 2000 or not row["samples"]:
                            raise RuntimeError("Invalid measurement duration/samples")
                        active_workload_stopped = scenario != "static" and row["workloadFrames"] <= 0
                        if active_workload_stopped or row["draws"] <= 0:
                            raise RuntimeError("Workload did not render")
                        if (row["sessionId"] is not None) != (mode != "off"):
                            raise RuntimeError("Recording readiness mismatch")
                        validate_transport(row, mode)
                        after_thermal = thermal()
                        if not foreground() or after_thermal not in (None, 0):
                            raise RuntimeError("Foreground/thermal validation failed")
                        row.update(repeat=repeat, apkSha256=apk_hash,
                                   thermalStatusBefore=before_thermal, thermalStatusAfter=after_thermal,
                                   utc=datetime.datetime.now(datetime.timezone.utc).isoformat())
                        rows.append(row)
                        write("results.json", rows)
                        write_reports(args.output, rows)
                        print(f"PASS CPU={row['cpuPercentOfOneCore']:.2f}% PSS={row['end']['pssKiB']/1024:.2f} MiB", flush=True)
                    finally:
                        stream.terminate()
                        stream.wait(timeout=10)
                        reader.join(timeout=2)
                        adb("shell", "am", "force-stop", package)
        print(f"DONE: {len(rows)} verified windows; report: {args.output / 'report.html'}", flush=True)
    finally:
        adb("shell", "am", "force-stop", package)



if __name__ == "__main__":
    main()
