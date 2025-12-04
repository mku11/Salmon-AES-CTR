#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O http_drive.py

from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.streams.aes_stream import ProviderType
from simple_fs.fs.file.file import File
from simple_fs.fs.file.http_file import HttpFile
from simple_fs.fs.file.http_sync_client import HttpSyncClient
from simple_fs.fs.file.credentials import Credentials

from common import Common
from samples.drive_sample import DriveSample

http_drive_url = "https://localhost/testvault"
password = "test"
threads = 1
http_user = "user"
http_password = "password"

print("Starting Salmon HTTP Sample")
print("make sure your HTTP server is up and running to run this sample, see scripts/misc/start_http_server.bat")

# enable only if you're testing with an HTTP server
# In all other cases you should be using an HTTPS server
# HttpSyncClient.set_allow_clear_text_traffic(True)

# uncomment to set the native library for performance
# Common.set_native_library()
# set the provider (see ProviderType)
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
