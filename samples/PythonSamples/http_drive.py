#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O http_drive.py

from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.streams.aes_stream import ProviderType
from salmon_core.salmon.bridge.native_proxy import NativeProxy
from salmon_fs.fs.file.file import File
from salmon_fs.fs.file.http_file import HttpFile
from salmon_fs.fs.file.http_sync_client import HttpSyncClient
from salmon_fs.fs.file.credentials import Credentials

from samples.drive_sample import DriveSample

http_drive_url = "https://localhost/testvault"
password = "test"
threads = 1
http_user = "user"
http_password = "password"

# enable only if you're testing with an HTTP server
#  In all other cases you should be using an HTTPS server
#  HttpSyncClient.set_allow_clear_text_traffic(True)

# Set with the path to the salmon library if you use the native AES providers, see README.txt for instructions
# NativeProxy.set_library_path("/path/to/lib/salmon.dll|libsalmon.so|libsalmon.dylib")
AesStream.set_aes_provider_type(ProviderType.Default)

if __name__ == '__main__':
    v_dir = File("./output")
    if not v_dir.exists():
        v_dir.mkdir()
    export_dir = v_dir.get_child("export")
    if not export_dir.exists():
        export_dir.mkdir()

    http_dir = HttpFile(http_drive_url, Credentials(http_user, http_password))
    http_drive = DriveSample.open_drive(http_dir, password)
    DriveSample.list_files(http_drive)
    DriveSample.export_files(http_drive, export_dir, threads)
    DriveSample.close_drive(http_drive)
