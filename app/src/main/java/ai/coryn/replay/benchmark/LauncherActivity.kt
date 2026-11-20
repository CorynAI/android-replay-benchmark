package ai.coryn.replay.benchmark

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Button
import android.widget.ScrollView
import android.widget.SeekBar
import kotlin.math.roundToInt

class LauncherActivity : Activity() {
    private val impl = LauncherActivityImpl(this)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        impl.onCreate()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        impl.arrangeLayout()
    }
}

internal class LauncherActivityImpl(private val activity: Activity) {
    private val style = BenchmarkStyle(activity)
    private lateinit var panels: LinearLayout
    private var capturePanel: LinearLayout? = null
    private lateinit var modes: LinearLayout
    private lateinit var notes: LinearLayout
    fun onCreate() {
        val store = BenchmarkConfigStore(activity)
        val requestedMode = activity.intent.getStringExtra("benchmarkMode")
        val allowedModes = if (BuildConfig.HAS_CORYN) setOf("off", "recording", "capture") else setOf("off")
        if (requestedMode != null && requestedMode in allowedModes) {
            openWorkload(store, BenchmarkConfig(requestedMode, "", "", "mock", store.read().fps))
            return
        }
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val config = store.read()
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(style.dp(20), style.dp(16), style.dp(20), style.dp(16)) }
        content.addView(style.text("Retrec benchmark", 32f, style.ink, true))
        style.gap(content, 20)
        panels = LinearLayout(activity)
        content.addView(panels)
        val fps = SeekBar(activity).apply {
            id = R.id.target_fps
            max = 19
            progress = (config.fps * 2).roundToInt().minus(1).coerceIn(0, max)
            progressTintList = android.content.res.ColorStateList.valueOf(style.navy)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(40, 28, 36, 64))
            thumbTintList = android.content.res.ColorStateList.valueOf(style.navy)
            contentDescription = "Target capture FPS"
        }
        val fpsValue = style.text("${(fps.progress + 1) / 2.0} FPS", 14f, style.navy, true)
        fps.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                fpsValue.text = "${(progress + 1) / 2.0} FPS"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        if (BuildConfig.HAS_CORYN) {
            val capture = style.card()
            val captureRate = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            captureRate.addView(style.text("Target capture FPS", 14f, style.ink, true).apply { labelFor = R.id.target_fps },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            captureRate.addView(fpsValue)
            capture.addView(captureRate)
            capture.addView(fps, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(48)))
            style.gap(capture, 4)
            capture.addView(style.text("0.5–10 FPS · full recording only. Adaptive limits may lower the actual rate on low-end devices.", 12f, style.muted, false))
            capturePanel = capture
        }
        modes = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        modes.addView(style.text("Mode selection", 20f, style.ink, true))
        style.gap(modes, 6)
        modes.addView(style.text("Each mode opens a fresh workload session.", 13f, style.muted, false))
        style.gap(modes, 12)
        val status = style.text("", 13f, Color.rgb(160, 48, 70), false)
        for ((mode, label, identifier) in listOf(
            Triple("off", "SDK off\nBaseline · recorder uninitialized", R.id.mode_off),
            Triple("recording", "Recording without UI Captures\nSession context without visual replay", R.id.mode_recording),
            Triple("capture", "Full recording\nSession context and visual replay", R.id.mode_capture)
        )) {
            val titleEnd = label.indexOf('\n')
            val title = SpannableString(label).apply {
                setSpan(AbsoluteSizeSpan(12, true), titleEnd + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(Color.rgb(207, 211, 226)), titleEnd + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            modes.addView(style.button(label, true).apply {
                id = identifier
                text = title
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(style.dp(18), style.dp(14), style.dp(18), style.dp(14))
                setLineSpacing(style.dp(6).toFloat(), 1f)
                isEnabled = BuildConfig.HAS_CORYN || mode == "off"
                if (!isEnabled) alpha = 0.5f
                setOnClickListener {
                    try {
                        val targetFps = (fps.progress + 1) / 2.0
                        openWorkload(store, BenchmarkConfig(mode, "", "", "mock", targetFps))
                    } catch (error: IllegalArgumentException) {
                        status.text = error.message
                    }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(80)))
            style.gap(modes, 10)
        }
        modes.addView(status)
        notes = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        notes.addView(style.text("Live CPU, RAM and heap readings include the whole app. Compare matched runs to measure SDK overhead.",
            12f, style.muted, false))
        style.gap(notes, 10)
        notes.addView(style.text("Local simulation works on this phone. For automated comparisons, follow the source README.", 12f, style.muted, false))
        val root = style.page()
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        root.addView(ScrollView(activity).apply { isFillViewport = true; addView(content) })
        arrangeLayout()
        activity.setContentView(root)
    }

    fun arrangeLayout() {
        if (!::panels.isInitialized) return
        for (view in listOfNotNull(capturePanel, modes, notes)) {
            (view.parent as? ViewGroup)?.removeView(view)
        }
        panels.removeAllViews()
        panels.orientation = if (style.wideLayout) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        for (index in 0 until modes.childCount) {
            val button = modes.getChildAt(index)
            if (button is Button) button.layoutParams.height = style.dp(if (style.wideLayout) 68 else 80)
        }
        if (style.wideLayout) {
            val settings = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            capturePanel?.let { settings.addView(it); style.gap(settings, 20) }
            settings.addView(notes)
            panels.addView(settings, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
            panels.addView(modes, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f).apply { marginStart = style.dp(24) })
        } else {
            capturePanel?.let { panels.addView(it); style.gap(panels, 20) }
            panels.addView(modes)
            style.gap(panels, 22)
            panels.addView(notes)
        }
        panels.requestApplyInsets()
    }

    private fun openWorkload(store: BenchmarkConfigStore, config: BenchmarkConfig) {
        store.write(config)
        val manager = activity.getSystemService(ActivityManager::class.java)
        // The control Activity selects the mode before the next main process initializes the SDK.
        manager.runningAppProcesses?.filter { it.processName == activity.packageName }?.forEach { Process.killProcess(it.pid) }
        activity.startActivity(Intent(activity, BenchmarkActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        activity.finish()
    }

}
