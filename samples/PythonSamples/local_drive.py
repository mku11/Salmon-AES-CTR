#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O local_drive.py

import time

from salmon_fs.file.py_file import PyFile

from samples.drive_sample import DriveSample

password = "test123"
threads = 2

# directories and files
v_dir = PyFile("./output")
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
files_to_import = [PyFile("./data/file.txt")]
DriveSample.import_files(local_drive, files_to_import, threads)

# list
DriveSample.list_files(local_drive)

# export the files
export_dir = drive_dir.get_child("export")
if not export_dir.exists():
    export_dir.mkdir()
DriveSample.export_files(local_drive, export_dir, threads)

DriveSample.close_drive(local_drive)
