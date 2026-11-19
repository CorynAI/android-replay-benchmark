package ai.coryn.replay.benchmark

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import android.text.SpannableString
import android.text.Spanned
import android.text.InputType
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch

class LauncherActivity : Activity() {
    private val impl = LauncherActivityImpl(this)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        impl.onCreate()
    }
}

internal class LauncherActivityImpl(private val activity: Activity) {
    fun onCreate() {
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val style = BenchmarkStyle(activity)
        val store = BenchmarkConfigStore(activity)
        val config = store.read()
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(style.dp(20), style.dp(24), style.dp(20), style.dp(24)) }
        content.addView(style.text("CORYN SDK  /  DEVICE LAB", 11f, style.purple, true).apply { letterSpacing = 0.1f })
        style.gap(content, 12)
        content.addView(style.text("Retrec benchmark", 32f, style.ink, true))
        style.gap(content, 8)
        content.addView(style.text("See the cost of recording on your phone.", 16f, style.muted, false))
        style.gap(content, 16)
        val info = if (BuildConfig.HAS_CORYN) "Coryn ${BuildConfig.CORYN_VERSION}" else "No Coryn dependencies"
        content.addView(style.text("$info   ·   4 fixed workloads", 12f, style.purple, true).apply {
            background = style.rounded(style.lavender, 8, Color.TRANSPARENT)
            setPadding(style.dp(12), style.dp(10), style.dp(12), style.dp(10))
        })
        style.gap(content, 20)
        val apiKey = EditText(activity).apply {
            hint = "Test project API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(config.apiKey)
        }
        val endpoint = EditText(activity).apply {
            hint = "Coryn hosted (default)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(config.endpoint)
        }
        val hosted = Switch(activity).apply {
            text = "Use hosted test project"
            textSize = 14f
            setTextColor(style.ink)
            isChecked = config.networkMode == "hosted"
            thumbTintList = android.content.res.ColorStateList.valueOf(style.navy)
            trackTintList = android.content.res.ColorStateList.valueOf(Color.argb(75, 28, 36, 64))
        }
        if (BuildConfig.HAS_CORYN) {
            val connection = style.card()
            connection.addView(style.text("Network", 16f, style.ink, true))
            style.gap(connection, 10)
            connection.addView(hosted)
            style.gap(connection, 8)
            val description = style.text("", 12f, style.muted, false)
            connection.addView(description)
            val fields = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            for ((label, field) in listOf("Project API key" to apiKey, "Endpoint override · optional" to endpoint)) {
                style.gap(fields, 12)
                fields.addView(style.text(label, 12f, style.muted, true))
                style.gap(fields, 6)
                field.apply {
                    textSize = 14f
                    typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                    setTextColor(style.ink)
                    setHintTextColor(style.muted)
                    setSingleLine()
                    background = style.rounded(Color.rgb(249, 249, 252), 10, style.border)
                    setPadding(style.dp(12), style.dp(10), style.dp(12), style.dp(10))
                }
                fields.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(46)))
            }
            connection.addView(fields)
            fun showConnection(useHosted: Boolean) {
                fields.visibility = if (useHosted) View.VISIBLE else View.GONE
                description.text = if (useHosted) "Recordings upload to your test project." else "Local simulation · no account, server or uploads."
            }
            hosted.setOnCheckedChangeListener { _, checked -> showConnection(checked) }
            showConnection(hosted.isChecked)
            content.addView(connection)
            style.gap(content, 20)
        }
        content.addView(style.text("Choose a recording mode", 20f, style.ink, true))
        style.gap(content, 6)
        content.addView(style.text("Each mode opens a fresh workload session.", 13f, style.muted, false))
        style.gap(content, 12)
        val status = style.text("", 13f, Color.rgb(160, 48, 70), false)
        for ((mode, label, identifier) in listOf(
            Triple("off", "SDK off\nBaseline · recorder uninitialized", R.id.mode_off),
            Triple("recording", "Recording · UI capture off\nSession context without visual replay", R.id.mode_recording),
            Triple("capture", "Full recording\nSession context and visual replay", R.id.mode_capture)
        )) {
            val titleEnd = label.indexOf('\n')
            val title = SpannableString(label).apply {
                setSpan(AbsoluteSizeSpan(12, true), titleEnd + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(Color.rgb(207, 211, 226)), titleEnd + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            content.addView(style.button(label, true).apply {
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
                        require(mode == "off" || !hosted.isChecked || apiKey.text.isNotBlank()) { "Enter a test project API key for hosted recording." }
                        val networkMode = if (hosted.isChecked) "hosted" else "mock"
                        store.write(BenchmarkConfig(mode, apiKey.text.toString().trim(), endpoint.text.toString().trim(), networkMode, config.fps))
                        val manager = activity.getSystemService(ActivityManager::class.java)
                        // Mode selection runs separately so the next main process initializes with the selected mode.
                        manager.runningAppProcesses?.filter { it.processName == activity.packageName }?.forEach { Process.killProcess(it.pid) }
                        activity.startActivity(Intent(activity, BenchmarkActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        activity.finish()
                    } catch (error: IllegalArgumentException) {
                        status.text = error.message
                    }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(80)))
            style.gap(content, 10)
        }
        content.addView(status)
        style.gap(content, 12)
        content.addView(style.text("Live CPU, RAM and heap readings include the whole app. Compare matched runs to measure SDK overhead.",
            12f, style.muted, false))
        style.gap(content, 10)
        content.addView(style.text("Local simulation works on this phone. For automated comparisons, follow the source README.", 12f, style.muted, false))
        val root = style.page()
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        root.addView(ScrollView(activity).apply { isFillViewport = true; addView(content) })
        activity.setContentView(root)
    }
}
