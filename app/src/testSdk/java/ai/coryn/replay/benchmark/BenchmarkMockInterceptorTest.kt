package ai.coryn.replay.benchmark

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BenchmarkMockInterceptorTest {
    @Test
    fun configurationIsServedWithoutAResolvableHostOrServer() {
        val recorder = BenchmarkTransportRecorder()
        val client = OkHttpClient.Builder().addInterceptor(BenchmarkMockInterceptor(2.5, recorder)).build()
        val request = Request.Builder().url("https://benchmark.invalid/api/user-management-service/project/me").build()
        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            val policy = Json.decodeFromString<BenchmarkProjectPolicy>(response.body!!.string())
            assertEquals(2.5, policy.capturingFps, 0.0)
            assertTrue(policy.capturingEnabled)
        }
        assertEquals(1, recorder.snapshot().requests)
    }

    @Test
    fun uploadBodiesAreWrittenAndConsumedBeforeAcknowledgement() {
        val recorder = BenchmarkTransportRecorder()
        val client = OkHttpClient.Builder().addInterceptor(BenchmarkMockInterceptor(5.0, recorder)).build()
        var written = false
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun writeTo(sink: BufferedSink) {
                written = true
                sink.writeUtf8("serialized-recording")
            }
        }
        val request = Request.Builder().url("https://benchmark.invalid/api/replay-service/sessions/chunks").post(body).build()
        client.newCall(request).execute().use { assertEquals(200, it.code) }
        assertTrue(written)
        assertEquals(20, recorder.snapshot().bytes)
        assertEquals(1, recorder.snapshot().recordings)
    }

    @Test
    fun multipartCapturePartsAreConsumedAndCounted() {
        val recorder = BenchmarkTransportRecorder()
        val client = OkHttpClient.Builder().addInterceptor(BenchmarkMockInterceptor(5.0, recorder)).build()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("capture", "frame.webp", "bitmap-bytes".toRequestBody("application/octet-stream".toMediaType())).build()
        val request = Request.Builder().url("https://benchmark.invalid/api/capture-service/captures/chunks/one/upload").post(body).build()
        client.newCall(request).execute().use { assertEquals(200, it.code) }
        assertEquals(body.contentLength(), recorder.snapshot().bytes)
        assertEquals(1, recorder.snapshot().captures)
        assertEquals(1, recorder.snapshot().imageFiles)
    }
}
