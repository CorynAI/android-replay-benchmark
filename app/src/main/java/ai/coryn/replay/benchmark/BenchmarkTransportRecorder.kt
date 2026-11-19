package ai.coryn.replay.benchmark

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class BenchmarkTransportSnapshot(
    @SerialName("installed") val installed: Boolean,
    @SerialName("requests") val requests: Int,
    @SerialName("sessions") val sessions: Int,
    @SerialName("recordings") val recordings: Int,
    @SerialName("captures") val captures: Int,
    @SerialName("imageFiles") val imageFiles: Int,
    @SerialName("bytes") val bytes: Long
)

internal class BenchmarkTransportRecorder {
    private val lock = Any()
    private var state = BenchmarkTransportSnapshot(false, 0, 0, 0, 0, 0, 0)

    fun installed() {
        synchronized(lock) { state = state.copy(installed = true) }
    }

    fun received(route: String, bytes: Long, images: Int) {
        synchronized(lock) {
            val sessions = if (route == "replay-service/sessions") 1 else 0
            val recordings = if (route == "replay-service/sessions/chunks") 1 else 0
            val captures = if (route.startsWith("capture-service/captures/chunks/") && route.endsWith("/upload")) 1 else 0
            state = state.copy(requests = state.requests + 1, sessions = state.sessions + sessions, recordings = state.recordings + recordings,
                captures = state.captures + captures, imageFiles = state.imageFiles + images, bytes = state.bytes + bytes)
        }
    }

    fun snapshot(): BenchmarkTransportSnapshot = synchronized(lock) { state }
}
