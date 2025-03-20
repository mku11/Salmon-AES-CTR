#!/usr/bin/env python3
import os
import sys

from salmon_fs.fs.file.ifile import IFile
from salmon_fs.fs.file.file import File
from salmon_fs.fs.file.http_file import HttpFile
from salmon_fs.fs.file.ws_file import WSFile
from salmon_fs.salmonfs.drive.drive import Drive
from salmon_fs.salmonfs.drive.http_drive import HttpDrive
from salmon_fs.salmonfs.drive.ws_drive import WSDrive
from salmon_fs.salmonfs.drive.aes_drive import AesDrive
from salmon_fs.salmonfs.file.aes_file import AesFile
from salmon_fs.salmonfs.sequence.file_sequencer import FileSequencer
from salmon_core.salmon.sequence.sequence_serializer import SequenceSerializer
from salmon_fs.salmonfs.streams.aes_file_input_stream import AesFileInputStream
from salmon_fs.salmonfs.drive.utils.aes_file_commander import AesFileCommander


class DriveSample:
    @staticmethod
    def create_drive(vault_dir, password) -> AesDrive | None:
        # create a drive
        drive: AesDrive | None = None
        if type(vault_dir) == File:  # local
            drive = Drive.create(vault_dir, password, sequencer)
        elif type(vault_dir) == WSFile:  # web service
            drive = WSDrive.create(vault_dir, password, sequencer)

        print("drive created: " + drive.get_real_root().get_display_path())
        return drive

    @staticmethod
    def open_drive(vault_dir, password) -> AesDrive | None:
        # open a drive
        drive: AesDrive | None = None
        if type(vault_dir) == File:  # local
            drive = Drive.open(vault_dir, password, sequencer)
        elif type(vault_dir) == WSFile:  # web service
            drive = WSDrive.open(vault_dir, password, sequencer)
        elif type(vault_dir) == HttpFile:  # http (Read-only)
            drive = HttpDrive.open(vault_dir, password)
        print("drive opened: " + drive.get_real_root().get_display_path())
        return drive

    @staticmethod
    def import_files(drive, files_to_import, threads=1):
        buffer_size = 256 * 1024

        commander = AesFileCommander(buffer_size, buffer_size, threads, True)

        def on_progress(task_progress: AesFileCommander.RealFileTaskProgress):
            print("file importing: " \
                + str(task_progress.get_file().get_name()) + ": " \
                + str(task_progress.get_processed_bytes()) + "/" \
                + str(task_progress.get_total_bytes()) + " bytes")

        def on_fail(file: IFile, ex: Exception):
            print("import failed: " + str(ex), file=sys.stderr)

        # import multiple files
        import_options = AesFileCommander.BatchImportOptions()
        import_options.integrity = True
        import_options.autorename = IFile.auto_rename_file
        import_options.on_failed = on_fail
        import_options.on_progress_changed = on_progress
        files_imported = commander.import_files(files_to_import, drive.get_root(), import_options)

        print("Files imported")

        # close the file commander
        commander.close()

    @staticmethod
    def export_files(drive, v_dir, threads=1):
        buffer_size = 256 * 1024
        commander = AesFileCommander(buffer_size, buffer_size, threads, True)

        def on_progress(task_progress: AesFileCommander.AesFileTaskProgress):
            print("file exporting: " \
                + str(task_progress.get_file().get_name()) + ": " \
                + str(task_progress.get_processed_bytes()) + "/" \
                + str(task_progress.get_total_bytes()) + " bytes")

        def on_fail(file: AesFile, ex: Exception):
            print("export failed: " + str(ex), file=sys.stderr)

        # export all files
        files: list[AesFile] = drive.get_root().list_files()
        export_options = AesFileCommander.BatchExportOptions()
        export_options.integrity = True
        export_options.autorename = IFile.auto_rename_file
        export_options.on_failed = on_fail
        export_options.on_progress_changed = on_progress
        files_exported = commander.export_files(files, v_dir, export_options)
        print("Files exported")

        # close the file commander
        commander.close()

    @staticmethod
    def list_files(drive):
        # query for the file from the drive
        root: AesFile = drive.get_root()
        files: list[AesFile] = root.list_files()
        print("directory listing:")
        if len(files) == 0:
            print("no files found")
            return

        for file in files:
            print("file: " + file.get_name() + ", size: " + str(file.get_length()))

        # to read you can use file.get_input_stream() to get a low level RandomAccessStream
        # or use AesFileInputStream which is a Python native BufferedIOBase wrapper with caching, see below:
        file: AesFile = files[0]  # pick the first file
        print("reading file: " + file.get_name())
        buffers = 4
        buffer_size = 4 * 1024 * 1024
        buffer_threads = 1
        back_offset = 256 * 1024  # optional, use for Media consumption
        input_stream = AesFileInputStream(file, buffers, buffer_size, buffer_threads, back_offset)
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
        private_dir: IFile = File(os.environ['LOCALAPPDATA'])

        sequencer_dir = private_dir.get_child("sequencer")
        if not sequencer_dir.exists():
            sequencer_dir.mkdir()
        sequence_file = sequencer_dir.get_child(seq_filename)
        file_sequencer = FileSequencer(sequence_file, SequenceSerializer())
        return file_sequencer


# create a file sequencer:
sequencer = DriveSample.create_sequencer()
