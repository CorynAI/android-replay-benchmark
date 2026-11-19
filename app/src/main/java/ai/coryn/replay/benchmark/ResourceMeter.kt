package ai.coryn.replay.benchmark

import android.os.Debug
import android.os.Process
import android.os.SystemClock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ResourceSample(
    @SerialName("elapsedMs") val elapsedMs: Long,
    @SerialName("cpuMs") val cpuMs: Long,
    @SerialName("pssKiB") val pssKiB: Int,
    @SerialName("javaHeapBytes") val javaHeapBytes: Long,
    @SerialName("nativeHeapBytes") val nativeHeapBytes: Long
)

internal class ResourceMeter {
    private var pssKiB = 0
    private var lastPssAt = 0L

    fun sample(forcePss: Boolean): ResourceSample {
        val elapsed = SystemClock.elapsedRealtime()
        val cpu = Process.getElapsedCpuTime()
        if (forcePss || elapsed - lastPssAt >= 5_000) {
            val memory = Debug.MemoryInfo()
            Debug.getMemoryInfo(memory)
            pssKiB = memory.totalPss
            lastPssAt = elapsed
        }
        val runtime = Runtime.getRuntime()
        return ResourceSample(elapsed, cpu, pssKiB, runtime.totalMemory() - runtime.freeMemory(), Debug.getNativeHeapAllocatedSize())
    }
}

@Serializable
internal data class BenchmarkResult(
    @SerialName("schemaVersion") val schemaVersion: Int,
    @SerialName("runId") val runId: String,
    @SerialName("mode") val mode: String,
    @SerialName("scenario") val scenario: String,
    @SerialName("sdkIncluded") val sdkIncluded: Boolean,
    @SerialName("sdkVersion") val sdkVersion: String,
    @SerialName("releaseBuild") val releaseBuild: Boolean,
    @SerialName("device") val device: String,
    @SerialName("androidApi") val androidApi: Int,
    @SerialName("pid") val pid: Int,
    @SerialName("availableProcessors") val availableProcessors: Int,
    @SerialName("sessionId") val sessionId: String?,
    @SerialName("warmupMs") val warmupMs: Long,
    @SerialName("wallMs") val wallMs: Long,
    @SerialName("cpuMs") val cpuMs: Long,
    @SerialName("cpuPercentOfOneCore") val cpuPercentOfOneCore: Double,
    @SerialName("draws") val draws: Int,
    @SerialName("workloadFrames") val workloadFrames: Int,
    @SerialName("start") val start: ResourceSample,
    @SerialName("end") val end: ResourceSample,
    @SerialName("samples") val samples: List<ResourceSample>,
    @SerialName("networkMode") val networkMode: String,
    @SerialName("captureFpsTarget") val captureFpsTarget: Double,
    @SerialName("transportAtMeasurementStart") val transportAtMeasurementStart: BenchmarkTransportSnapshot,
    @SerialName("transport") val transport: BenchmarkTransportSnapshot
)
