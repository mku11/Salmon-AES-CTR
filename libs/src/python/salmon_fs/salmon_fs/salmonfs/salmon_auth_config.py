#!/usr/bin/env python3
'''
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
'''
from __future__ import annotations

from typeguard import typechecked

from salmon_fs.file.ireal_file import IRealFile
from salmon_core.iostream.memory_stream import MemoryStream
from salmon_core.salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon_core.salmon.iostream.salmon_stream import SalmonStream
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_fs.salmonfs.salmon_auth_exception import SalmonAuthException
from salmon_fs.salmonfs.salmon_drive import SalmonDrive
from salmon_fs.salmonfs.salmon_drive_generator import SalmonDriveGenerator
from salmon_fs.salmonfs.salmon_file import SalmonFile


@typechecked
class SalmonAuthConfig:
    """
     * Device Authorization Configuration. This represents the authorization that will be provided
     * to the target device to allow writing operations for a virtual drive.
    """

    def get_drive_id(self) -> bytearray:
        """
         * Get the drive ID to grant authorization for.
         * @return
        """
        return self.__driveID

    def get_auth_id(self) -> bytearray:
        """
         * Get the authorization ID for the target device.
         * @return
        """
        return self.__authID

    def get_start_nonce(self) -> bytearray:
        """
         * Get the nonce maximum value the target device will use.
         * @return
        """
        return self.__startNonce

    def get_max_nonce(self) -> bytearray:
        """
         * Get the nonce maximum value the target device will use.
         * @return
        """
        return self.__maxNonce

    def __init__(self, contents: bytearray):
        """
         * Instantiate a class with the properties of the authorization config file.
         * @param contents The byte array that contains the contents of the auth config file.
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
         * Write the properties of the auth configuration to a config file that will be imported by another device.
         * The new device will then be authorized editing operations ie: import, rename files, etc.
         * @param auth_config_file
         * @param drive The drive you want to create an auth config for.
         * @param target_auth_id authorization ID of the target device.
         * @param target_starting_nonce Starting nonce for the target device.
         * @param target_max_nonce Maximum nonce for the target device.
         * @throws Exception
        """
        salmon_file: SalmonFile = SalmonFile(auth_config_file, drive)
        stream: SalmonStream = salmon_file.get_output_stream(config_nonce)
        SalmonAuthConfig.write_to_stream(stream, drive.get_drive_id(), target_auth_id, target_starting_nonce,
                                         target_max_nonce)

    @staticmethod
    def write_to_stream(stream: SalmonStream, drive_id: bytearray, auth_id: bytearray,
                        next_nonce: bytearray, max_nonce: bytearray):
        """
         * Write authorization configuration to a SalmonStream.
         * @param stream The stream to write to.
         * @param drive_id The drive id.
         * @param auth_id The auth id of the new device.
         * @param next_nonce The next nonce to be used by the new device.
         * @param max_nonce The max nonce to be used byte the new device.
         * @throws Exception
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
            print(ex)
            raise SalmonAuthException("Could not write auth config") from ex
        finally:
            ms.close()
            stream.flush()
            stream.close()
