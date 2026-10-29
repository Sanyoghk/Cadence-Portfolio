import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from audit_publication import inspect_text, private_filename, scan


class PrivacyRulesTest(unittest.TestCase):
    def test_redacts_email_and_home_path(self):
        text = "person" + "@gmail.com\nC:/Users/" + "private-person/project"
        result = inspect_text(text)
        self.assertEqual(result, [("personal-email", 1), ("personal-home-path", 2)])
        self.assertNotIn("private-person", json.dumps(result))

    def test_windows_json_and_wsl_paths(self):
        for prefix in ("C:\\\\Users\\\\", "/mnt/c/Users/", "/home/", "/Users/"):
            self.assertEqual(inspect_text(prefix + "private-person/project"), [("personal-home-path", 1)])

    def test_portable_examples_and_runner_paths(self):
        self.assertEqual(inspect_text("C:/Users/<user>/repo /home/runner/work user@example.com"), [])

    def test_chat_identifier(self):
        self.assertEqual(inspect_text("g-p-" + "a" * 24), [("private-chat-id", 1)])
        self.assertEqual(inspect_text("01a00000" + "-0000-7000-8000-000000000000"), [("private-chat-id", 1)])

    def test_regex_source_is_not_a_user_directory(self):
        self.assertEqual(inspect_text("/Users/" + "|/home/)"), [])

    def test_phone_candidate_needs_review_without_echoing_value(self):
        value = "+1" + "2025550123"
        self.assertEqual(inspect_text(value), [("international-phone-candidate", 1)])
        self.assertNotIn(value, json.dumps(inspect_text(value)))

    def test_random_database_uuid_is_not_a_chat(self):
        value = "01ad3a3e" + "-0004-4364-85a9-f4f590af5d7c"
        self.assertEqual(inspect_text("jdbc:h2:mem:" + value), [])

    def test_private_filenames_and_example(self):
        for name in (".env", "app/.env.local", "id_rsa", "data.p12", "backup.dump"):
            self.assertTrue(private_filename(name), name)
        for name in (".env.example", "app/.env.example", "README.md", "config.key.ts"):
            self.assertFalse(private_filename(name), name)


class HistoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.git("init", "--quiet")
        self.git("config", "user.name", "Privacy Test")
        self.git("config", "user.email", "test@example.com")

    def tearDown(self):
        self.temp.cleanup()

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.root), *args], stderr=subprocess.PIPE)

    def commit(self):
        self.git("add", ".")
        self.git("commit", "--quiet", "-m", "fixture")

    def test_deleted_secret_file_remains_in_history(self):
        path = self.root / ".env"
        path.write_text("PLACEHOLDER=true")
        self.commit()
        path.unlink()
        (self.root / "README.md").write_text("Example")
        self.commit()
        self.assertEqual(scan(self.root)["findings"], [])
        self.assertIn("private-file", [x["rule"] for x in scan(self.root, history=True)["findings"]])

    def test_commit_email_checked_even_when_files_clean(self):
        (self.root / "README.md").write_text("Example")
        self.git("config", "user.email", "private" + "@gmail.com")
        self.commit()
        findings = scan(self.root, history=True)["findings"]
        self.assertTrue(any(x["path"] == "[commit metadata]" for x in findings))
        self.assertNotIn("private@", json.dumps(findings))

    def test_renamed_blob_keeps_sensitive_path(self):
        (self.root / ".env").write_text("PLACEHOLDER=true")
        self.commit()
        self.git("mv", ".env", "sample.txt")
        self.commit()
        self.assertTrue(any(x["path"] == ".env" for x in scan(self.root, history=True)["findings"]))

    def test_worktree_ignores_untracked_private_files(self):
        (self.root / "README.md").write_text("Example")
        self.commit()
        (self.root / ".env").write_text("private" + "@gmail.com")
        self.assertEqual(scan(self.root, worktree=True)["findings"], [])

    def test_invalid_ref_fails(self):
        with self.assertRaises(subprocess.CalledProcessError):
            scan(self.root, ["missing-ref"])


if __name__ == "__main__":
    unittest.main()
