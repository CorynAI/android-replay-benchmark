package ai.coryn.replay.benchmark

import android.app.Application
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSink
import okio.Sink
import okio.blackholeSink
import okio.buffer

class BenchmarkTransport private constructor() {
    companion object {
        @JvmStatic
        fun configure(builder: OkHttpClient.Builder, application: Application): OkHttpClient.Builder =
            BenchmarkTransportImpl(builder, (application as BenchmarkApplication).benchmark).configure()
    }
}

internal class BenchmarkTransportImpl(private val builder: OkHttpClient.Builder, private val application: BenchmarkApplicationImpl) {
    fun configure(): OkHttpClient.Builder {
        if (application.config.networkMode == "mock") {
            application.transport.installed()
            android.util.Log.i("CorynBenchmark", "MOCK_TRANSPORT_INSTALLED")
            builder.addInterceptor(BenchmarkMockInterceptor(application.config.fps, application.transport))
        }
        return builder
    }
}

internal class BenchmarkMockInterceptor(private val fps: Double, private val recorder: BenchmarkTransportRecorder) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        check(request.url.host == "benchmark.invalid") { "Mock transport must never handle a hosted origin" }
        val sink = BenchmarkCountingSink(blackholeSink())
        sink.buffer().use { output -> request.body?.writeTo(output) }
        val body = request.body as? MultipartBody
        val images = body?.parts?.count { part ->
            val disposition = part.headers?.get("Content-Disposition") ?: ""
            Regex("filename=\"[^\"]+\\.(webp|jpg|jpeg|png)\"").containsMatchIn(disposition)
        } ?: 0
        val route = request.url.encodedPath.removePrefix("/api/")
        recorder.received(route, sink.bytes, images)
        val response = reply(route, request.method)
        if (response.first == 404) { android.util.Log.e("CorynBenchmark", "MOCK_UNHANDLED_GET:$route") }
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(response.first).message("Benchmark mock")
            .body(response.second.toResponseBody("application/json".toMediaType())).build()
    }

    private fun reply(route: String, method: String): Pair<Int, String> {
        if (method == "POST") return 200 to Json.encodeToString(emptyMap<String, String>())
        return when (route) {
            "admin-service/public/sdk/disabled" -> 200 to Json.encodeToString(BenchmarkDisabledVersions(emptyMap(), emptyList()))
            "user-management-service/project/me/disabled/version" -> 200 to Json.encodeToString(emptyList<String>())
            "user-management-service/project/me" -> 200 to Json.encodeToString(BenchmarkProjectPolicy(false, true, false, fps, true))
            else -> 404 to Json.encodeToString(emptyMap<String, String>())
        }
    }
}

internal class BenchmarkCountingSink(delegate: Sink) : ForwardingSink(delegate) {
    private var count = 0L
    val bytes: Long get() = count
    override fun write(source: Buffer, byteCount: Long) {
        count += byteCount
        super.write(source, byteCount)
    }
}

@Serializable
internal data class BenchmarkDisabledVersions(
    @SerialName("partial") val partial: Map<String, String>,
    @SerialName("full") val full: List<String>
)

@Serializable
internal data class BenchmarkProjectPolicy(
    @SerialName("defaultProjectConfig") val defaultProjectConfig: Boolean,
    @SerialName("versionEnabled") val versionEnabled: Boolean,
    @SerialName("permanentlyDisabled") val permanentlyDisabled: Boolean,
    @SerialName("capturingFps") val capturingFps: Double,
    @SerialName("capturingEnabled") val capturingEnabled: Boolean
)
