package ai.coryn.replay.benchmark

import android.app.Application
import android.view.View

@Suppress("UNUSED_PARAMETER")
internal class SdkBridge(application: Application, config: BenchmarkConfig, transport: BenchmarkTransportRecorder) {
    val sessionId: String? = null
    val error: String? = null
    val ready: Boolean = true
    fun initialize() = Unit
    fun start(metrics: View) = Unit
    fun stop() = Unit
}
