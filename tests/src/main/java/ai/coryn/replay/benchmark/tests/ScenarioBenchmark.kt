package ai.coryn.replay.benchmark.tests

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScenarioBenchmark {
    private val impl = ScenarioBenchmarkImpl()
    @get:Rule val benchmark = MacrobenchmarkRule()
    @Test fun staticScreen() = impl.run(benchmark, "static")
    @Test fun scrollingList() = impl.run(benchmark, "list")
    @Test fun scrollingImages() = impl.run(benchmark, "images")
    @Test fun animation() = impl.run(benchmark, "animation")
}

internal class ScenarioBenchmarkImpl {
    fun run(rule: MacrobenchmarkRule, scenario: String) {
        val arguments = InstrumentationRegistry.getArguments()
        val mode = arguments.getString("mode", "capture")
        require(mode in setOf("off", "recording", "capture"))
        val iterations = arguments.getString("iterations", "5").toInt()
        val warmupMs = arguments.getString("warmupMs", "20000").toLong()
        val duration = arguments.getString("measureMs", "30000").toLong()
        require(iterations in 1..100 && warmupMs in 0..600_000 && duration in 1_000..600_000)
        val packageName = "ai.coryn.replay.benchmark"
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            rule.measureRepeated(packageName, listOf(FrameTimingMetric()), CompilationMode.Full(), null, iterations, setupBlock = {
                device.setOrientationNatural()
                killProcess()
                val configured = device.executeShellCommand("am broadcast --include-stopped-packages --receiver-foreground -n $packageName/.ModeReceiver " +
                    "--es mode $mode --es networkMode mock")
                check(configured.contains("CONFIGURED:$mode")) { configured }
                killProcess()
                val intent = Intent().setComponent(ComponentName(packageName, "$packageName.BenchmarkActivity"))
                    .putExtra("scenario", scenario).putExtra("warmupMs", 0L).putExtra("measureMs", duration)
                startActivityAndWait(intent)
                check(device.wait(Until.hasObject(By.desc("sdk-ready")), 60_000)) { "Recording did not become ready" }
                Thread.sleep(warmupMs)
            }) {
                device.executeShellCommand("am start --activity-single-top -n $packageName/.BenchmarkActivity " +
                    "--es runId macro-${System.currentTimeMillis()} --el warmupMs 0 --el measureMs $duration")
                check(device.wait(Until.hasObject(By.descStartsWith("benchmark-complete:")), duration + 65_000)) { "Benchmark did not complete" }
            }
        } finally {
            device.unfreezeRotation()
        }
    }
}
