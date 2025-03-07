#!/usr/bin/env python3
from __future__ import annotations

__license__ = """
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""

from typeguard import typechecked
import sys

from salmon_fs.file.ireal_file import IRealFile
from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon_core.salmon.streams.salmon_stream import SalmonStream
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_fs.salmon.salmon_auth_exception import SalmonAuthException
from salmon_core.salmon.salmon_security_exception import SalmonSecurityException
from salmon_fs.salmon.salmon_drive_generator import SalmonDriveGenerator
from salmon_fs.salmon.salmon_file import SalmonFile
from salmon_fs.salmon.salmon_drive import SalmonDrive
from salmon_core.convert.bit_converter import BitConverter
from salmon_fs.sequence.nonce_sequence import NonceSequence
from salmon_core.salmon.salmon_nonce import SalmonNonce
from salmon_fs.sequence.sequence_exception import SequenceException


@typechecked
class SalmonAuthConfig:
    """
    Device Authorization Configuration. This represents the authorization that will be provided
    to the target device to allow writing operations for a virtual drive.
    """

    def get_drive_id(self) -> bytearray:
        """
        Get the drive ID to grant authorization for.
        :return: The drive id
        """
        return self.__driveID

    def get_auth_id(self) -> bytearray:
        """
        Get the authorization ID for the target device.
        :return: The authorization id
        """
        return self.__authID

    def get_start_nonce(self) -> bytearray:
        """
        Get the nonce maximum value the target device will use.
        :return: The starting nonce
        """
        return self.__startNonce

    def get_max_nonce(self) -> bytearray:
        """
        Get the nonce maximum value the target device will use.
        :return: The max nonce
        """
        return self.__maxNonce

    def __init__(self, contents: bytearray):
        """
        Instantiate a class with the properties of the authorization config file.
        :param contents: The byte array that contains the contents of the auth config file.
        """

        self.__driveID: bytearray = bytearray(SalmonDriveGenerator.DRIVE_ID_LENGTH)
        self.__authID: bytearray = bytearray(SalmonDriveGenerator.AUTH_ID_SIZE)
        self.__startNonce: bytearray = bytearray(SalmonGenerator.NONCE_LENGTH)
        self.__maxNonce: bytearray = bytearray(SalmonGenerator.NONCE_LENGTH)

        ms: MemoryStream = MemoryStream(contents)
        ms.read(self.__driveID, 0, SalmonDriveGenerator.DRIVE_ID_LENGTH)
        ms.read(self.__authID, 0, SalmonDriveGenerator.AUTH_ID_SIZE)
        ms.read(self.__startNonce, 0, SalmonGenerator.NONCE_LENGTH)
        ms.read(self.__maxNonce, 0, SalmonGenerator.NONCE_LENGTH)
        ms.close()

    @staticmethod
    def write_auth_file(auth_config_file: IRealFile,
                        drive: SalmonDrive,
                        target_auth_id: bytearray,
                        target_starting_nonce: bytearray,
                        target_max_nonce: bytearray,
                        config_nonce: bytearray):
        """
        Write the properties of the auth configuration to a config file that will be imported by another device.
        The new device will then be authorized editing operations ie: import, rename files, etc.
        :param auth_config_file:
        :param drive: The drive you want to create an auth config for.
        :param target_auth_id: authorization ID of the target device.
        :param target_starting_nonce: Starting nonce for the target device.
        :param target_max_nonce: Maximum nonce for the target device.
        :param config_nonce: The nonce for the config file itself.
        :raises Exception: Thrown when error during writing file
        """
        drive_id: bytearray = drive.get_drive_id()
        if drive_id is None:
            raise Exception("Could not write auth file, no drive id found")
        salmon_file: SalmonFile = SalmonFile(auth_config_file, drive)
        stream: SalmonStream = salmon_file.get_output_stream(config_nonce)
        SalmonAuthConfig.write_to_stream(stream, drive_id, target_auth_id, target_starting_nonce,
                                         target_max_nonce)

    @staticmethod
    def write_to_stream(stream: SalmonStream, drive_id: bytearray, auth_id: bytearray,
                        next_nonce: bytearray, max_nonce: bytearray):
        """
        Write authorization configuration to a SalmonStream.
        :param stream: The stream to write to.
        :param drive_id: The drive id.
        :param auth_id: The auth id of the new device.
        :param next_nonce: The next nonce to be used by the new device.
        :param max_nonce: The max nonce to be used byte the new device.f
        :raises Exception: Thrown when error during writing to stream
        """

        ms: MemoryStream = MemoryStream()
        try:
            ms.write(drive_id, 0, len(drive_id))
            ms.write(auth_id, 0, len(auth_id))
            ms.write(next_nonce, 0, len(next_nonce))
            ms.write(max_nonce, 0, len(max_nonce))
            content: bytearray = ms.to_array()
            buffer: bytearray = bytearray(SalmonIntegrity.DEFAULT_CHUNK_SIZE)
            buffer[0: len(content)] = content[0:len(content)]
            stream.write(buffer, 0, len(content))
        except Exception as ex:
            print(ex, file=sys.stderr)
            raise SalmonAuthException("Could not write auth config") from ex
        finally:
            ms.close()
            stream.flush()
            stream.close()

    @staticmethod
    def get_auth_config(drive: SalmonDrive, auth_file: IRealFile) -> SalmonAuthConfig:
        """
        Get the app drive pair configuration properties for this drive
        :param drive: The drive
        :param auth_file: The encrypted authorization file.
        :return: The decrypted authorization file.
        :raises Exception: Thrown when error during reading file
        """
        salmon_file: SalmonFile = SalmonFile(auth_file, drive)
        stream: SalmonStream = salmon_file.get_input_stream()
        ms: MemoryStream = MemoryStream()
        stream.copy_to(ms)
        ms.close()
        stream.close()
        drive_config: SalmonAuthConfig = SalmonAuthConfig(ms.to_array())
        if not SalmonAuthConfig.__verify_auth_id(drive, drive_config.get_auth_id()):
            raise SalmonSecurityException("Could not authorize this device, the authorization id does not match")
        return drive_config

    @staticmethod
    def __verify_auth_id(drive: SalmonDrive, auth_id: bytearray) -> bool:
        """
        Verify the authorization id with the current drive auth id.
        
        :param auth_id: The authorization id to verify.
        :return: True if verifcation succeded
        :raises Exception: Thrown when verification failed
        """
        return SalmonAuthConfig.__arrays_equal(auth_id, drive.get_auth_id_bytes())

    @staticmethod
    def import_sequence(drive: SalmonDrive, auth_config: SalmonAuthConfig):
        """
        Import sequence into the current drive.
        :param drive: The drive
        :param auth_config:         :raises Exception: Thrown when error during importing sequence
        """
        drv_str: str = BitConverter.to_hex(auth_config.get_drive_id())
        auth_str: str = BitConverter.to_hex(auth_config.get_auth_id())
        drive.get_sequencer().init_sequence(drv_str, auth_str, auth_config.get_start_nonce(),
                                            auth_config.get_max_nonce())

    @staticmethod
    def import_auth_file(drive: SalmonDrive, auth_config_file: IRealFile):
        """
        Import the device authorization file.
        :param drive: The drive
        :param auth_config_file: The config file
        :raises Exception: Thrown when error during
        """
        sequence: NonceSequence = drive.get_sequencer().get_sequence(
            BitConverter.to_hex(drive.get_drive_id()))
        if sequence is not None and sequence.get_status() == NonceSequence.Status.Active:
            raise Exception("Device is already authorized")

        if auth_config_file is None or not auth_config_file.exists():
            raise Exception("Could not import file")

        auth_config: SalmonAuthConfig = SalmonAuthConfig.get_auth_config(drive, auth_config_file)

        if not SalmonAuthConfig.__arrays_equal(
                auth_config.get_auth_id(), drive.get_auth_id_bytes()) \
                or not SalmonAuthConfig.__arrays_equal(auth_config.get_drive_id(),
                                                       drive.get_drive_id()):
            raise Exception("Auth file doesn't match drive_id or auth_id")

        SalmonAuthConfig.import_sequence(drive, auth_config)

    @staticmethod
    def export_auth_file(drive: SalmonDrive, target_auth_id: str, file: IRealFile):
        """
        :param drive: The drive
        :param target_auth_id: The authorization id of the target device.
        :param file:     The auth config file.
        :raises Exception:         """
        cfg_nonce: bytearray = drive.get_sequencer().next_nonce(
            BitConverter.to_hex(drive.get_drive_id()))

        sequence: NonceSequence = drive.get_sequencer().get_sequence(
            BitConverter.to_hex(drive.get_drive_id()))
        if sequence is None:
            raise Exception("Device is not authorized to export")

        if file.exists() and file.length() > 0:
            out_stream: RandomAccessStream | None = None
            try:
                out_stream = file.getOutputStream()
                out_stream.setLength(0)
            finally:
                if out_stream is not None:
                    out_stream.close()
        max_nonce: bytearray | None = sequence.get_max_nonce()
        if max_nonce is None:
            raise SequenceException("Could not get current max nonce")
        next_nonce: bytearray | None = sequence.get_next_nonce()
        if next_nonce is None:
            raise SequenceException("Could not get next nonce")

        pivot_nonce: bytearray = SalmonNonce.split_nonce_range(sequence.get_next_nonce(), sequence.get_max_nonce())
        auth_id: str | None = sequence.get_auth_id()
        if auth_id is None:
            raise SequenceException("Could not get auth id")
        drive.get_sequencer().set_max_nonce(sequence.get_drive_id(), sequence.get_auth_id(), pivot_nonce)
        SalmonAuthConfig.write_auth_file(file, drive,
                                         BitConverter.hex_to_bytes(target_auth_id),
                                         pivot_nonce, sequence.get_max_nonce(),
                                         cfg_nonce)

    @staticmethod
    def __arrays_equal(array1: bytearray, array2: bytearray) -> bool:
        if len(array1) != len(array2):
            return False
        for i in range(0, len(array1)):
            if array1[i] != array2[i]:
                return False
        return True
