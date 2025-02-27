#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O web_service_drive.py

import time

from salmon_core.salmon.streams.salmon_stream import SalmonStream
from salmon_core.salmon.streams.salmon_stream import ProviderType
from salmon_fs.file.py_file import PyFile
from salmon_fs.file.py_ws_file import PyWSFile, Credentials

from samples.drive_sample import DriveSample

ws_service_path = "http://localhost:8080"
ws_user = "user"
ws_password = "password"
drive_path = "/example_drive_" + str(round(time.time() * 1000))
password = "test123"

SalmonStream.set_aes_provider_type(ProviderType.Default)

files_to_import = [PyFile("./data/file.txt")]

v_dir = PyFile("./output")
if not v_dir.exists():
    v_dir.mkdir()
export_dir = v_dir.get_child("export")
if not export_dir.exists():
    export_dir.mkdir()

drive_dir = PyWSFile(drive_path, ws_service_path, Credentials(ws_user, ws_password))
if not drive_dir.exists():
    drive_dir.mkdir()

DriveSample.create_drive(drive_dir, password)
ws_drive = DriveSample.open_drive(drive_dir, password)
DriveSample.import_files(ws_drive, files_to_import)
DriveSample.list_files(ws_drive)
DriveSample.export_files(ws_drive, export_dir)
DriveSample.close_drive(ws_drive)
