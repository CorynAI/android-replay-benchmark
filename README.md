# Retrec benchmark

An independent Android benchmark project that consumes versioned Coryn SDK artifacts. It contains a reference app, an AndroidX Macrobenchmark test APK,
and a repeatable CPU/memory comparison runner. It is maintained separately from the recording SDK.

The reference app has four fixed workloads: static screen, scrolling text list, scrolling image list, and Canvas animation. CPU, RAM/PSS, Java heap and
native heap stay visible. The native UI follows the dashboard's lavender surfaces, rounded cards and navy action buttons.

## Run the reference app

Install the SDK APK from [CorynDocs](https://docs.coryn.ai/android/overhead/benchmark), open **Retrec benchmark**, choose a recording mode, and select a workload.
The default **Local simulation** works entirely on the phone: no account, computer, HTTP server or ADB port forwarding is needed.

* **SDK off:** recorder included but never initialized in this fresh process.
* **Recording · UI capture off:** initialized recording with `CorynUiCapturing.DISABLED`.
* **Full recording:** initialized recording with text and visuals. Other recording options follow the selected SDK's defaults.

Use **Measure 30 sec** for a 20-second warm-up and a 30-second CPU interval. The app then samples memory, stops recording and validates simulated upload
requests outside the timed CPU interval. Results are saved under `/sdcard/Android/data/ai.coryn.replay.benchmark/files/results/`.
Select **Change mode** for another fresh recording; the SDK does not restart recording in an initialized process.

To exercise your real test project, turn on **Use hosted test project** and enter its API key. Leave its endpoint blank for the SDK default.
Hosted mode sends recordings to that project, including on metered networks. It is a separate experiment from local simulation.

## Build with and without Coryn

Requirements: JDK 17 or newer supported by Gradle 9.3.1 (tested with JDK 21), Android SDK Platform 37, and an SDK location in `ANDROID_HOME` or `local.properties`.
See the [AGP 9.1.1 compatibility requirements](https://developer.android.com/build/releases/agp-9-1-0-release-notes). Android 8.0/API 26 is the minimum phone version. Use a physical phone for performance measurements.

```sh
# Recorder included, optional Log Viewer excluded.
./gradlew :app:assembleSdkRelease

# No Coryn artifacts or their transitive dependencies.
./gradlew :app:assemblePlainRelease

# Optional development Log Viewer, for installation-size comparison.
./gradlew :app:assembleViewerRelease
```

The SDK version is pinned in `versions.toml`. Artifacts resolve from `https://nexus.retrec.io/repository/android-public`.
Outputs are under `build/app/outputs/apk/{sdk,plain,viewer}/release/`. Release builds are minified, non-debuggable and shell-profileable, with development signing.
They are test APKs. Plain and SDK application IDs differ, so both can be installed together. Export results and uninstall the corresponding test app if a
local build reports a signing-certificate mismatch with an existing download.

```sh
adb -s YOUR_SERIAL install -r build/app/outputs/apk/sdk/release/app-sdk-release.apk
adb -s YOUR_SERIAL install -r build/app/outputs/apk/plain/release/app-plain-release.apk
./gradlew :app:dependencies --configuration plainReleaseRuntimeClasspath
```

## Repeatable CPU and memory comparison

Python 3.10+ and `adb` are required. Run from this repository root:

```sh
python3 tools/run.py --serial YOUR_SERIAL \
  --apk build/app/outputs/apk/sdk/release/app-sdk-release.apk --output results/my-phone

# Separate dataset containing no Coryn dependencies.
python3 tools/run.py --serial YOUR_SERIAL --plain \
  --apk build/app/outputs/apk/plain/release/app-plain-release.apk --output results/my-phone-plain
```

The runner installs and fingerprints the supplied APK, compiles it with ART `speed`, checks exclusive instrumentation access, and uses fresh processes.
It runs six counterbalanced repetitions of each workload in the three runtime modes, with a 15-second warm-up, 20-second measurement and 5 FPS capture
ceiling. Allow roughly 45–60 minutes for the default campaign. It does not start a server or change ADB reverse mappings.

Adjust `--scenarios`, `--repeats`, `--warmup-ms`, `--measure-ms` and `--fps` as needed. Completed runs are retained; the same output directory resumes only when
the APK, device and protocol settings match. Keep the phone unlocked, foreground and available. Avoid other interaction, tests or charging changes.

Foreground, rendered workload, recording readiness, available thermal status and simulated recording/capture requests are validated. Unsupported thermal
status is marked unverified. In SDK-off mode, any SDK transport activity fails the run. Full capture requires consumed image upload parts.

Outputs include `report.html`, `results.json`, `measurements.csv`, `summary.json`, `metadata.json` and the exact `measured.apk`.
Compare matched differences from one SDK APK; a separate plain APK also changes dependency/startup and shrinking effects.

## How network simulation works

The benchmark's build plugin attaches an application interceptor to the SDK's HTTP client factory in SDK and viewer variants. It works with the published
SDK's obfuscated factory and the source factory signature; production SDK artifacts are unchanged. This hook belongs to this benchmark build.

The interceptor supplies SDK policy responses, writes request bodies into a counted discard sink, and returns successful upload responses. Recording,
UI capture, encoding, file storage and request-body serialization still run. Counters verify that sessions, recording chunks and image parts were consumed.
The mock origin is `https://benchmark.invalid/api/`; it is never resolved or contacted when the hook is active. Missing hooks fail recording validation.

**Simulation excludes sockets, DNS, TLS exchanges, internet latency, network energy and server processing.** Mock response handling and bookkeeping run in
the app process and are included in its readings. Cold initialization, finalization and post-stop upload-body work are outside the active-workload CPU window.
The Log Viewer is excluded from runtime reference measurements. Lower FPS targets and adaptive capture pacing can reduce the effective capture cadence.

## AndroidX Macrobenchmark: frames and traces

The separate `tests` APK uses `FrameTimingMetric`, full compilation and UI Automator. It prepares a fresh mode, waits for recording readiness, warms up outside
the measured section, runs the workload and waits for completion. No account or server setup is required.

```sh
ANDROID_SERIAL=YOUR_SERIAL ./gradlew :tests:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.mode=capture
```

Mode arguments: `off`, `recording`, `capture`. Optional arguments: `iterations` (5), `warmupMs` (20000), `measureMs` (30000).
AndroidX writes results and Perfetto traces under `build/tests/outputs/`. Run this separately from `tools/run.py`; both require exclusive device access.

## Benchmarking a client's app

Running the reference APK requires no app integration. It measures the supplied native View/Canvas workloads, not the client's screens.

For a client app, adapt the `tests` module into its Android project and point `targetProjectPath` at the app module. Provide a non-debuggable, minified,
profileable benchmark build, a benchmark-only SDK-mode hook **before SDK initialization**, and deterministic test data/login state. Write a UI Automator
journey using stable resource IDs or exposed Compose tags. Deep links can prepare an isolated screen; put navigation in the measured block for transition tests.

The reference app supplies its own process sampler, JSON result protocol and mock bridge. Those are app-side work in addition to the external test driver;
a client integration must adapt these hooks too if it needs the same CPU/PSS/heap report. The build plugin's bridge currently targets this reference app.
Do not apply it unchanged to a different Application class or a production build. Frame-timing tests can use the client's normal test-project backend.

## Reading the numbers

* **CPU:** process CPU milliseconds / elapsed milliseconds. 100% is one logical core; the live `1/N cores` label gives the phone's available logical processor
  count. A multithreaded process can exceed 100%. This is not battery use or a performance-weighted percentage of the whole phone.
* **RAM/PSS:** proportional resident memory, including shared pages proportionally; sampled about every five seconds, plus a fresh end-of-window sample.
* **Java heap:** `Runtime.totalMemory() - Runtime.freeMemory()`, without forced GC.
* **Native heap:** `Debug.getNativeHeapAllocatedSize()`.

Do not add heap figures to PSS. Heap snapshots include allocations a later GC can reclaim. PSS is a sampled working set, not peak or long-session memory.
The live meter is included in every mode and its entire card is masked in captures. Negative differences do not show that enabling the SDK makes apps faster.
These workloads do not cover Compose, WebView, SurfaceView, cross-platform runtimes or custom state providers.

Historical Pixel 7 reports retain their original frozen APKs and source archive in CorynDocs. Keep results from their loopback-server protocol separate
from the current in-process simulation protocol; UI layout, SDK build and transport differences can change readings and APK size.

## Checks

```sh
python3 -m unittest discover -s tools -v
./gradlew :app:testSdkDebugUnitTest
./gradlew -p build-logic test
```

The checks cover pairing/counterbalancing, protocol validation, request-body consumption and the SDK-client injection for static and companion factories.
