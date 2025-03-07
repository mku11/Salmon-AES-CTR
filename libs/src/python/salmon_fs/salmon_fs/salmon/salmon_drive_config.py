#!/usr/bin/env python3
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

from salmon_core.convert.bit_converter import BitConverter
from salmon_fs.file.ireal_file import IRealFile
from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_fs.salmon.salmon_drive_generator import SalmonDriveGenerator


@typechecked
class SalmonDriveConfig:
    """
    Represents a configuration file for a drive. The properties are encrypted in the file
    with a master key which is password derived.
    """

    def __init__(self, contents: bytearray):
        """
        Provide a class that hosts the properties of the drive config file
        
        :param contents: The byte array that contains the contents of the config file
        """
        self.__magicBytes: bytearray = bytearray(SalmonGenerator.MAGIC_LENGTH)
        self.__version: bytearray = bytearray(SalmonGenerator.VERSION_LENGTH)
        self.__salt: bytearray = bytearray(SalmonDriveGenerator.SALT_LENGTH)
        self.__iterations: bytearray = bytearray(SalmonDriveGenerator.ITERATIONS_LENGTH)
        self.__iv: bytearray = bytearray(SalmonDriveGenerator.IV_LENGTH)
        self.__encryptedData: bytearray = bytearray(
            SalmonDriveGenerator.COMBINED_KEY_LENGTH + SalmonDriveGenerator.DRIVE_ID_LENGTH)
        self.__hashSignature: bytearray = bytearray(SalmonGenerator.HASH_RESULT_LENGTH)

        ms: MemoryStream = MemoryStream(contents)
        ms.read(self.__magicBytes, 0, SalmonGenerator.MAGIC_LENGTH)
        ms.read(self.__version, 0, SalmonGenerator.VERSION_LENGTH)
        ms.read(self.__salt, 0, SalmonDriveGenerator.SALT_LENGTH)
        ms.read(self.__iterations, 0, SalmonDriveGenerator.ITERATIONS_LENGTH)
        ms.read(self.__iv, 0, SalmonDriveGenerator.IV_LENGTH)
        ms.read(self.__encryptedData, 0, SalmonDriveGenerator.COMBINED_KEY_LENGTH + SalmonDriveGenerator.AUTH_ID_SIZE)
        ms.read(self.__hashSignature, 0, SalmonGenerator.HASH_RESULT_LENGTH)
        ms.close()

    @staticmethod
    def write_drive_config(config_file: IRealFile, magic_bytes: bytearray, version: int, salt: bytearray,
                           iterations: int, key_iv: bytearray,
                           encrypted_data: bytearray, hash_signature: bytearray):
        """
        Write the properties of a drive to a config file
        
        :param config_file:                   The configuration file that will be used to write the content into
        :param magic_bytes:                   The magic bytes for the header
        :param version:                      The version of the file format
        :param salt:                         The salt that will be used for encryption of the combined key
        :param iterations:                   The iteration that will be used to derive the master key from a text
                                             password
        :param key_iv:                        The initial vector that was used with the master password to encrypt
                                             the combined key
        :param encrypted_data: The encrypted combined key and drive id
        :param hash_signature:                The hash signature of the drive id
        """

        # construct the contents of the config file
        ms2: MemoryStream = MemoryStream()
        ms2.write(magic_bytes, 0, len(magic_bytes))
        ms2.write(bytearray([version]), 0, 1)
        ms2.write(salt, 0, len(salt))
        ms2.write(BitConverter.to_bytes(iterations, 4), 0, 4)  # sizeof( int)
        ms2.write(key_iv, 0, len(key_iv))
        ms2.write(encrypted_data, 0, len(encrypted_data))
        ms2.write(hash_signature, 0, len(hash_signature))
        ms2.flush()
        ms2.set_position(0)

        # we write the contents to the config file
        output_stream: RandomAccessStream = config_file.get_output_stream()
        ms2.copy_to(output_stream)
        output_stream.flush()
        output_stream.close()
        ms2.close()

    def clear(self):
        """
        Clear properties.
        """
        self.__magicBytes[0:len(self.__magicBytes)] = [0] * len(self.__magicBytes)
        self.__salt[0:len(self.__salt)] = [0] * len(self.__salt)
        self.__iterations[0:len(self.__iterations)] = [0] * len(self.__iterations)
        self.__iv[0:len(self.__iv)] = [0] * len(self.__iv)
        self.__encryptedData[0:len(self.__encryptedData)] = [0] * len(self.__encryptedData)
        self.__hashSignature[0:len(self.__hashSignature)] = [0] * len(self.__hashSignature)

    def get_magic_bytes(self) -> bytearray:
        """
        Get the magic bytes from the config file.
        :return: The magic bytes
        """
        return self.__magicBytes

    def get_salt(self) -> bytearray:
        """
        Get the salt to be used for the password key derivation.
        :return: The salt
        """
        return self.__salt

    def get_iterations(self) -> int:
        """
        Get the iterations to be used for the key derivation.
        :return: The iterations
        """
        if self.__iterations is None:
            return 0
        return BitConverter.to_long(self.__iterations, 0, SalmonDriveGenerator.ITERATIONS_LENGTH)

    def get_encrypted_data(self) -> bytearray:
        """
        Get encrypted data using the master key: drive key, hash key, drive id.
        :return: The encrypted data
        """
        return self.__encryptedData

    def get_iv(self) -> bytearray:
        """
        Get the initial vector that was used to encrypt this drive configuration.
        :return: The initial vector
        """
        return self.__iv

    def get_hash_signature(self) -> bytearray:
        """
        Get the hash signature that was used to sign this drive configuration.
        :return: The hash signature
        """
        return self.__hashSignature
