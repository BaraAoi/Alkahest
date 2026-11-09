#!/usr/bin/env python3
"""Launch the real development game until Fabric finishes starting, then stop it."""
import os
import queue
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path

mode = sys.argv[1]
if mode == "server":
    run = Path("run")
    run.mkdir(exist_ok=True)
    (run / "eula.txt").write_text("eula=true\n")
    (run / "server.properties").write_text("online-mode=false\nserver-port=0\n")
    command = ["./gradlew", "runServer", "--no-daemon"]
    marker = "Done ("
elif mode == "client":
    command = ["xvfb-run", "-a", "./gradlew", "runClient", "--no-daemon"]
    marker = "Alkahest client started"
else:
    raise SystemExit("expected server or client")

proc = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                        text=True, errors="replace", bufsize=1, start_new_session=True)
lines = []
output = queue.Queue()
def read_output():
    for line in proc.stdout:
        output.put(line)
threading.Thread(target=read_output, daemon=True).start()
deadline = time.monotonic() + 360
success = False
try:
    while time.monotonic() < deadline:
        try:
            line = output.get(timeout=1)
            print(line, end="", flush=True)
            lines.append(line)
            if marker in line:
                success = True
                break
        except queue.Empty:
            if proc.poll() is not None and output.empty():
                break
finally:
    if proc.poll() is None:
        os.killpg(proc.pid, signal.SIGTERM)
        try:
            proc.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(proc.pid, signal.SIGKILL)
            proc.wait()
if not success:
    # Check annotations remain accessible even if the CI log hosting is unavailable.
    errors = [i for i, line in enumerate(lines) if "Caused by:" in line or "FAILURE:" in line or "error:" in line]
    summary = "".join(lines[max(0, errors[0] - 4):errors[0] + 25]) if errors else "".join(lines[-35:])
    summary = (f"{mode} failed to reach startup marker.\n" + summary)[:3900]
    print("::error::" + summary.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"))
    raise SystemExit(1)
print(f"{mode} startup verified")
