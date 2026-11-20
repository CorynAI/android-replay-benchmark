package ai.coryn.replay.benchmark

import ai.coryn.replay.api.core.main.Coryn
import ai.coryn.replay.api.core.model.configuration.CorynConfiguration
import ai.coryn.replay.api.core.model.configuration.CorynRecordingConfiguration
import ai.coryn.replay.shared.listener.CorynRecordingListener
import ai.coryn.replay.shared.logviewer.CorynLogViewerIndicatorVisibility
import ai.coryn.replay.shared.uicapture.CorynUiCapturing
import android.app.Application
import android.view.View

internal class SdkBridge(private val application: Application, private val config: BenchmarkConfig, private val transport: BenchmarkTransportRecorder) {
    @Volatile private var _sessionId: String? = null
    private var _error: String? = null
    private var started = false
    val sessionId: String? get() = _sessionId
    val error: String? get() = _error
    val ready: Boolean get() = config.mode == "off" || (_sessionId != null && (config.networkMode != "mock" || transport.snapshot().installed))

    fun initialize() {
        if (config.mode == "off") return
        if (config.networkMode == "hosted" && config.apiKey.isBlank()) {
            _error = "Enter a test project API key for hosted recording."
            return
        }
        val logViewer = if (BuildConfig.DEBUG) CorynLogViewerIndicatorVisibility.VISIBLE_ALWAYS else CorynLogViewerIndicatorVisibility.GONE
        val builder = CorynConfiguration.Builder().setPreErase(true, null)
            .setUploadOnMeteredNetworkAllowed(true).setLogViewerIndicatorVisibility(logViewer)
        if (config.networkMode == "mock") {
            builder.setNetworkBaseUrl("https://benchmark.invalid/api/")
        } else if (config.endpoint.isNotEmpty()) {
            builder.setNetworkBaseUrl(config.endpoint)
        }
        val apiKey = if (config.networkMode == "mock") "benchmark-mock-${java.util.UUID.randomUUID()}" else config.apiKey
        Coryn.init(application, apiKey, builder.build())
    }

    fun start(metrics: View) {
        if (config.mode == "off" || started || _error != null) { return }
        started = true
        Coryn.hideFromUiCapture(metrics, true, null)
        val capturing = if (config.mode == "capture") { CorynUiCapturing.TEXT_AND_VISUALS } else { CorynUiCapturing.DISABLED }
        val configuration = CorynRecordingConfiguration.Builder().setUiCapturing(capturing)
            .setRecordingListener(CorynRecordingListener {
                _sessionId = it
                android.util.Log.i("CorynBenchmark", "SDK_RECORDING_READY")
            }).build()
        Coryn.startRecording(configuration)
    }

    fun stop() {
        if (config.mode != "off" && started) {
            Coryn.stopRecording("Benchmark completed")
            _sessionId = null
            _error = "Recording finished. Change mode to start a fresh process."
        }
    }
}
