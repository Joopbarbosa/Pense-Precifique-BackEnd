import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name('check_pmd_baseline.py')
sys.path.insert(0, str(SCRIPT.parent))
from check_pmd_baseline import findings


def xml(violations):
    body = ''.join(f'<violation rule="{rule}" beginline="{line}">{message}</violation>'
                   for rule, line, message in violations)
    return f'<pmd xmlns="http://pmd.sourceforge.net/report/2.0.0"><file name="/repo/src/main/java/A.java">{body}</file></pmd>'


class PmdBaselineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.report = self.root / 'pmd.xml'
        self.baseline = self.root / 'baseline.json'

    def run_script(self, *extra):
        return subprocess.run([sys.executable, str(SCRIPT), '--report', str(self.report),
                               '--baseline', str(self.baseline), *extra], text=True,
                              capture_output=True, check=False)

    def test_new_identical_violation_in_same_class_fails(self):
        self.report.write_text(xml([('UnusedLocalVariable', 10, 'unused x')]))
        self.assertEqual(self.run_script('--write-baseline').returncode, 0)
        self.report.write_text(xml([('UnusedLocalVariable', 10, 'unused x'),
                                    ('UnusedLocalVariable', 20, 'unused x')]))
        result = self.run_script()
        self.assertEqual(result.returncode, 1)
        self.assertEqual(json.loads(result.stdout)['new'][0]['count'], 1)

    def test_removed_or_moved_violation_passes(self):
        self.report.write_text(xml([('EmptyCatchBlock', 10, 'empty catch')]))
        self.assertEqual(self.run_script('--write-baseline').returncode, 0)
        self.report.write_text(xml([('EmptyCatchBlock', 50, 'empty catch')]))
        self.assertEqual(self.run_script().returncode, 0)

    def test_missing_report_or_baseline_is_environment_error(self):
        self.assertEqual(self.run_script().returncode, 2)
        self.report.write_text(xml([]))
        self.assertEqual(self.run_script().returncode, 2)

    def test_checkout_path_containing_src_uses_source_relative_path(self):
        self.report.write_text('<pmd><file name="/home/u/src/repo/src/main/java/A.java">'
                               '<violation rule="X">message</violation></file></pmd>')
        self.assertEqual(next(iter(findings(self.report)))[0], 'src/main/java/A.java')

    def test_removed_violation_requires_baseline_reduction(self):
        self.report.write_text(xml([('EmptyCatchBlock', 10, 'empty catch')]))
        self.assertEqual(self.run_script('--write-baseline').returncode, 0)
        self.report.write_text(xml([]))
        result = self.run_script()
        self.assertEqual(result.returncode, 1)
        self.assertEqual(json.loads(result.stdout)['stale'][0]['count'], 1)

    def test_baseline_cannot_be_overwritten_silently(self):
        self.report.write_text(xml([]))
        self.assertEqual(self.run_script('--write-baseline').returncode, 0)
        self.assertEqual(self.run_script('--write-baseline').returncode, 2)


if __name__ == '__main__':
    unittest.main()
