package ai.coryn.replay.benchmark.tests

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {
    @Test fun rotationPreservesRecordingAndWorkload() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val packageName = "ai.coryn.replay.benchmark"
        val runId = "rotation-${System.currentTimeMillis()}"
        val previousIdleTimeout = Configurator.getInstance().waitForIdleTimeout
        Configurator.getInstance().waitForIdleTimeout = 0
        try {
            device.setOrientationNatural()
            device.executeShellCommand("am force-stop $packageName")
            val configured = device.executeShellCommand("am broadcast --include-stopped-packages --receiver-foreground " +
                "-n $packageName/.ModeReceiver --es mode capture --es networkMode mock")
            assertTrue(configured, configured.contains("CONFIGURED:capture"))
            device.executeShellCommand("am force-stop $packageName")
            device.executeShellCommand("am start -W -n $packageName/.BenchmarkActivity --es scenario images " +
                "--es runId $runId --el warmupMs 0 --el measureMs 20000")
            assertTrue("Recording did not start", device.wait(Until.hasObject(By.text("Measuring for 20s…")), 60_000))
            val pid = device.executeShellCommand("pidof $packageName").trim()
            val naturalWide = device.displayWidth / instrumentation.context.resources.displayMetrics.density >= 600
            assertGeometry(device, packageName, naturalWide)
            device.setOrientationLeft()
            assertGeometry(device, packageName, true)
            assertTrue(device.findObject(By.res(packageName, "scenario_images")).isSelected)
            device.setOrientationNatural()
            assertGeometry(device, packageName, naturalWide)
            assertEquals(pid, device.executeShellCommand("pidof $packageName").trim())
            assertTrue(device.findObject(By.res(packageName, "scenario_images")).isSelected)
            assertTrue("Timed run did not survive rotation", device.wait(Until.hasObject(By.desc("benchmark-complete:$runId")), 65_000))
            val result = JSONObject(device.executeShellCommand("cat /sdcard/Android/data/$packageName/files/results/$runId.json"))
            assertEquals("images", result.getString("scenario"))
            assertTrue(!result.isNull("sessionId"))
            assertEquals(1, result.getJSONObject("transport").getInt("sessions"))
            assertTrue(result.getInt("workloadFrames") > 0)
        } finally {
            Configurator.getInstance().waitForIdleTimeout = previousIdleTimeout
            device.unfreezeRotation()
        }
    }

    private fun assertGeometry(device: UiDevice, packageName: String, wide: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        do {
            val metrics = device.findObject(By.res(packageName, "metrics"))?.visibleBounds
            val workload = device.findObject(By.res(packageName, "scenario_content"))?.visibleBounds
            if (metrics != null && workload != null && workload.width() > 0 && workload.height() > 0) {
                if (wide && workload.left > metrics.right) return
                if (!wide && workload.top > metrics.bottom) return
            }
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Expected ${if (wide) "two-column" else "stacked"} layout")
    }
}
