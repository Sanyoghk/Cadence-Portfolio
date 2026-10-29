import importlib.util
import os
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("renderer_ephemeral", Path(__file__).with_name("ephemeral.py"))
ephemeral = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ephemeral)


class EphemeralCleanupTest(unittest.TestCase):
    def test_previous_job_payload_and_ipc_files_are_removed_before_next_job(self):
        with tempfile.TemporaryDirectory() as fixture:
            roots = [Path(fixture) / "tmp", Path(fixture) / "shm"]
            for root in roots:
                root.mkdir()
                (root / "payload.py").write_bytes(b"previous-tenant-payload")
                nested = root / "browser-profile"
                nested.mkdir()
                (nested / "state").write_bytes(b"previous-tenant-evidence")
            with patch.object(ephemeral, "_ROOTS", tuple(roots)):
                ephemeral.purge_ephemeral_storage()
                for root in roots:
                    self.assertEqual(list(root.iterdir()), [])
                    (root / "next-job").write_bytes(b"next-tenant")
                ephemeral.purge_ephemeral_storage()
                ephemeral.purge_ephemeral_storage()
            self.assertTrue(all(root.is_dir() and not list(root.iterdir()) for root in roots))

    def test_symlink_targets_and_original_hardlink_are_preserved(self):
        with tempfile.TemporaryDirectory() as fixture:
            base = Path(fixture)
            root = base / "root"
            root.mkdir()
            outside = base / "outside"
            outside.mkdir()
            outside_mode = stat.S_IMODE(outside.stat().st_mode)
            original = outside / "original"
            original.write_bytes(b"must-remain-unchanged")
            (root / "directory-link").symlink_to(outside, target_is_directory=True)
            (root / "file-link").symlink_to(original)
            os.link(original, root / "hardlink")
            ephemeral._purge_roots((root,))
            self.assertEqual(list(root.iterdir()), [])
            self.assertEqual(original.read_bytes(), b"must-remain-unchanged")
            self.assertEqual(original.stat().st_nlink, 1)
            self.assertEqual(stat.S_IMODE(outside.stat().st_mode), outside_mode)

    def test_mode_zero_directories_and_fifo_cannot_block_cleanup(self):
        with tempfile.TemporaryDirectory() as fixture:
            root = Path(fixture) / "root"
            nested = root / "nested" / "deep"
            nested.mkdir(parents=True)
            (nested / "payload").write_bytes(b"hostile-payload")
            os.mkfifo(nested / "fifo")
            nested.chmod(0)
            nested.parent.chmod(0)
            root.chmod(0)
            ephemeral._purge_roots((root,))
            self.assertEqual(list(root.iterdir()), [])

    def test_depth_limit_rejects_incomplete_cleanup_and_accepts_exact_boundary(self):
        with tempfile.TemporaryDirectory() as fixture:
            root = Path(fixture) / "root"
            root.mkdir()
            deepest = root
            for _ in range(ephemeral._MAX_DEPTH):
                deepest = deepest / "d"
                deepest.mkdir()
            (deepest / "payload").write_bytes(b"bounded")
            ephemeral._purge_roots((root,))
            self.assertEqual(list(root.iterdir()), [])
            deepest = root
            for _ in range(ephemeral._MAX_DEPTH + 1):
                deepest = deepest / "d"
                deepest.mkdir()
            payload = deepest / "payload"
            payload.write_bytes(b"must-fail-closed")
            with self.assertRaises(ephemeral.EphemeralCleanupFailure) as error:
                ephemeral._purge_roots((root,))
            self.assertEqual(error.exception.reason, "depth")
            self.assertTrue(payload.exists())

    def test_deadline_is_shared_across_roots_and_failure_is_sanitized(self):
        with tempfile.TemporaryDirectory() as fixture:
            first, second = Path(fixture) / "first", Path(fixture) / "second"
            first.mkdir()
            second.mkdir()
            (first / "payload").write_bytes(b"old")
            remaining = second / "private-name-must-not-be-disclosed"
            remaining.write_bytes(b"old")
            now = [0]
            original_unlink = os.unlink
            original_open_directory = ephemeral._open_directory

            def advance_after_unlink(*args, **kwargs):
                result = original_unlink(*args, **kwargs)
                now[0] = 1.5
                return result

            def expire_before_second_root(name, *args, **kwargs):
                if name == second:
                    now[0] = 2.5
                return original_open_directory(name, *args, **kwargs)

            with patch.object(ephemeral.os, "unlink", side_effect=advance_after_unlink), \
                    patch.object(ephemeral, "_open_directory", side_effect=expire_before_second_root):
                with self.assertRaises(ephemeral.EphemeralCleanupFailure) as error:
                    ephemeral._purge_roots((first, second), clock=lambda: now[0])
            self.assertEqual(error.exception.reason, "deadline")
            self.assertEqual(str(error.exception), "Ephemeral cleanup failed")
            self.assertEqual(list(first.iterdir()), [])
            self.assertTrue(remaining.exists())

    def test_foreign_owned_entry_is_preserved_and_blocks_cleanup(self):
        with tempfile.TemporaryDirectory() as fixture:
            root = Path(fixture)
            payload = root / "foreign-owned"
            payload.write_bytes(b"preserve")
            original_stat = os.stat

            def foreign_metadata(path, *args, **kwargs):
                result = original_stat(path, *args, **kwargs)
                if path == "foreign-owned":
                    values = list(result)
                    values[4] = os.geteuid() + 1
                    return os.stat_result(values)
                return result

            with patch.object(ephemeral.os, "stat", side_effect=foreign_metadata):
                with self.assertRaises(ephemeral.EphemeralCleanupFailure) as error:
                    ephemeral._purge_roots((root,))
            self.assertEqual(error.exception.reason, "foreign-owner")
            self.assertEqual(payload.read_bytes(), b"preserve")

    def test_root_symlink_is_rejected_without_touching_target(self):
        with tempfile.TemporaryDirectory() as fixture:
            outside = Path(fixture) / "outside"
            outside.mkdir()
            payload = outside / "payload"
            payload.write_bytes(b"preserve")
            link = Path(fixture) / "root-link"
            link.symlink_to(outside, target_is_directory=True)
            with self.assertRaises(ephemeral.EphemeralCleanupFailure):
                ephemeral._purge_roots((link,))
            self.assertEqual(payload.read_bytes(), b"preserve")

    def test_filesystem_boundary_is_rejected_without_unlinking_entry(self):
        with tempfile.TemporaryDirectory() as fixture:
            root = Path(fixture)
            payload = root / "other-filesystem"
            payload.write_bytes(b"preserve")
            original_stat = os.stat

            def different_device(path, *args, **kwargs):
                result = original_stat(path, *args, **kwargs)
                if path == "other-filesystem":
                    values = list(result)
                    values[2] = result.st_dev + 1
                    return os.stat_result(values)
                return result

            with patch.object(ephemeral.os, "stat", side_effect=different_device):
                with self.assertRaises(ephemeral.EphemeralCleanupFailure) as error:
                    ephemeral._purge_roots((root,))
            self.assertEqual(error.exception.reason, "filesystem-boundary")
            self.assertEqual(payload.read_bytes(), b"preserve")

    def test_entry_budget_and_native_failure_reject_incomplete_cleanup(self):
        with tempfile.TemporaryDirectory() as fixture:
            root = Path(fixture)
            (root / "one").write_bytes(b"payload")
            (root / "two").write_bytes(b"payload")
            with self.assertRaises(ephemeral.EphemeralCleanupFailure) as error:
                ephemeral._purge_roots((root,), max_entries=1)
            self.assertEqual(error.exception.reason, "entries")
            self.assertEqual(len(list(root.iterdir())), 1)
            with patch.object(ephemeral.os, "unlink", side_effect=PermissionError("private-path")):
                with self.assertRaises(ephemeral.EphemeralCleanupFailure) as error:
                    ephemeral._purge_roots((root,))
            self.assertEqual(str(error.exception), "Ephemeral cleanup failed")
            self.assertEqual(error.exception.reason, "filesystem-operation")
            self.assertIsNone(error.exception.__cause__)


if __name__ == "__main__":
    unittest.main()
