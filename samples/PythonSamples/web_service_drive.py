#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O web_service_drive.py

import time

from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.streams.aes_stream import ProviderType
from salmon_core.salmon.bridge.native_proxy import NativeProxy
from salmon_fs.fs.file.file import File
from salmon_fs.fs.file.ws_file import WSFile
from salmon_fs.fs.file.credentials import Credentials
from salmon_fs.fs.file.http_sync_client import HttpSyncClient

from samples.drive_sample import DriveSample

ws_service_path = "https://localhost:8443"
ws_user = "user"
ws_password = "password"
drive_path = "/example_drive_" + str(round(time.time() * 1000))
password = "test123"

# enable only if you're testing with an HTTP server
#  In all other cases you should be using an HTTPS server
#  HttpSyncClient.set_allow_clear_text_traffic(True)

# Set with the path to the salmon library if you use the native AES providers, see README.txt for instructions
# NativeProxy.set_library_path("/path/to/lib/salmon.dll|libsalmon.so|libsalmon.dylib")
AesStream.set_aes_provider_type(ProviderType.Default)

if __name__ == '__main__':
    files_to_import = [File("./data/file.txt")]

    v_dir = File("./output")
    if not v_dir.exists():
        v_dir.mkdir()
    export_dir = v_dir.get_child("export")
    if not export_dir.exists():
        export_dir.mkdir()

    drive_dir = WSFile(drive_path, ws_service_path, Credentials(ws_user, ws_password))
    if not drive_dir.exists():
        drive_dir.mkdir()

    DriveSample.create_drive(drive_dir, password)
    ws_drive = DriveSample.open_drive(drive_dir, password)
    DriveSample.import_files(ws_drive, files_to_import)
    DriveSample.list_files(ws_drive)
    DriveSample.export_files(ws_drive, export_dir)
    DriveSample.close_drive(ws_drive)
