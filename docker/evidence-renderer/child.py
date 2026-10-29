"""Runs only inside an isolated user/network namespace, with fixed arguments and OS limits."""
import os
import ctypes
from pathlib import Path
import resource
import subprocess
import sys


def protect_supervisor():
    # A parser with the same UID must not modify this trusted namespace PID1 or
    # clear the unshare parent-death signal through /proc/mem or process inspection.
    library = ctypes.CDLL(None, use_errno=True)
    library.prctl.argtypes = [ctypes.c_int] + [ctypes.c_ulong] * 4
    library.prctl.restype = ctypes.c_int
    if (library.prctl(4, 0, 0, 0, 0) != 0
            or library.prctl(3, 0, 0, 0, 0) != 0):
        raise SystemExit(1)
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def limits(pdf):
    resource.setrlimit(resource.RLIMIT_CPU, (7, 7))
    file_limit = (3 if pdf else 64) * 1024 * 1024
    resource.setrlimit(resource.RLIMIT_FSIZE, (file_limit, file_limit))
    resource.setrlimit(resource.RLIMIT_NOFILE, (1024, 1024))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    if pdf:
        resource.setrlimit(resource.RLIMIT_AS, (256 * 1024 * 1024, 256 * 1024 * 1024))


def render_html(source, target):
    from playwright.sync_api import sync_playwright
    content = source.read_bytes().decode("utf-8", errors="strict")
    # Inject a restrictive CSP before untrusted HTML; scripts additionally disabled in context.
    html = ('<!doctype html><meta http-equiv="Content-Security-Policy" '
            'content="default-src \'none\'; style-src \'unsafe-inline\'; '
            'script-src \'none\'; object-src \'none\'; frame-src \'none\'; '
            'connect-src \'none\'; img-src \'none\'; font-src \'none\'; '
            'base-uri \'none\'; form-action \'none\'">') + content
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True, chromium_sandbox=True,
                                              timeout=5000)
        context = browser.new_context(java_script_enabled=False, service_workers="block",
                                      accept_downloads=False, viewport={"width": 1280, "height": 720})
        context.route("**/*", lambda route: route.abort())
        page = context.new_page()
        page.set_content(html, wait_until="domcontentloaded", timeout=3000)
        page.screenshot(path=str(target), type="png", full_page=False,
                        animations="disabled", timeout=3000)
        context.close()
        browser.close()


def main():
    if (len(sys.argv) != 4 or sys.argv[1] not in ("html", "pdf")
            or os.geteuid() != 10001 or os.getpid() != 1):
        raise SystemExit(1)
    protect_supervisor()
    operation = sys.argv[1]
    source, target = Path(sys.argv[2]), Path(sys.argv[3])
    # A new namespace must differ from PID1's network; never silently parse in server network.
    interfaces = {line.split(":", 1)[0].strip() for line in Path("/proc/net/dev").read_text().splitlines() if ":" in line}
    if not os.environ.get("CADENCE_PARENT_NET_NAMESPACE") or os.readlink("/proc/self/ns/net") == os.environ["CADENCE_PARENT_NET_NAMESPACE"] or interfaces != {"lo"}:
        raise SystemExit(1)
    limits(operation == "pdf")
    if operation == "html":
        render_html(source, target)
    else:
        with target.open("wb") as output:
            subprocess.run(["/usr/bin/pdftotext", "-enc", "UTF-8", "-f", "1", "-l", "20",
                            "-nopgbrk", str(source), "-"], stdin=subprocess.DEVNULL,
                           stdout=output, stderr=subprocess.DEVNULL, timeout=8, check=True)


if __name__ == "__main__":
    main()
