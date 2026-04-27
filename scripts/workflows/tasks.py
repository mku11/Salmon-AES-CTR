import subprocess
import time
import psutil
import os
import sys
from pathlib import Path

log_file = True
log_console = True
log_file_path = "/tmp/salmon-ci-log.txt"


def process_started(name):
    for proc in psutil.process_iter(["pid", "name", "cmdline"]):
        try:
            cmd = proc.info["cmdline"]
            if cmd and any(name in arg for arg in cmd):
                proc_cmd = str.join(" ", list(cmd))
                print(f"Found process PID: {proc.info['pid']} {proc_cmd}")
                return True
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            continue
    return False


def log(text: str):
    if log_console:
        print(text)
    if log_file:
        with open(log_file_path, "a") as wfile:
            wfile.write(text + "\n")
            wfile.flush()


def log_result(result: subprocess.CompletedProcess[str]):
    log("Return code: " + str(result.returncode))
    log(result.stdout.strip())
    if result.stderr:
        log("Error: '" + result.stderr.strip() + "'")


def setup_ws_server(cmd: list[str], directory: str, env: dict):
    log("")
    log("cmd: " + str.join(" ", cmd))

    if process_started(cmd[1]):
        log(f"process: {cmd} has already started")
        return

    if not directory.startswith("/"):
        directory = (Path(__file__).parent / directory).resolve()
    log("directory: " + str(directory))

    my_env = os.environ.copy()
    my_env.update(env)

    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        cwd=directory,
        env=my_env,
    )
    log_result(result)
    if result.returncode:
        exit(1)


def start_ws_server(cmd: list[str], directory: str, env: dict):
    log("\n\n")
    log("cmd: " + str.join(" ", cmd))
    log("dir: " + directory)
    log("env: " + str(env))

    if process_started("webfs-service.war"):
        log(f"process: {cmd} has already started")
        return

    if not directory.startswith("/"):
        directory = (Path(__file__).parent / directory).resolve()
    log("directory: " + str(directory))

    my_env = os.environ.copy()
    my_env.update(env)

    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        cwd=directory,
        env=my_env,
    )
    log_result(result)
    if result.returncode:
        exit(1)

def run_test(cmd: list[str], directory: str, env: dict):
    log("\n\n")
    log("cmd: " + str.join(" ", cmd))
    log("dir: " + directory)
    log("env: " + str(env))

    if not directory.startswith("/"):
        directory = (Path(__file__).parent / directory).resolve()

    my_env = os.environ.copy()
    my_env.update(env)

    result: subprocess.CompletedProcess[str] = subprocess.run(
        cmd, capture_output=True, text=True, cwd=directory, env=my_env
    )
    log_result(result)
    if result.returncode:
        exit(1)
