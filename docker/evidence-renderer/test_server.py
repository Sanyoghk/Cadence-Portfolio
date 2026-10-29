import importlib.util
from contextlib import nullcontext
import os
from pathlib import Path
import unittest
import http.client
import secrets
import threading
import tempfile
import subprocess
import sys
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("renderer_server", Path(__file__).with_name("server.py"))
server = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(server)
CHILD_SPEC = importlib.util.spec_from_file_location("renderer_child", Path(__file__).with_name("child.py"))
child = importlib.util.module_from_spec(CHILD_SPEC)
CHILD_SPEC.loader.exec_module(child)


class RendererBoundaryTest(unittest.TestCase):
    def test_operations_bytes_signature_and_encoding_are_validated_before_subprocess(self):
        with patch.object(server.subprocess, "Popen") as process:
            for operation, content in [("url", b"https://127.0.0.1"), ("html", b""),
                                       ("html", b"\xff"), ("html", b"x" * (server.MAX_HTML + 1)),
                                       ("pdf", b"not PDF"), ("pdf", b"%PDF-" + b"x" * server.MAX_PDF)]:
                with self.assertRaises(server.RenderFailure):
                    server.run_isolated(operation, content)
            process.assert_not_called()

    def test_namespace_failure_never_falls_back_to_unsafe_parser(self):
        with patch.object(server, "protect_parent"), patch.object(server, "purge_ephemeral"), patch.object(server.subprocess, "Popen") as start, patch.object(server.os, "killpg", create=True), patch.object(server.signal, "SIGKILL", 9, create=True), patch.object(server.os, "readlink", return_value="net:[1]"), patch.object(server, "pinned_directory", return_value=nullcontext(123)):
            start.return_value.returncode = 1
            start.return_value.pid = 99999999
            with self.assertRaises(server.RenderFailure):
                server.run_isolated("html", b"<h1>safe</h1>")
            args = start.call_args.args[0]
            self.assertEqual(args[:9], ["unshare", "--user", "--map-current-user", "--net", "--pid", "--mount", "--ipc", "--fork", "--kill-child"])
            self.assertNotIn("--mount-proc", args)
            self.assertNotIn("--no-sandbox", args)
            self.assertTrue(start.call_args.kwargs["close_fds"])
            self.assertEqual(start.call_count, 1)

    def test_timeout_poison_prevents_cleanup_and_the_next_tenant_even_after_outer_reap(self):
        try:
            with patch.object(server, "protect_parent"), patch.object(server, "purge_ephemeral") as cleanup, patch.object(server.subprocess, "Popen") as start, patch.object(server.os, "killpg", create=True) as kill, patch.object(server.signal, "SIGKILL", 9, create=True), patch.object(server.os, "readlink", return_value="net:[1]"), patch.object(server, "pinned_directory", return_value=nullcontext(123)):
                process = start.return_value
                process.pid = 99999999
                process.wait.side_effect = [server.subprocess.TimeoutExpired("fixture", 10), 0]
                with self.assertRaises(server.RenderFailure):
                    server.run_isolated("pdf", b"%PDF-fixture")
                self.assertTrue(server.POISONED)
                kill.assert_called_once_with(process.pid, 9)
                self.assertEqual(process.wait.call_count, 2)
                cleanup.assert_called_once()
                with self.assertRaises(server.RenderFailure):
                    server.run_isolated("pdf", b"%PDF-next-tenant")
                self.assertEqual(start.call_count, 1)
                cleanup.assert_called_once()
        finally:
            server.POISONED = False

    def test_kill_or_reap_failure_and_signalled_supervisor_poison_without_cleanup(self):
        for failure in ("kill", "reap", "signal"):
            try:
                with self.subTest(failure=failure), patch.object(server, "protect_parent"), patch.object(server, "purge_ephemeral") as cleanup, patch.object(server.subprocess, "Popen") as start, patch.object(server, "read_output", return_value=b"safe"), patch.object(server.os, "killpg", create=True) as kill, patch.object(server.signal, "SIGKILL", 9, create=True), patch.object(server.os, "readlink", return_value="net:[1]"), patch.object(server, "pinned_directory", return_value=nullcontext(123)):
                    process = start.return_value
                    process.pid = 99999999
                    process.returncode = -9 if failure == "signal" else 0
                    process.wait.side_effect = [0, server.subprocess.TimeoutExpired("fixture", 2)] if failure == "reap" else [0, 0]
                    if failure == "kill":
                        kill.side_effect = PermissionError("fixed fixture")
                    with self.assertRaises(server.RenderFailure):
                        server.run_isolated("pdf", b"%PDF-fixture")
                    self.assertTrue(server.POISONED)
                    cleanup.assert_called_once()
                    with self.assertRaises(server.RenderFailure):
                        server.run_isolated("pdf", b"%PDF-next-tenant")
                    self.assertEqual(start.call_count, 1)
            finally:
                server.POISONED = False

    def test_job_supervisor_protection_failure_precedes_input_or_parser_access(self):
        with patch.object(child.sys, "argv", ["child.py", "pdf", "/tmp/input", "/tmp/output"]), patch.object(child.os, "geteuid", return_value=10001), patch.object(child.os, "getpid", return_value=1), patch.object(child, "protect_supervisor", side_effect=SystemExit(1)), patch.object(child.Path, "read_bytes") as input_read, patch.object(child.Path, "read_text") as metadata_read, patch.object(child.subprocess, "run") as parser:
            with self.assertRaises(SystemExit):
                child.main()
            input_read.assert_not_called()
            metadata_read.assert_not_called()
            parser.assert_not_called()

    @unittest.skipUnless(sys.platform.startswith("linux"), "Linux process memory protection requires the renderer image")
    def test_job_supervisor_nondumpable_blocks_same_uid_proc_memory_and_fds(self):
        script = ("import importlib.util,pathlib,time; "
                  "spec=importlib.util.spec_from_file_location('child',"
                  "'/opt/evidence-renderer/child.py'); "
                  "module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module); "
                  "module.protect_supervisor(); print('ready',flush=True); time.sleep(10)")
        process = subprocess.Popen([sys.executable, "-I", "-c", script], stdin=subprocess.DEVNULL,
                                   stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, close_fds=True,
                                   env={"PATH": "/usr/local/bin:/usr/bin:/bin", "LANG": "C"})
        try:
            self.assertEqual(process.stdout.readline(), b"ready\n")
            server.namespace_probe.verify_parent_protection(process.pid)
        finally:
            process.terminate()
            try:
                process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=2)
            process.stdout.close()

    def test_readable_parent_is_rejected_before_parser_launch(self):
        with patch.object(server, "protect_parent", side_effect=server.RenderFailure()), patch.object(server.subprocess, "Popen") as process:
            with self.assertRaises(server.RenderFailure):
                server.run_isolated("html", b"<h1>safe</h1>")
            process.assert_not_called()

    def test_failed_cleanup_permanently_blocks_the_next_tenant(self):
        try:
            with patch.object(server, "protect_parent"), patch.object(server.ephemeral, "purge_ephemeral_storage", side_effect=RuntimeError("fixed fixture")), patch.object(server.subprocess, "Popen") as process:
                with self.assertRaises(server.RenderFailure):
                    server.run_isolated("html", b"<h1>first tenant</h1>")
                self.assertTrue(server.POISONED)
                process.assert_not_called()
            with patch.object(server, "protect_parent"), patch.object(server.ephemeral, "purge_ephemeral_storage") as cleanup, patch.object(server.subprocess, "Popen") as process:
                with self.assertRaises(server.RenderFailure):
                    server.run_isolated("html", b"<h1>next tenant</h1>")
                cleanup.assert_not_called()
                process.assert_not_called()
        finally:
            server.POISONED = False

    def test_pdf_text_limit_uses_utf16_units_without_splitting_supplementary_characters(self):
        for original, expected in [("a" * 16001, "a" * 16000),
                                   ("\U0001f600" * 8001, "\U0001f600" * 8000),
                                   ("\U0001f600" * 16000, "\U0001f600" * 8000),
                                   ("a" * 15999 + "\U0001f600", "a" * 15999),
                                   ("a" * 15998 + "\U0001f600", "a" * 15998 + "\U0001f600")]:
            result = server.bounded_text(original.encode("utf-8")).decode("utf-8")
            self.assertEqual(result, expected)
            self.assertLessEqual(len(result.encode("utf-16-le")) // 2, 16000)

    @unittest.skipUnless(os.name == "posix", "Linux descriptor semantics verified in renderer container suite")
    def test_output_symlink_fifo_hardlink_and_oversized_files_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            secret = base / "environment-fixture"
            secret.write_bytes(b"fixture-secret-must-not-be-returned")
            output = base / "output"
            with server.pinned_directory(directory) as descriptor:
                output.symlink_to(secret)
                with self.assertRaises((server.RenderFailure, OSError)):
                    server.read_output(descriptor, 1024)
                output.unlink()
                os.mkfifo(output)
                with self.assertRaises(server.RenderFailure):
                    server.read_output(descriptor, 1024)
                output.unlink()
                os.link(secret, output)
                with self.assertRaises(server.RenderFailure):
                    server.read_output(descriptor, 1024)
                output.unlink()
                output.write_bytes(b"x" * 1025)
                with self.assertRaises(server.RenderFailure):
                    server.read_output(descriptor, 1024)
                output.write_bytes(b"safe fixture")
                self.assertEqual(server.read_output(descriptor, 1024), b"safe fixture")

    @unittest.skipUnless(os.name == "posix", "Linux descriptor semantics verified in renderer container suite")
    def test_output_ancestor_replacement_cannot_redirect_pinned_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            original = base / "request"
            original.mkdir()
            (original / "output").write_bytes(b"safe original")
            replacement = base / "secret-directory"
            replacement.mkdir()
            (replacement / "output").write_bytes(b"fixture-secret-must-not-be-returned")
            with server.pinned_directory(original) as descriptor:
                original.rename(base / "moved-request")
                original.symlink_to(replacement, target_is_directory=True)
                self.assertEqual(server.read_output(descriptor, 1024), b"safe original")

    def test_http_auth_content_bounds_fixed_paths_and_failure_contract(self):
        server.Handler.token = secrets.token_urlsafe(32)
        listener = server.Server(("127.0.0.1", 0), server.Handler)
        thread = threading.Thread(target=listener.serve_forever, daemon=True)
        thread.start()
        try:
            cases = [({}, "/pdf/text", b"%PDF-x", 401),
                     ({"Authorization": "Bearer " + server.Handler.token}, "/arbitrary/path", b"%PDF-x", 404),
                     ({"Authorization": "Bearer " + server.Handler.token, "Content-Type": "text/plain"}, "/pdf/text", b"%PDF-x", 400),
                     ({"Authorization": "Bearer " + server.Handler.token, "Content-Type": "application/pdf", "Content-Length": str(server.MAX_PDF + 1)}, "/pdf/text", b"", 413)]
            with patch.object(server, "run_isolated") as parser:
                for headers, path, body, expected in cases:
                    connection = http.client.HTTPConnection("127.0.0.1", listener.server_port, timeout=2)
                    connection.request("POST", path, body=body, headers=headers)
                    response = connection.getresponse()
                    self.assertEqual(response.status, expected)
                    self.assertEqual(response.read(), b"")
                    connection.close()
                parser.assert_not_called()
            with patch.object(server, "run_isolated", side_effect=server.RenderFailure()):
                connection = http.client.HTTPConnection("127.0.0.1", listener.server_port, timeout=2)
                connection.request("POST", "/pdf/text", body=b"%PDF-x", headers={"Authorization": "Bearer " + server.Handler.token, "Content-Type": "application/pdf"})
                self.assertEqual(connection.getresponse().status, 503)
                connection.close()
        finally:
            listener.shutdown()
            listener.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
