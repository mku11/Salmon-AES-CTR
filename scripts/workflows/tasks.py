import subprocess
import time
import psutil
import os
import sys
from pathlib import Path

log_file = True
log_console = True
log_file_dir = "/tmp"
log_file_name = "salmon-ci-log"
log_file_ext = "txt"

def get_log_file_path(name):
    return f"{log_file_dir}/{log_file_name}_{name}.{log_file_ext}"

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


def log(text: str, name):
    if log_console:
        print(text)
    if log_file:
        file_path = get_log_file_path(name)
        with open(file_path, "a") as wfile:
            wfile.write(text + "\n")
            wfile.flush()


def log_result(result: subprocess.CompletedProcess[str], name: str):
    log("Return code: " + str(result.returncode), name)
    log(result.stdout.strip(), name)
    if result.stderr:
        log("Error: '" + result.stderr.strip() + "'", name)


def setup_ws_server(cmd: list[str], directory: str, env: dict):
    log("", name)
    log("cmd: " + str.join(" ", cmd), name)

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
    log_result(result, name)
    if result.returncode:
        exit(1)


def start_ws_server(cmd: list[str], directory: str, env: dict):
    name = "start_ws_server"
    log("\n\n", name)
    log("cmd: " + str.join(" ", cmd), name)
    log("dir: " + directory, name)
    log("env: " + str(env), name)

    if process_started("webfs-service.war"):
        log(f"process: {cmd} has already started", name)
        return

    if not directory.startswith("/"):
        directory = (Path(__file__).parent / directory).resolve()
    log("directory: " + str(directory), name)

    my_env = os.environ.copy()
    my_env.update(env)

    result = subprocess.Popen(
        cmd,
        text=True,
        cwd=directory,
        env=my_env,
    )
    if result.returncode:
        exit(1)

    log("sleep until server settles", name)
    time.sleep(20)
    
    log("server started", name)
    

def run_test(name: str, cmd: list[str], directory: str, env: dict):
    log("\n\n", name)
    log("name: " + name, name)
    log("cmd: " + str.join(" ", cmd), name)
    log("dir: " + directory, name)
    log("env: " + str(env), name)

    if not directory.startswith("/"):
        directory = (Path(__file__).parent / directory).resolve()

    my_env = os.environ.copy()
    my_env.update(env)

    result: subprocess.CompletedProcess[str] = subprocess.run(
        cmd, capture_output=True, text=True, cwd=directory, env=my_env
    )
    log_result(result, name)
    if result.returncode:
        exit(1)
