import subprocess
import time
import psutil
import os
import sys
from pathlib import Path

enable_log_file = True
enable_log_console = True
log_file_dir = "/tmp"
log_file_name = "salmon-ci-log"
err_file_name = "salmon-ci-err"
log_file_ext = "txt"
use_unique_logfiles = True

def get_unique_name(name: str):
    return f"{name}.{int(time.time() * 1000)}"

def get_log_file_path(name, error: bool = False):
    file_name = log_file_name if not error else err_file_name
    return f"{log_file_dir}/{file_name}_{name}.{log_file_ext}"

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


def err(text: str, file = None):
    log(text, file, True)

def log(text: str, file = None, error: bool = False):
    if enable_log_console:
        if error:
            print(text, end='', file=sys.stderr,  flush=True)
        else:
            print(text, end='', flush=True)
    if enable_log_file and file:
        file.write(text)
        file.flush()

def submit(name: str, cmd: list[str], directory: str, env: dict, delay = 0, block = True):
    if use_unique_logfiles:
        name = get_unique_name(name)
    log_file = get_log_file_path(name,False)
    with open(log_file, "a") as flog_file:
        log("\n\n", flog_file)
        log("name: " + name + "\n", flog_file)
        log("cmd: " + str.join(" ", cmd)+ "\n", flog_file)
        log("dir: " + directory+ "\n", flog_file)
        log("env: " + str(env)+ "\n", flog_file)
        log("log_file: " + log_file+ "\n", flog_file)
        log("\n", flog_file)
        
        try:
            if not directory.startswith("/"):
                directory = (Path(__file__).parent / directory).resolve()

            my_env = os.environ.copy()
            my_env.update(env)
            
            process = subprocess.Popen(cmd, 
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True, cwd=directory, env=my_env
                )
            
            return_code = 0
            if block:
                for line in process.stdout:
                    log(line, flog_file)            
                return_code = process.wait()
                
            if delay:
                log("delaying secs: " + str(delay) + "\n", flog_file)
                time.sleep(delay)
                
            log("Return code: " + str(return_code) + "\n", flog_file)
            if return_code:
                exit(1)
                
        except Exception as ex:
            err(f"Error during workflow submit: {str(ex)}\n", flog_file)

def setup_ws_server(cmd: list[str], directory: str, env: dict):
    name = "setup_ws_server"
    if process_started(cmd[1]) or process_started("webfs-service.war"):
        log(f"process: {cmd} has already started\n")
        return
    submit(name, cmd, directory, env)
        
def start_ws_server(cmd: list[str], directory: str, env: dict):
    name = "start_ws_server"
    if process_started("webfs-service.war"):
        log(f"process: {cmd} has already started\n")
        return
    submit(name, cmd, directory, env, 10, block=False)
    
def run_test(name: str, cmd: list[str], directory: str, env: dict):
    submit(name, cmd, directory, env)
    
def run_build(name: str, cmd: list[str], directory: str, env: dict):
    submit(name, cmd, directory, env)
