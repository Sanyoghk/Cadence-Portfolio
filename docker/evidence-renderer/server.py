"""Authenticated fixed-operation renderer. Remote data never selects a URL/path/command."""
import hmac
import http.server
import ctypes
import importlib.util
from contextlib import contextmanager
import os
from pathlib import Path
import re
import signal
import socket
import stat
import subprocess
import sys
import tempfile
import threading
import time

_EPHEMERAL = importlib.util.spec_from_file_location("ephemeral", Path(__file__).with_name("ephemeral.py"))
ephemeral = importlib.util.module_from_spec(_EPHEMERAL)
_EPHEMERAL.loader.exec_module(ephemeral)
_PROBE = importlib.util.spec_from_file_location("namespace_probe", Path(__file__).with_name("namespace_probe.py"))
namespace_probe = importlib.util.module_from_spec(_PROBE)
_PROBE.loader.exec_module(namespace_probe)

MAX_HTML = 2 * 1024 * 1024
MAX_PDF = 10 * 1024 * 1024
MAX_PNG = 3 * 1024 * 1024
MAX_TEXT = 64 * 1024
DEADLINE = 10
SLOTS = threading.BoundedSemaphore(1)
VERSION = "isolated-v1"
FILESYSTEM_SLOT = threading.BoundedSemaphore(1)
POISONED = False


def protect_parent(configure=False):
    """Only the server owns credentials; inherited proc must never expose its memory or FDs."""
    global POISONED
    try:
        library = ctypes.CDLL(None, use_errno=True)
        library.prctl.argtypes = [ctypes.c_int] + [ctypes.c_ulong] * 4
        library.prctl.restype = ctypes.c_int
        if configure and library.prctl(4, 0, 0, 0, 0) != 0:  # PR_SET_DUMPABLE
            raise RenderFailure()
        if library.prctl(3, 0, 0, 0, 0) != 0:  # PR_GET_DUMPABLE
            raise RenderFailure()
    except (OSError, AttributeError, RenderFailure) as error:
        POISONED = True
        raise RenderFailure() from error


def purge_ephemeral():
    global POISONED
    try:
        ephemeral.purge_ephemeral_storage()
    except (OSError, ValueError, RuntimeError) as error:
        POISONED = True
        raise RenderFailure() from error


class RenderFailure(Exception):
    pass


@contextmanager
def pinned_directory(directory):
    descriptor = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC)
    try:
        yield descriptor
    finally:
        os.close(descriptor)


def read_output(directory_descriptor, limit):
    # The parser controls output names, inode type and bytes; the server must not follow links.
    descriptor = os.open("output", os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK | os.O_CLOEXEC,
                         dir_fd=directory_descriptor)
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1 or metadata.st_size > limit:
            raise RenderFailure()
        with os.fdopen(descriptor, "rb", closefd=False) as output:
            result = output.read(limit + 1)
        if len(result) > limit:
            raise RenderFailure()
        return result
    finally:
        os.close(descriptor)


def bounded_text(result):
    text = result.decode("utf-8", errors="strict")
    text = " ".join("".join(char if char.isprintable() else " " for char in text).split())
    # Java's public contract counts UTF-16 units; supplementary characters consume two units.
    characters = []
    units = 0
    for character in text:
        width = 2 if ord(character) > 0xffff else 1
        if units + width > 16000:
            break
        characters.append(character)
        units += width
    return "".join(characters).encode("utf-8")


def run_isolated(operation, content):
    if operation not in ("html", "pdf"):
        raise RenderFailure()
    if not content or len(content) > (MAX_HTML if operation == "html" else MAX_PDF):
        raise RenderFailure()
    if operation == "pdf" and not content.startswith(b"%PDF-"):
        raise RenderFailure()
    if operation == "html":
        try:
            content.decode("utf-8", errors="strict")
        except UnicodeError as error:
            raise RenderFailure() from error
    protect_parent()
    if POISONED or not FILESYSTEM_SLOT.acquire(blocking=False):
        raise RenderFailure()
    try:
        purge_ephemeral()
        return run_child(operation, content)
    finally:
        try:
            if not POISONED:
                purge_ephemeral()
        finally:
            FILESYSTEM_SLOT.release()


def run_child(operation, content):
    global POISONED
    directory = tempfile.mkdtemp(prefix="cadence-render-")
    source = Path(directory) / "input"
    target = Path(directory) / "output"
    source.write_bytes(content)
    environment = {"PATH": "/usr/local/bin:/usr/bin:/bin", "HOME": directory,
                   "TMPDIR": directory, "TZ": "UTC", "PLAYWRIGHT_BROWSERS_PATH": "/ms-playwright",
                   "CADENCE_PARENT_NET_NAMESPACE": os.readlink("/proc/self/ns/net")}
    # Preserve non-root UID inside the namespace: -r would make Chromium see root.
    # Keep Docker's locked proc masks; remounting a fresh proc would hide those barriers.
    args = ["unshare", "--user", "--map-current-user", "--net", "--pid", "--mount", "--ipc", "--fork", "--kill-child",
            "--", "python3", "-I", "/opt/evidence-renderer/child.py", operation,
            str(source), str(target)]
    # Pin before spawning: changing the UUID directory cannot redirect the parent read.
    with pinned_directory(directory) as directory_descriptor:
        process = subprocess.Popen(args, env=environment, stdin=subprocess.DEVNULL,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                   start_new_session=True, close_fds=True)
        try:
            process.wait(timeout=DEADLINE)
            if process.returncode != 0:
                if process.returncode < 0:
                    # A signalled unshare supervisor cannot prove its PID namespace died.
                    POISONED = True
                raise RenderFailure()
            limit = MAX_PNG if operation == "html" else MAX_TEXT
            result = read_output(directory_descriptor, limit)
            if operation == "html":
                if not result.startswith(b"\x89PNG\r\n\x1a\n"):
                    raise RenderFailure()
                return result
            return bounded_text(result)
        except subprocess.TimeoutExpired as error:
            # --kill-child uses mutable PR_SET_PDEATHSIG in the child. A compromised
            # job PID1 could clear it and escape the process group. Never purge or
            # admit another tenant after a deadline without namespace death proof.
            POISONED = True
            raise RenderFailure() from error
        except (UnicodeError, OSError) as error:
            raise RenderFailure() from error
        finally:
            # Best-effort process-group kill/reap; uncertain namespace teardown poisons reuse.
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            except OSError as error:
                POISONED = True
                raise RenderFailure() from error
            try:
                process.wait(timeout=2)
            except (subprocess.SubprocessError, OSError) as error:
                POISONED = True  # Never purge or admit a tenant with a potentially living parser.
                raise RenderFailure() from error

class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "cadence-renderer"
    sys_version = ""
    token = None

    def setup(self):
        super().setup()
        self.connection.settimeout(3)
        self.watchdog = threading.Timer(20, self.close_socket)
        self.watchdog.daemon = True
        self.watchdog.start()

    def close_socket(self):
        try:
            self.connection.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.connection.close()

    def finish(self):
        self.watchdog.cancel()
        super().finish()

    def log_message(self, *_args):
        pass  # No content, authorization header, filename or provider diagnostic in logs.

    def reply(self, status, body=b"", media_type="application/octet-stream"):
        self.close_connection = True
        self.send_response(status)
        self.send_header("Content-Type", media_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.send_header("X-Cadence-Renderer-Version", VERSION)
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        # Liveness gives no configuration or signature details and does not run the parser.
        if self.path == "/health":
            self.reply(503 if POISONED else 200, b"" if POISONED else b"ok", "text/plain")
        else:
            self.reply(404)

    def do_POST(self):
        if not hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + self.token):
            self.reply(401)
            return
        operation = {"/html/screenshot": "html", "/pdf/text": "pdf"}.get(self.path)
        if operation is None:
            self.reply(404)
            return
        lengths = self.headers.get_all("Content-Length", [])
        expected_media = "text/html; charset=utf-8" if operation == "html" else "application/pdf"
        if (len(lengths) != 1 or not re.fullmatch(r"[1-9][0-9]{0,7}", lengths[0])
                or self.headers.get("Transfer-Encoding") is not None
                or self.headers.get("Content-Type") != expected_media):
            self.reply(400)
            return
        size = int(lengths[0])
        if size > (MAX_HTML if operation == "html" else MAX_PDF):
            self.reply(413)
            return
        if not SLOTS.acquire(blocking=False):
            self.reply(503)
            return
        try:
            # A connection trickling bytes cannot reset a per-read timeout forever.
            start = time.monotonic()
            chunks = []
            remaining = size
            while remaining:
                budget = 3 - (time.monotonic() - start)
                if budget <= 0:
                    raise RenderFailure()
                self.connection.settimeout(budget)
                data = self.rfile.read1(min(65536, remaining))
                if not data:
                    raise RenderFailure()
                chunks.append(data)
                remaining -= len(data)
            result = run_isolated(operation, b"".join(chunks))
            self.reply(200, result, "image/png" if operation == "html" else "text/plain; charset=utf-8")
        except (RenderFailure, OSError, subprocess.SubprocessError):
            self.reply(503)
        finally:
            SLOTS.release()


class Server(http.server.HTTPServer):
    request_queue_size = 8

    def handle_error(self, *_args):
        pass


def main():
    if os.getpid() != 1 or os.getuid() != 10001 or len(sys.argv) != 1:
        raise SystemExit("Invalid renderer process boundary")
    protect_parent(configure=True)
    expected_profile = os.environ.get("CADENCE_RENDERER_APPARMOR_PROFILE", "")
    if expected_profile and expected_profile != namespace_probe.PROFILE:
        raise SystemExit("Invalid renderer profile configuration")
    if namespace_probe.preflight(require_profile=bool(expected_profile)) != 0:
        raise SystemExit("Invalid renderer sandbox boundary")
    purge_ephemeral()
    token = os.environ.get("CADENCE_RENDERER_TOKEN", "")
    if not re.fullmatch(r"[A-Za-z0-9_-]{32,128}", token):
        raise SystemExit("Invalid renderer configuration")
    Handler.token = token
    # Single request at a time; listen backlog is capped, no unbounded thread creation.
    server = Server(("0.0.0.0", 8081), Handler)
    def stop(_signal, _frame):
        raise SystemExit(0)
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    try:
        server.serve_forever(poll_interval=0.2)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
