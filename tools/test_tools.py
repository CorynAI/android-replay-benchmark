import json
import tempfile
import unittest
from pathlib import Path

from report import MODES, ORDERS, summarize, write_reports
from run import PACKAGE, is_benchmark_foreground, thermal_status, validate_transport


class BenchmarkToolsTest(unittest.TestCase):
    def test_thermal_status_is_not_invented_on_older_devices(self):
        self.assertEqual(thermal_status("Thermal Status: 0\n"), 0)
        self.assertEqual(thermal_status("  Thermal Status: 3\n"), 3)
        self.assertIsNone(thermal_status("Can't find service: thermalservice"))

    def test_foreground_guard_rejects_other_variant_and_launcher(self):
        self.assertTrue(is_benchmark_foreground("topResumedActivity=ActivityRecord{ " + PACKAGE + "/.BenchmarkActivity }", PACKAGE))
        self.assertFalse(is_benchmark_foreground("topResumedActivity=ActivityRecord{ " + PACKAGE + "/.LauncherActivity }", PACKAGE))
        plain = PACKAGE + ".plain"
        state = "topResumedActivity=ActivityRecord{ " + plain + "/" + PACKAGE + ".BenchmarkActivity }"
        self.assertFalse(is_benchmark_foreground(state, PACKAGE))
        self.assertTrue(is_benchmark_foreground(state, plain))

    def test_orders_cover_every_permutation_and_position(self):
        self.assertEqual(len(set(ORDERS)), 6)
        for mode in MODES:
            for position in range(3):
                self.assertEqual(sum(order[position] == mode for order in ORDERS), 2)

    def test_only_matched_repetitions_are_compared(self):
        def row(mode, repeat, cpu):
            return dict(scenario="list", mode=mode, repeat=repeat, cpuPercentOfOneCore=cpu,
                        end=dict(pssKiB=1024, javaHeapBytes=1048576, nativeHeapBytes=2097152))
        result = summarize([row("off", 1, 10), row("recording", 1, 12), row("capture", 1, 30), row("capture", 2, 900)])
        self.assertEqual(result["list"]["pairedRepetitions"], 1)
        self.assertEqual(result["list"]["pairedDeltasVsOff"]["capture"]["cpuPercentOfOneCore"]["median"], 20)
        self.assertEqual(result["list"]["modes"]["capture"]["nativeHeapMiB"], 2)

    def test_mock_transport_validation_rejects_silent_or_incomplete_recording(self):
        empty = dict(installed=False, requests=0, sessions=0, recordings=0, captures=0, imageFiles=0, bytes=0)
        validate_transport(dict(transport=empty), "off")
        with self.assertRaises(RuntimeError):
            validate_transport(dict(transport=empty), "recording")
        recording = dict(empty, installed=True, requests=5, sessions=1, recordings=1, bytes=100)
        validate_transport(dict(transport=recording), "recording")
        with self.assertRaises(RuntimeError):
            validate_transport(dict(transport=recording), "capture")
        capture = dict(recording, captures=1, imageFiles=2)
        validate_transport(dict(transport=capture), "capture")
        for mode in ["off", "recording"]:
            with self.assertRaises(RuntimeError):
                validate_transport(dict(transport=capture), mode)

    def test_report_escapes_external_labels_and_keeps_build_fingerprint(self):
        rows = [dict(runId=mode, scenario="<script>bad()</script>", mode=mode, repeat=1, wallMs=20000, cpuMs=100,
                     cpuPercentOfOneCore=0.5, end=dict(pssKiB=1024, javaHeapBytes=2048, nativeHeapBytes=4096)) for mode in MODES]
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "metadata.json").write_text(json.dumps(dict(apkSha256="frozen-apk", warmupMs=15000, measureMs=20000, fps=5)))
            write_reports(directory, rows)
            report = (directory / "report.html").read_text()
            self.assertNotIn("<script>", report)
            self.assertIn("&lt;script&gt;", report)
            self.assertIn("frozen-apk", report)

    def test_plain_app_has_absolute_results_without_invented_comparisons(self):
        row = dict(scenario="static", mode="off", repeat=1, sdkIncluded=False, cpuPercentOfOneCore=1.0,
                   end=dict(pssKiB=1024, javaHeapBytes=2048, nativeHeapBytes=4096))
        result = summarize([row])["static"]
        self.assertEqual(result["modes"]["off"]["pssMiB"], 1)
        self.assertEqual(result["pairedDeltasVsOff"], {})
        row.update(runId="plain-test", wallMs=20000, cpuMs=200)
        with tempfile.TemporaryDirectory() as temporary:
            write_reports(temporary, [row])
            report = (Path(temporary) / "report.html").read_text()
            self.assertIn("No Coryn dependencies", report)
            self.assertIn("Baseline only", report)
            self.assertNotIn("Paired differences", report)


if __name__ == "__main__":
    unittest.main()
