package ai.coryn.replay.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ModeReceiver : BroadcastReceiver() {
    private val impl = ModeReceiverImpl()
    override fun onReceive(context: Context, intent: Intent) {
        resultData = impl.configure(context, intent)
    }
}

internal class ModeReceiverImpl {
    fun configure(context: Context, intent: Intent): String {
        return try {
            val mode = requireNotNull(intent.getStringExtra("mode"))
            val apiKey = intent.getStringExtra("apiKey") ?: ""
            val endpoint = intent.getStringExtra("endpoint") ?: ""
            val networkMode = intent.getStringExtra("networkMode") ?: "mock"
            val fps = intent.getDoubleExtra("fps", 5.0)
            BenchmarkConfigStore(context).write(BenchmarkConfig(mode, apiKey, endpoint, networkMode, fps))
            "CONFIGURED:$mode"
        } catch (_: IllegalArgumentException) {
            "INVALID_BENCHMARK_CONFIGURATION"
        }
    }
}
