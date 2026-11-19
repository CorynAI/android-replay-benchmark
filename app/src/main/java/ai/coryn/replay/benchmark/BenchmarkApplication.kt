package ai.coryn.replay.benchmark

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Process

class BenchmarkApplication : Application() {
    internal val benchmark = BenchmarkApplicationImpl(this)
    override fun onCreate() {
        super.onCreate()
        benchmark.onCreate()
    }
}

internal class BenchmarkApplicationImpl(private val application: Application) {
    val config by lazy { BenchmarkConfigStore(application).read() }
    val transport = BenchmarkTransportRecorder()
    val sdk by lazy { SdkBridge(application, config, transport) }

    fun onCreate() {
        val manager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val process = manager.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName
        if (process == application.packageName) sdk.initialize()
    }
}

internal data class BenchmarkConfig(val mode: String, val apiKey: String, val endpoint: String, val networkMode: String, val fps: Double)

internal class BenchmarkConfigStore(private val context: Context) {
    fun read(): BenchmarkConfig {
        val preferences = context.getSharedPreferences("benchmark", Context.MODE_PRIVATE)
        val defaultMode = if (BuildConfig.HAS_CORYN) "capture" else "off"
        return BenchmarkConfig(
            preferences.getString("mode", defaultMode)!!,
            preferences.getString("apiKey", "")!!,
            preferences.getString("endpoint", "")!!,
            preferences.getString("networkMode", "mock")!!,
            preferences.getFloat("fps", 5f).toDouble()
        )
    }

    fun write(config: BenchmarkConfig) {
        require(config.mode in setOf("off", "recording", "capture"))
        require(BuildConfig.HAS_CORYN || config.mode == "off")
        require(config.networkMode in setOf("mock", "hosted"))
        require(config.fps in 0.5..10.0)
        require(config.endpoint.isEmpty() || config.endpoint.startsWith("https://") || config.endpoint.startsWith("http://127.0.0.1:"))
        require(config.endpoint.isEmpty() || config.endpoint.endsWith('/'))
        check(context.getSharedPreferences("benchmark", Context.MODE_PRIVATE).edit()
            .putString("mode", config.mode).putString("apiKey", config.apiKey).putString("endpoint", config.endpoint)
            .putString("networkMode", config.networkMode).putFloat("fps", config.fps.toFloat()).commit())
    }
}
