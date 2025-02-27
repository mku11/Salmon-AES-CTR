#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O http_drive.py

from salmon_core.salmon.streams.salmon_stream import SalmonStream
from salmon_core.salmon.streams.salmon_stream import ProviderType
from salmon_fs.file.py_file import PyFile
from salmon_fs.file.py_http_file import PyHttpFile

from samples.drive_sample import DriveSample

http_drive_url = "http://localhost/saltest/httpserv/vault"
password = "test123"
threads = 2

SalmonStream.set_aes_provider_type(ProviderType.Default)

v_dir = PyFile("./output")
if not v_dir.exists():
    v_dir.mkdir()
export_dir = v_dir.get_child("export")
if not export_dir.exists():
    export_dir.mkdir()

http_dir = PyHttpFile(http_drive_url)
http_drive = DriveSample.open_drive(http_dir, password)
DriveSample.list_files(http_drive)
DriveSample.export_files(http_drive, export_dir, threads)
DriveSample.close_drive(http_drive)
