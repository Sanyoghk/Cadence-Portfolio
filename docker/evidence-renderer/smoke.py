import importlib.util
from pathlib import Path
import struct

spec = importlib.util.spec_from_file_location("renderer", Path(__file__).with_name("server.py"))
renderer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(renderer)
renderer.protect_parent(configure=True)
png = renderer.run_isolated("html", b'<html><body><h1>Website evidence</h1><script>document.body.remove()</script><img src="http://169.254.169.254/"></body></html>')
assert png[:8] == b"\x89PNG\r\n\x1a\n"
assert struct.unpack(">II", png[16:24]) == (1280, 720)
print("isolated screenshot verified: 1280x720")
