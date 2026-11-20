package ai.coryn.replay.benchmark

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class BenchmarkActivity : Activity() {
    private val impl = BenchmarkActivityImpl(this)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        impl.onCreate()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        impl.arrangeLayout()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        impl.measureFromIntent(intent)
    }
    override fun onDestroy() {
        impl.onDestroy()
        super.onDestroy()
    }
}

internal class BenchmarkActivityImpl(private val activity: Activity) : Choreographer.FrameCallback {
    private val style = BenchmarkStyle(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val meter = ResourceMeter()
    private val coreCount = Runtime.getRuntime().availableProcessors()
    private val application by lazy { (activity.application as BenchmarkApplication).benchmark }
    private lateinit var metrics: TextView
    private lateinit var cpuValue: TextView
    private lateinit var pssValue: TextView
    private lateinit var javaValue: TextView
    private lateinit var nativeValue: TextView
    private val scenarioButtons = mutableMapOf<String, Button>()
    private lateinit var scenarios: ScenarioController
    private var scenario = "static"
    private var phase = ""
    private var clockStart = 0L
    private var draws = 0
    private var startDraws = 0
    private var startFrames = 0
    private var start: ResourceSample? = null
    private var last: ResourceSample? = null
    private var previous: ResourceSample? = null
    private val samples = mutableListOf<ResourceSample>()
    private var transportAtStart = BenchmarkTransportSnapshot(false, 0, 0, 0, 0, 0, 0)
    private var runId = ""
    private var warmupMs = 0L
    private var running = false
    private var destroyed = false
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var metricsCard: LinearLayout
    private lateinit var workloadButtons: LinearLayout
    private lateinit var statistics: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var changeMode: Button

    fun onCreate() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root = style.page()
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft + style.dp(16), insets.systemWindowInsetTop + style.dp(16),
                insets.systemWindowInsetRight + style.dp(16), insets.systemWindowInsetBottom + style.dp(12))
            insets
        }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(style.text("Retrec benchmark", 25f, style.ink, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val modeLabel = when {
            !BuildConfig.HAS_CORYN -> "NO SDK"
            application.config.mode == "off" -> "SDK OFF"
            application.config.mode == "recording" -> "SDK ON · Captures OFF"
            else -> "SDK ON · Captures ON"
        }
        val sdkActive = BuildConfig.HAS_CORYN && application.config.mode != "off"
        val badgeText = if (sdkActive) style.green else style.muted
        val badgeBackground = if (sdkActive) style.greenSurface else Color.rgb(236, 238, 242)
        header.addView(style.text(modeLabel, 10f, badgeText, true).apply {
            background = style.rounded(badgeBackground, 8, Color.TRANSPARENT)
            setPadding(style.dp(10), style.dp(8), style.dp(10), style.dp(8))
            letterSpacing = 0.06f
        })
        root.addView(header)
        style.gap(root, 14)
        body = LinearLayout(activity)
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        metricsCard = style.card().apply { setPadding(style.dp(12), style.dp(12), style.dp(12), style.dp(12)) }
        metrics = style.text(resourceHeading(), 13f, style.ink, true).apply {
            id = R.id.metrics
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        metricsCard.addView(metrics)
        style.gap(metricsCard, 10)
        statistics = LinearLayout(activity)
        cpuValue = statistic(statistics, "CPU · $coreCount cores", 17f)
        pssValue = statistic(statistics, "RAM / PSS", 17f)
        javaValue = statistic(statistics, "Java heap", 17f)
        nativeValue = statistic(statistics, "Native heap", 17f)
        metricsCard.addView(statistics)
        workloadButtons = LinearLayout(activity)
        for ((name, identifier) in listOf("static" to R.id.scenario_static, "list" to R.id.scenario_list,
            "images" to R.id.scenario_images, "animation" to R.id.scenario_animation)) {
            scenarioButtons[name] = style.button(name.replaceFirstChar(Char::uppercase), false).apply {
                id = identifier
                textSize = 12f
                setPadding(0, 0, 0, 0)
                setOnClickListener { if (!running) show(name) }
            }
        }
        content = FrameLayout(activity).apply {
            id = R.id.scenario_content
            background = style.rounded(Color.WHITE, 16, style.border)
            clipToOutline = true
        }
        scenarios = ScenarioController(activity, content)
        changeMode = style.button("Change mode", true).apply {
            setOnClickListener {
                if (!running) { activity.startActivity(Intent(activity, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        arrangeLayout()
        activity.setContentView(root)
        root.viewTreeObserver.addOnDrawListener { draws++ }
        application.sdk.start(metricsCard)
        show(activity.intent.getStringExtra("scenario") ?: "static")
        worker.scheduleAtFixedRate({ collect() }, 0, 1, java.util.concurrent.TimeUnit.SECONDS)
        measureFromIntent(activity.intent)
    }

    fun arrangeLayout() {
        if (!::body.isInitialized) return
        val wide = style.wideLayout
        // Reparent the existing views so rotation preserves recording, workload time and samples.
        for (view in listOf(metricsCard, workloadButtons, content, changeMode) + scenarioButtons.values) {
            (view.parent as? ViewGroup)?.removeView(view)
        }
        body.removeAllViews()
        workloadButtons.removeAllViews()
        workloadButtons.orientation = LinearLayout.VERTICAL
        val columns = if (wide) 2 else 4
        scenarioButtons.values.toList().chunked(columns).forEachIndexed { index, buttons ->
            if (index > 0) style.gap(workloadButtons, 8)
            val row = LinearLayout(activity)
            buttons.forEachIndexed { column, button ->
                row.addView(button, LinearLayout.LayoutParams(0, style.dp(48), 1f).apply {
                    if (column > 0) marginStart = style.dp(6)
                })
            }
            workloadButtons.addView(row)
        }
        val values = listOf(cpuValue, pssValue, javaValue, nativeValue)
        val statisticColumns = values.map { it.parent as View }
        for (column in statisticColumns) (column.parent as? ViewGroup)?.removeView(column)
        statistics.removeAllViews()
        statistics.orientation = LinearLayout.VERTICAL
        val metricColumns = if (wide && activity.resources.configuration.screenWidthDp < 760) 2 else 4
        statisticColumns.chunked(metricColumns).forEachIndexed { index, columns ->
            if (index > 0) style.gap(statistics, 10)
            val row = LinearLayout(activity)
            columns.forEachIndexed { column, view ->
                row.addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (column > 0) marginStart = style.dp(8)
                })
            }
            statistics.addView(row)
        }
        for (value in values) value.textSize = if (wide && metricColumns == 4) 14f else 17f
        body.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        if (wide) {
            val sidebar = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            sidebar.addView(metricsCard)
            style.gap(sidebar, 14)
            val actions = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            actions.addView(workloadButtons)
            style.gap(actions, 14)
            actions.addView(changeMode, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(48)))
            sidebar.addView(ScrollView(activity).apply { addView(actions) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            val sidebarWidth = minOf(320, (activity.resources.configuration.screenWidthDp * 0.42f).toInt()).coerceAtLeast(240)
            body.addView(sidebar, LinearLayout.LayoutParams(style.dp(sidebarWidth), ViewGroup.LayoutParams.MATCH_PARENT))
            body.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = style.dp(20) })
        } else {
            body.addView(metricsCard)
            style.gap(body, 14)
            body.addView(workloadButtons)
            style.gap(body, 12)
            body.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            style.gap(body, 14)
            body.addView(changeMode, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(48)))
        }
        root.requestApplyInsets()
    }

    fun measureFromIntent(intent: Intent) {
        val requestedId = intent.getStringExtra("runId")
        if (requestedId != null && !running) {
            begin(requestedId, intent.getLongExtra("warmupMs", 20_000), intent.getLongExtra("measureMs", 30_000))
        }
    }

    private fun statistic(parent: LinearLayout, label: String, size: Float): TextView {
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(style.text(label, 10f, style.muted, true))
        style.gap(column, 4)
        val value = style.text("—", size, style.ink, true).apply { setSingleLine() }
        column.addView(value)
        parent.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return value
    }

    private fun show(name: String) {
        scenario = name
        for ((key, button) in scenarioButtons) {
            val selected = key == name
            val color = if (selected) style.navy else Color.WHITE
            button.background = style.rounded(color, 10, style.border)
            val textColor = if (selected) Color.WHITE else style.ink
            button.setTextColor(textColor)
            button.isSelected = selected
        }
        scenarios.show(name)
        clockStart = SystemClock.elapsedRealtime()
        Choreographer.getInstance().removeFrameCallback(this)
        if (name != "static") { Choreographer.getInstance().postFrameCallback(this) }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (destroyed) return
        scenarios.frame(SystemClock.elapsedRealtime() - clockStart)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun collect() {
        val sample = meter.sample(false)
        handler.post {
            if (destroyed) return@post
            previous = last
            last = sample
            if (start != null) samples.add(sample)
            display(sample)
        }
    }

    private fun begin(identifier: String, warmup: Long, duration: Long) {
        require(identifier.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        require(warmup in 0..600_000 && duration in 1_000..600_000)
        running = true
        runId = identifier
        warmupMs = warmup
        val requestedAt = SystemClock.elapsedRealtime()
        phase = "Waiting for recording readiness…"
        awaitReady(requestedAt, duration)
    }

    private fun awaitReady(requestedAt: Long, duration: Long) {
        if (application.sdk.ready) {
            phase = "Warming up for ${warmupMs / 1000}s…"
            handler.postDelayed({ startMeasurement(duration) }, warmupMs)
        } else if (application.sdk.error != null || SystemClock.elapsedRealtime() - requestedAt > 60_000) {
            phase = "FAILED: recording did not become ready."
            Log.e("CorynBenchmark", "FAILED:$runId:recording-not-ready:${application.transport.snapshot()}:session=${application.sdk.sessionId != null}")
            running = false
        } else {
            handler.postDelayed({ awaitReady(requestedAt, duration) }, 200)
        }
    }

    private fun startMeasurement(duration: Long) {
        if (destroyed) return
        samples.clear()
        clockStart = SystemClock.elapsedRealtime()
        scenarios.frame(0)
        startDraws = draws
        startFrames = scenarios.frames
        transportAtStart = application.transport.snapshot()
        start = ResourceSample(SystemClock.elapsedRealtime(), Process.getElapsedCpuTime(), 0, 0, 0)
        phase = "Measuring for ${duration / 1000}s…"
        Log.i("CorynBenchmark", "STARTED:$runId")
        handler.postDelayed({ finishMeasurement() }, duration)
    }

    private fun finishMeasurement() {
        val finishWall = SystemClock.elapsedRealtime()
        val finishCpu = Process.getElapsedCpuTime()
        val began = start ?: return
        start = null
        val measuredSamples = samples.toList()
        val measuredDraws = draws - startDraws
        val measuredFrames = scenarios.frames - startFrames
        worker.execute {
            val end = meter.sample(true)
            val wall = finishWall - began.elapsedMs
            val cpu = finishCpu - began.cpuMs
            val result = BenchmarkResult(2, runId, application.config.mode, scenario, BuildConfig.HAS_CORYN, BuildConfig.CORYN_VERSION,
                !BuildConfig.DEBUG, Build.MANUFACTURER + " " + Build.MODEL, Build.VERSION.SDK_INT, Process.myPid(),
                coreCount, application.sdk.sessionId, warmupMs, wall, cpu, cpu * 100.0 / wall, cpu * 100.0 / wall / coreCount,
                measuredDraws, measuredFrames, began, end, measuredSamples, application.config.networkMode, application.config.fps,
                transportAtStart, application.transport.snapshot())
            handler.post {
                application.sdk.stop()
                finishTransport(result, SystemClock.elapsedRealtime() + 20_000)
            }
        }
    }

    private fun finishTransport(result: BenchmarkResult, deadline: Long) {
        if (destroyed) return
        val snapshot = application.transport.snapshot()
        val mockRecording = application.config.networkMode == "mock" && application.config.mode != "off"
        val recordingAccepted = snapshot.installed && snapshot.sessions > 0 && snapshot.recordings > 0
        val captureAccepted = application.config.mode != "capture" || (snapshot.captures > 0 && snapshot.imageFiles > 0)
        if (mockRecording && !(recordingAccepted && captureAccepted)) {
            if (SystemClock.elapsedRealtime() < deadline) {
                phase = "Checking recorded data…"
                handler.postDelayed({ finishTransport(result, deadline) }, 100)
            } else {
                phase = "FAILED: simulated uploads were not validated."
                Log.e("CorynBenchmark", "FAILED:$runId:mock-upload-validation:$snapshot")
                running = false
            }
            return
        }
        worker.execute {
            val directory = File(activity.getExternalFilesDir(null), "results").apply { mkdirs() }
            File(directory, "$runId.json").writeText(Json.encodeToString(result.copy(transport = snapshot)))
            Log.i("CorynBenchmark", "COMPLETE:$runId")
            handler.post {
                phase = "Complete · results saved on this device."
                metrics.contentDescription = "benchmark-complete:$runId"
                running = false
                last?.let(::display)
            }
        }
    }

    private fun resourceHeading(): String {
        val config = application.config
        return if (BuildConfig.HAS_CORYN && config.mode == "capture" && config.networkMode == "mock") {
            "Live resources · target ${config.fps} FPS"
        } else {
            "Live resources"
        }
    }

    private fun display(sample: ResourceSample) {
        val before = previous ?: sample
        val wall = (sample.elapsedMs - before.elapsedMs).coerceAtLeast(1)
        val cpu = 100.0 * (sample.cpuMs - before.cpuMs) / wall / coreCount
        val mib = 1_048_576.0
        val finalStatus = phase.startsWith("Complete") || phase.startsWith("FAILED")
        val status = if (running || finalStatus) phase else application.sdk.error ?: phase
        if (!running && !phase.startsWith("Complete")) {
            metrics.contentDescription = if (application.sdk.ready) "sdk-ready" else "sdk-not-ready"
        }
        cpuValue.text = String.format(Locale.US, "%.1f%%", cpu)
        pssValue.text = String.format(Locale.US, "%.1f MiB", sample.pssKiB / 1024.0)
        javaValue.text = String.format(Locale.US, "%.1f MiB", sample.javaHeapBytes / mib)
        nativeValue.text = String.format(Locale.US, "%.1f MiB", sample.nativeHeapBytes / mib)
        val heading = if (status.isBlank()) resourceHeading() else status
        if (metrics.text.toString() != heading) metrics.text = heading
    }

    fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(this)
        worker.shutdownNow()
    }
}
