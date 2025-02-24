#!/usr/bin/env python3
import os
import sys

from salmon_fs.file.ireal_file import IRealFile
from salmon_fs.file.py_file import PyFile
from salmon_fs.file.py_http_file import PyHttpFile
from salmon_fs.file.py_ws_file import PyWSFile
from salmon_fs.salmon.drive.py_drive import PyDrive
from salmon_fs.salmon.drive.py_http_drive import PyHttpDrive
from salmon_fs.salmon.drive.py_ws_drive import PyWSDrive
from salmon_fs.salmon.salmon_drive import SalmonDrive
from salmon_fs.salmon.salmon_file import SalmonFile
from salmon_fs.salmon.sequence.salmon_file_sequencer import SalmonFileSequencer
from salmon_fs.salmon.sequence.salmon_sequence_serializer import SalmonSequenceSerializer
from salmon_fs.salmon.streams.salmon_file_input_stream import SalmonFileInputStream
from salmon_fs.salmon.utils.salmon_file_commander import SalmonFileCommander


class DriveSample:
    @staticmethod
    def create_drive(vault_dir, password) -> SalmonDrive | None:
        # create a drive
        drive: SalmonDrive | None = None
        if type(vault_dir) == PyFile:  # local
            drive = PyDrive.create(vault_dir, password, sequencer)
        elif type(vault_dir) == PyWSFile:  # web service
            drive = PyWSDrive.create(vault_dir, password, sequencer)

        print("drive created: " + drive.get_real_root().get_absolute_path())
        return drive

    @staticmethod
    def open_drive(vault_dir, password) -> SalmonDrive | None:
        # open a drive
        drive: SalmonDrive | None = None
        if type(vault_dir) == PyFile:  # local
            drive = PyDrive.open(vault_dir, password, sequencer)
        elif type(vault_dir) == PyWSFile:  # web service
            drive = PyWSDrive.open(vault_dir, password, sequencer)
        elif type(vault_dir) == PyHttpFile:  # http (Read-only)
            drive = PyHttpDrive.open(vault_dir, password)
        print("drive opened: " + drive.get_real_root().get_absolute_path())
        return drive

    @staticmethod
    def import_files(drive, files_to_import, threads=1):
        buffer_size = 256 * 1024

        commander = SalmonFileCommander(buffer_size, buffer_size, threads, True)

        def on_progress(task_progress: SalmonFileCommander.RealFileTaskProgress):
            print("file importing: " + task_progress.get_file().get_base_name() + ": "
                  + str(task_progress.get_processed_bytes()) + "/" + str(task_progress.get_total_bytes()) + " bytes")

        def on_fail(file: IRealFile, ex: Exception):
            print("import failed: " + file.get_base_name() + "\n" + str(ex), file=sys.stderr)

        # import multiple files
        files_imported = commander.import_files(files_to_import, drive.get_root(), False, True, on_progress,
                                                IRealFile.auto_rename_file, on_fail)

        print("Files imported")

        # close the file commander
        commander.close()

    @staticmethod
    def export_files(drive, v_dir, threads=1):
        buffer_size = 256 * 1024
        commander = SalmonFileCommander(buffer_size, buffer_size, threads)

        def on_progress(task_progress: SalmonFileCommander.SalmonFileTaskProgress):
            try:
                print("file exporting: " + task_progress.get_file().get_base_name() + ": "
                      + str(task_progress.get_processed_bytes()) + "/" + str(
                    task_progress.get_total_bytes()) + " bytes")
            except Exception as e:
                print(e, file=sys.stderr)

        def on_fail(file, ex):
            print("export failed: " + file.get_base_name() + "\n" + ex, file=sys.stderr)

        # export all files
        files: list[SalmonFile] = drive.get_root().list_files()
        files_exported = commander.export_files(files, v_dir, False, True, on_progress,
                                                IRealFile.auto_rename_file, on_fail)
        print("Files exported")

        # close the file commander
        commander.close()

    @staticmethod
    def list_files(drive):
        # query for the file from the drive
        root: SalmonFile = drive.get_root()
        files: list[SalmonFile] = root.list_files()
        print("directory listing:")
        if len(files) == 0:
            print("no files found")
            return

        for file in files:
            print("file: " + file.get_base_name() + ", size: " + str(file.get_size()))

        # to read you can use file.get_input_stream() to get a low level RandomAccessStream
        # or use SalmonFileInputStream which is a Python native BufferedIOBase wrapper with caching, see below:
        file: SalmonFile = files[0]  # pick the first file
        print("reading file: " + file.get_base_name())
        buffers = 4
        buffer_size = 4 * 1024 * 1024
        buffer_threads = 1
        back_offset = 256 * 1024  # optional, use for Media consumption
        input_stream = SalmonFileInputStream(file, buffers, buffer_size, buffer_threads, back_offset)
        total_bytes_read = 0
        while buffer := input_stream.read():
            # do whatever you want with the data...
            total_bytes_read += len(buffer)
        print("bytes read: " + str(total_bytes_read))
        input_stream.close()

    @staticmethod
    def close_drive(drive):
        # close the drive
        drive.close()
        print("drive closed")

    @staticmethod
    def create_sequencer():
        # create a file nonce sequencer and place it in a private space
        # make sure you never edit or back up this file.
        seq_filename = "sequencer.xml"
        # if you use Linux/macOS use HOME
        private_dir: IRealFile = PyFile(os.environ['LOCALAPPDATA'])

        sequencer_dir = private_dir.get_child("sequencer")
        if not sequencer_dir.exists():
            sequencer_dir.mkdir()
        sequence_file = sequencer_dir.get_child(seq_filename)
        file_sequencer = SalmonFileSequencer(sequence_file, SalmonSequenceSerializer())
        return file_sequencer


# create a file sequencer:
sequencer = DriveSample.create_sequencer()
