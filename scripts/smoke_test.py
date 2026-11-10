#!/usr/bin/env python3
"""Launch a development game, check its startup and exercise the cauldron on a server."""
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
    command = ["./gradlew", "runServer", "--no-daemon", "--console=plain"]
    marker = "Done ("
elif mode == "client":
    command = ["xvfb-run", "-a", "./gradlew", "runClient", "--no-daemon", "--console=plain"]
    # Resource/model loading occurs after CLIENT_STARTED during the splash screen.
    marker = "minecraft:textures/atlas/blocks.png-atlas"
else:
    raise SystemExit("expected server or client")

proc = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                        text=True, errors="replace", bufsize=1, start_new_session=True)
lines = []
output = queue.Queue()
def read_output():
    for line in proc.stdout:
        output.put(line)
threading.Thread(target=read_output, daemon=True).start()
deadline = time.monotonic() + 360

def await_marker(expected):
    while time.monotonic() < deadline:
        try:
            line = output.get(timeout=1)
            print(line, end="", flush=True)
            lines.append(line)
            if expected in line:
                return True
        except queue.Empty:
            if proc.poll() is not None and output.empty():
                return False
    return False

def send(command):
    proc.stdin.write(command + "\n")
    proc.stdin.flush()

success = False
try:
    success = await_marker(marker)
    if success and mode == "client":
        # Give model baking time to finish and reject errors involving mod resources.
        end = time.monotonic() + 15
        while time.monotonic() < end and proc.poll() is None:
            try:
                line = output.get(timeout=1)
                print(line, end="", flush=True)
                lines.append(line)
            except queue.Empty:
                pass
        if proc.poll() is not None:
            success = False
        for line in lines:
            low = line.lower()
            if "alkahest" in low and any(word in low for word in ("error", "unable to load", "failed", "missing")):
                success = False
    if success and mode == "server":
        # A pre-0.1.2 world has water in block-entity NBT but no filled block state.
        send("forceload add 0 0")
        time.sleep(4)
        send("setblock 0 120 0 alkahest:alchemy_cauldron")
        send("data merge block 0 120 0 {Water:1b}")
        time.sleep(2)
        send("execute if block 0 120 0 alkahest:alchemy_cauldron[filled=true] run say ALKAHEST_WATER_FILLED_OK")
        success = await_marker("ALKAHEST_WATER_FILLED_OK")
        if success:
            send("data merge block 0 120 0 {Water:0b}")
            time.sleep(2)
            send("execute if block 0 120 0 alkahest:alchemy_cauldron[filled=false] run say ALKAHEST_WATER_EMPTY_OK")
            success = await_marker("ALKAHEST_WATER_EMPTY_OK")
        if success:
            send("setblock 0 119 0 minecraft:magma_block")
            send("data merge block 0 120 0 {Water:1b,Evaporation:399}")
            time.sleep(2)
            send("execute if block 0 120 0 alkahest:alchemy_cauldron[filled=false] run say ALKAHEST_EVAPORATION_OK")
            success = await_marker("ALKAHEST_EVAPORATION_OK")
finally:
    if proc.poll() is None:
        os.killpg(proc.pid, signal.SIGTERM)
        try:
            proc.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(proc.pid, signal.SIGKILL)
            proc.wait()
if not success:
    errors = [i for i, line in enumerate(lines) if "Caused by:" in line or "FAILURE:" in line or "error:" in line]
    summary = "".join(lines[max(0, errors[0] - 4):errors[0] + 25]) if errors else "".join(lines[-35:])
    summary = (f"{mode} failed its smoke test.\n" + summary)[:3900]
    print("::error::" + summary.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"))
    raise SystemExit(1)
print(f"{mode} smoke test passed")
