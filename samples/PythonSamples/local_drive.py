#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O local_drive.py

import time

from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.streams.provider_type import ProviderType
from salmon_core.salmon.bridge.native_proxy import NativeProxy
from salmon_fs.fs.file.file import File

from samples.drive_sample import DriveSample

password = "test123"
threads = 1

# Set with the path to the salmon library if you use the native AES providers, see README.txt for instructions
# NativeProxy.set_library_path("/path/to/lib/salmon.dll|libsalmon.so|libsalmon.dylib")
AesStream.set_aes_provider_type(ProviderType.Default)

if __name__ == '__main__':
    # directories and files
    v_dir = File("./output")
    if not v_dir.exists():
        v_dir.mkdir()

    # create
    drive_dir = v_dir.get_child("drive_" + str(round(time.time() * 1000)))
    if not drive_dir.exists():
        drive_dir.mkdir()
    DriveSample.create_drive(drive_dir, password)

    # open
    local_drive = DriveSample.open_drive(drive_dir, password)

    # import
    files_to_import = [File("./data/file.txt")]
    DriveSample.import_files(local_drive, files_to_import, threads)

    # list
    DriveSample.list_files(local_drive)

    # export the files
    export_dir = drive_dir.get_child("export")
    if not export_dir.exists():
        export_dir.mkdir()
    DriveSample.export_files(local_drive, export_dir, threads)

    DriveSample.close_drive(local_drive)
