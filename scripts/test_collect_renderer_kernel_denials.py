import io
import json
import unittest

from collect_renderer_kernel_denials import collect, parse_line


class RendererKernelDiagnosticsTest(unittest.TestCase):
    def test_retains_exact_mount_category_and_fixed_paths(self):
        result = parse_line('apparmor="DENIED" operation="mount" '
                            'profile="cadence-evidence-renderer" info="failed flags match" '
                            'name="/proc/" srcname="proc" fstype="proc" '
                            'flags="rw, nosuid, nodev, noexec" error=-13')
        self.assertEqual(result["operation"], "mount")
        self.assertEqual(result["info"], "failed flags match")
        self.assertEqual(result["name"], "/proc/")
        self.assertEqual(result["error"], -13)

    def test_redacts_unreviewed_values_and_never_emits_credentials(self):
        result = parse_line('apparmor="DENIED" profile="cadence-evidence-renderer" '
                            'operation="mount" name="/private/secret-token" '
                            'srcname="secret-token" info="secret-token" '
                            'authorization="Bearer secret-token"')
        self.assertNotIn("secret-token", json.dumps(result))
        self.assertEqual(result["info"], "<redacted>")
        self.assertEqual(result["name"], "<redacted>")
        self.assertNotIn("authorization", result)

    def test_rejects_other_profiles_and_bounds_lines_and_records(self):
        self.assertIsNone(parse_line('apparmor="DENIED" profile="other-profile"'))
        line = b'apparmor="DENIED" profile="cadence-evidence-renderer" operation="mount"\n'
        result = collect(io.BytesIO(b"X" * 10000 + b"\n" + line * 40))
        self.assertEqual(len(result), 20)
        self.assertTrue(all(item["operation"] == "mount" for item in result))


if __name__ == "__main__":
    unittest.main()
