# Retrec benchmark

A standalone Android reference app and AndroidX Macrobenchmark tests for the Coryn SDK.
The project is maintained separately from the recording SDK at [CorynAI/android-replay-benchmark](https://github.com/CorynAI/android-replay-benchmark).

Four fixed native workloads make comparisons repeatable: a static screen, a scrolling text list, a scrolling image list, and Canvas animation.
CPU, RAM/PSS, Java heap and native heap stay visible. The UI follows the dashboard's lavender surfaces, rounded cards and navy action buttons.

## Run from Android Studio

The repository includes three shared **Android App** configurations in `.run/`:

* **Full recording** - SDK with screen text and visuals; the default configuration.
* **SDK - captures off** - SDK recording without built-in screen text or visual captures.
* **NO SDK** - the separate `noSdk` app target, with no Coryn dependencies.

Sync the project, choose a configuration and a device in the normal Android Studio toolbar, and press **Run**.
The configurations install and launch their app through Android Studio. A control Activity sets the requested mode and local simulation before starting
a fresh workload process. The same fixed workloads and meter are shared by both app targets.

`app` defaults to its `sdk` flavor; `viewer` is an optional development flavor. Select a `release` build variant for performance measurements,
or `debug` for development and the debugger. Debug readings are not comparable with the minified release reference results.
The SDK-enabled app also defaults to full recording on a fresh install. Use the app's mode chooser to configure a hosted test project instead.

## Build with and without Coryn

Clone this repository and open it in Android Studio, or use its Gradle wrapper:

```sh
git clone https://github.com/CorynAI/android-replay-benchmark.git
cd android-replay-benchmark

# Recorder included; optional Log Viewer excluded.
./gradlew :app:assembleSdkRelease

# No Coryn artifacts or their transitive dependencies.
./gradlew :noSdk:assembleRelease

# Optional development Log Viewer, for installation-size comparison.
./gradlew :app:assembleViewerRelease
```

Requirements: JDK 17 or newer supported by Gradle 9.3.1 (tested with JDK 21), Android SDK Platform 37, and an SDK location in `ANDROID_HOME` or `local.properties`.
See the [AGP compatibility requirements](https://developer.android.com/build/releases/agp-9-1-0-release-notes).
Android 8.0/API 26 is the minimum phone version. Use a physical phone for performance measurements.

The SDK version is pinned in `versions.toml`. Artifacts resolve from `https://nexus.retrec.io/repository/android-public`.
Outputs are under `build/app/outputs/apk/{sdk,viewer}/release/` and `build/noSdk/outputs/apk/release/`. Release builds are minified, non-debuggable and shell-profileable, with development signing.
These are test APKs. Plain and SDK application IDs differ, so both can be installed together.

```sh
adb -s YOUR_SERIAL install -r build/app/outputs/apk/sdk/release/app-sdk-release.apk
adb -s YOUR_SERIAL install -r build/noSdk/outputs/apk/release/noSdk-release.apk

# Inspect the dependency-free variant.
./gradlew :noSdk:dependencies --configuration releaseRuntimeClasspath
```

If an earlier build has a different signing certificate, export its results and uninstall that test app before installing your build.

## Measure on the phone

Open **Retrec benchmark**, choose a mode and select a workload:

* **SDK off:** recorder packaged but never initialized in this fresh process.
* **Recording · UI capture off:** initialized recording with `CorynUiCapturing.DISABLED`; built-in screen text and visuals are disabled.
* **Full recording:** initialized recording with text and visuals. Other options follow the selected SDK's defaults.

Tap **Measure 30 sec** for a 20-second warm-up and a 30-second process CPU interval. The app then takes a fresh memory snapshot, stops recording and
validates simulated uploads outside that CPU interval. Select **Change mode** for another fresh process; recording cannot restart in an initialized process.

The default **Local simulation** works entirely on the phone: no account, server or port forwarding is needed.
To test a real deployment, enable **Use hosted test project** and enter its test API key. Leave the endpoint blank for the SDK default.
Hosted mode sends recordings to that project, including on metered networks; treat it as a separate experiment from local simulation.

The app saves JSON measurements, including both CPU bases and the recorded core count, under:

```text
/sdcard/Android/data/ai.coryn.replay.benchmark/files/results/
/sdcard/Android/data/ai.coryn.replay.benchmark.plain/files/results/
```

Use Android Studio's Device Explorer or `adb pull` to export them:

```sh
adb -s YOUR_SERIAL pull /sdcard/Android/data/ai.coryn.replay.benchmark/files/results ./results-sdk
adb -s YOUR_SERIAL pull /sdcard/Android/data/ai.coryn.replay.benchmark.plain/files/results ./results-plain
```

Compare repeated matched runs from one SDK APK; the plain APK additionally changes dependencies, shrinking and startup behavior.
Keep the same data, refresh rate and timing, rotate mode order across repetitions, and keep the phone foreground, cool and otherwise unused.
Repeatable workloads still have timing and GC variance. Negative differences do not demonstrate an SDK speedup.

## AndroidX Macrobenchmark: frames and traces

The separate Kotlin `tests` APK uses `FrameTimingMetric`, full compilation and UI Automator. Each iteration prepares a fresh SDK mode, waits for recording
readiness, warms up outside the measured section, then runs the workload and waits for completion. No account or server setup is required.

```sh
ANDROID_SERIAL=YOUR_SERIAL ./gradlew :tests:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.mode=capture
```

Modes: `off`, `recording`, `capture`. Run each mode for a comparison. Optional arguments: `iterations` (default 5), `warmupMs` (20000), `measureMs` (30000).
AndroidX writes results and Perfetto traces under `build/tests/outputs/`; the app-side JSON snapshots remain in the device directory above.
The measured test block includes waiting for finalization; use the app's CPU-window fields for its active-workload CPU interval.
Run tests separately from interactive measurements because both need exclusive device access.

## How local simulation works

The benchmark build plugin attaches an application interceptor to the SDK HTTP client factory in SDK/viewer variants. Production SDK artifacts are unchanged.
The interceptor supplies policy responses, consumes request bodies into a counted discard sink, and returns successful upload responses. Recording, capture,
encoding, storage and request serialization still run. Counters validate sessions, recording chunks and image parts.

The mock origin `https://benchmark.invalid/api/` is never resolved or contacted when the hook is active. Missing hooks fail recording validation.
Simulation excludes sockets, DNS, TLS, internet latency, server processing and network energy. Mock handling is included in app CPU/RAM.
Cold initialization, finalization and post-stop upload work are outside the app's timed CPU interval. The Log Viewer is excluded from runtime reference tests.

## Reading the numbers

* **CPU:** `100 × process CPU milliseconds / (elapsed milliseconds × available logical cores)`. All available core-time is 100%.
  The live label shows the denominator, for example `CPU · 8 cores`. A one-core 40% reading becomes 5% on eight cores.
  JSON exports `cpuPercentOfAllCores` and retains `cpuPercentOfOneCore`. Cores are weighted equally; this is not a battery estimate.
* **RAM/PSS:** proportional resident memory, including shared pages proportionally; sampled about every five seconds and freshly at the end of a measurement.
* **Java heap:** `Runtime.totalMemory() - Runtime.freeMemory()`, without benchmark-forced GC.
* **Native heap:** `Debug.getNativeHeapAllocatedSize()`.

Do not add heap figures to PSS. Heap snapshots include allocations a later GC can reclaim. PSS is a sampled working set, not peak or long-session memory.
The live meter runs in every mode and its card is masked in captures.
Historical [Pixel 7 evidence](https://docs.coryn.ai/android/overhead) retains its frozen inputs; current reports normalize those original CPU readings by the recorded core count.

## Benchmarking a client's app

The reference app measures its View/Canvas workloads. For a client's app, adapt the `tests` module, target its app module, and provide a minified,
non-debuggable, profileable benchmark build. Add a benchmark-only mode hook before SDK initialization, stable login/data, and a UI Automator journey through
the client's screens. Use stable resource IDs or exposed Compose tags. Put navigation in the measured block when testing transitions.

The reference app supplies its own process sampler, JSON result protocol and mock bridge. Adapt those app-side hooks for CPU/PSS/heap snapshots.
The build plugin targets this reference Application; do not apply it unchanged to another Application or a production build.
Frame-timing tests can use the client's normal test-project backend. These native workloads do not measure Compose, WebView, Flutter or React Native rendering.

## Unit checks

```sh
./gradlew :app:testSdkDebugUnitTest
./gradlew -p build-logic test
```

Kotlin tests cover request-body consumption and SDK-client injection for static and companion factories.
