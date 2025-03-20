#!/usr/bin/env python3
"""!@brief Abstract virtual drive
"""

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

from abc import ABC
import sys
from typeguard import typechecked
from typing import Type

from salmon_core.convert.bit_converter import BitConverter
from salmon_fs.fs.file.ifile import IFile
from salmon_fs.fs.drive.virtual_drive import VirtualDrive
from salmon_fs.fs.file.ivirtual_file import IVirtualFile
from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon.integrity.hmac_sha256_provider import HmacSHA256Provider
from salmon.integrity.ihash_provider import IHashProvider
from salmon_core.salmon.integrity.integrity import Integrity
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.streams.encryption_format import EncryptionFormat
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.password.password import Password
from salmon_core.salmon.generator import Generator
from salmon_core.salmon.security_exception import SecurityException
from salmon_fs.salmonfs.auth.auth_exception import AuthException
from salmon_fs.salmonfs.drive.drive_config import DriveConfig
from salmon_fs.salmonfs.drive.drive_generator import DriveGenerator
from salmon_fs.salmonfs.drive.drive_key import DriveKey
from salmon.sequence.inonce_sequencer import INonceSequencer
from salmon.sequence.nonce_sequence import NonceSequence


@typechecked
class AesDrive(VirtualDrive, ABC):
    """!
    Abstract virtual drive that can be extended for use with
    any filesystem ie disk, net, cloud, etc.
    Drive implementations needs to be realized together with {@link IRealFile}.
    """

    __DEFAULT_FILE_CHUNK_SIZE: int = 256 * 1024

    __configFilename: str = "vault.slmn"
    __authConfigFilename: str = "auth.slma"
    __virtualDriveDirectoryName: str = "fs"
    __shareDirectoryName: str = "share"
    __exportDirectoryName: str = "export"

    def __init__(self):
        """!
        Create a virtual drive
        """
        super().__init__()
        self.__defaultFileChunkSize: int = AesDrive.__DEFAULT_FILE_CHUNK_SIZE
        self.__key: DriveKey | None = None
        self.__driveID: bytearray | None = None
        self.__realRoot: IFile | None = None
        self.__virtualRoot: IVirtualFile | None = None
        self.__hashProvider: IHashProvider = HmacSHA256Provider()
        self.__sequencer: INonceSequencer | None = None

    def initialize(self, real_root: IFile, create_if_not_exists: bool):
        """!
        Initialize a virtual drive at the directory path provided
        
        @param real_root: The directory for the drive
        @param create_if_not_exists: Create the drive if it does not exist
        """

        self.close()
        if real_root is None:
            return
        self.__realRoot = real_root
        if not create_if_not_exists and not self.has_config() \
                and self.__realRoot.get_parent() and self.__realRoot.get_parent().exists():
            # try the parent if this is the filesystem folder
            original_real_root: IFile = self.__realRoot
            self.__realRoot = self.__realRoot.get_parent()
            if not self.has_config():
                # revert to original
                self.__realRoot = original_real_root
        if self.__realRoot is None:
            raise Exception("Could not initialize root folder")

        virtual_root_real_file: IFile | None = self.__realRoot.get_child(AesDrive.__virtualDriveDirectoryName)
        if create_if_not_exists and (virtual_root_real_file is None or not virtual_root_real_file.exists()):
            virtual_root_real_file = self.__realRoot.create_directory(AesDrive.__virtualDriveDirectoryName)

        if virtual_root_real_file is None:
            raise Exception("Could not create directory for the virtual file system")

        self.__virtualRoot = self.get_virtual_file(virtual_root_real_file)
        self.__register_on_process_close()
        self.__key = DriveKey()

    @staticmethod
    def get_config_filename() -> str:
        """!
        Get the default configuration file name
        @returns The file name
        """
        return AesDrive.__configFilename

    @staticmethod
    def set_config_filename(config_filename: str):
        """!
        Set the default configuraation file name
        @param config_filename: The configuration file name
        """
        AesDrive.__configFilename = config_filename

    @staticmethod
    def get_auth_config_filename() -> str:
        """!
        Get the authentication configuration file name
        @returns The file name
        """
        return AesDrive.__authConfigFilename

    @staticmethod
    def set_auth_config_filename(auth_config_filename: str):
        """!
        Set the authentication configuration file name
        @param auth_config_filename The file name
        """
        AesDrive.__authConfigFilename = auth_config_filename

    @staticmethod
    def get_virtual_drive_directory_name() -> str:
        """!
        Get the default virtual drive directory name
        @returns The directory name
        """
        return AesDrive.__virtualDriveDirectoryName

    @staticmethod
    def set_virtual_drive_directory_name(virtual_drive_directory_name: str):
        """!
        Set the default virtual drive directory name
        @param virtual_drive_directory_name The directory name
        """
        AesDrive.__virtualDriveDirectoryName = virtual_drive_directory_name

    @staticmethod
    def get_export_directory_name() -> str:
        """!
        Get the default export directory name
        @returns The directory name
        """
        return AesDrive.__exportDirectoryName

    @staticmethod
    def set_export_directory_name(export_directory_name: str):
        """!
        Set the default export directory name
        @param export_directory_name The directory name
        """
        AesDrive.__exportDirectoryName = export_directory_name

    @staticmethod
    def get_share_directory_name() -> str:
        """!
        Get the default share directory name
        @returns The directory name
        """
        return AesDrive.__shareDirectoryName

    @staticmethod
    def set_share_directory_name(share_directory_name: str):
        """!
        Set the default share directory name
        @param share_directory_name The directory name
        """
        AesDrive.__shareDirectoryName = share_directory_name

    def __register_on_process_close(self):
        """!
        Clear sensitive information when app is close.
        """
        # TODO:
        # Runtime.getRuntime().addShutdownHook(new Thread(this::close))
        pass

    def get_default_file_chunk_size(self) -> int:
        """!
        Return the default file chunk size
        @returns The default chunk size.
        """
        return self.__defaultFileChunkSize

    def set_default_file_chunk_size(self, file_chunk_size: int):
        """!
        Set the default file chunk size to be used with hash integrity.
        @param file_chunk_size:         """
        self.__defaultFileChunkSize = file_chunk_size

    def get_key(self) -> DriveKey | None:
        """!
        Return the encryption key that is used for encryption / decryption
        @returns The key
        """
        return self.__key

    def get_root(self) -> IVirtualFile | None:
        """!
        Return the virtual root directory of the drive.
        @returns The drive root directory
        @exception AuthException: Thrown when there is a failure in the nonce sequencer.
        """
        if self.__realRoot is None or not self.__realRoot.exists():
            return None
        if not self.is_unlocked():
            raise AuthException("Not authorized")
        return self.__virtualRoot

    def get_real_root(self) -> IFile:
        """!
        Get the real root
        @returns The real root
        """
        return self.__realRoot

    def __unlock(self, password: str):
        """!
        Verify if the user password is correct otherwise it throws a AuthException
        
        @param password: The password.
        """
        stream: AesStream | None = None
        try:
            if password is None:
                raise SecurityException("Password is missing")

            salmon_config: DriveConfig = self.__get_drive_config()
            iterations: int = salmon_config.get_iterations()
            salt: bytearray = salmon_config.get_salt()

            # derive the master key from the text password
            master_key: bytearray = Password.get_master_key(password, salt, iterations,
                                                            DriveGenerator.MASTER_KEY_LENGTH)

            # get the master Key Iv
            master_key_iv: bytearray = salmon_config.get_iv()

            # get the encrypted combined key and drive id
            enc_data: bytearray = salmon_config.get_encrypted_data()

            # decrypt the combined key (drive key + hash key) using the master key
            ms: MemoryStream = MemoryStream(enc_data)
            stream = AesStream(master_key, master_key_iv, EncryptionMode.Decrypt, ms, EncryptionFormat.Generic)

            drive_key: bytearray = bytearray(Generator.KEY_LENGTH)
            stream.read(drive_key, 0, len(drive_key))

            hash_key: bytearray = bytearray(Generator.HASH_KEY_LENGTH)
            stream.read(hash_key, 0, len(hash_key))

            drive_id: bytearray = bytearray(DriveGenerator.DRIVE_ID_LENGTH)
            stream.read(drive_id, 0, len(drive_id))

            # to make sure we have the right key we get the hash portion
            # and try to verify the drive nonce
            self.__verify_hash(salmon_config, enc_data, hash_key)

            # set the combined key (drive key + hash key) and the drive nonce
            self.set_key(master_key, drive_key, hash_key, iterations)
            self.__driveID = drive_id
            self.init_fs()
            self._on_unlock_success()
        except Exception as ex:
            self._on_unlock_error()
            raise ex
        finally:
            if stream:
                stream.close()

    def set_key(self, master_key: bytearray, drive_key: bytearray, hash_key: bytearray, iterations: int):
        """!
        Sets the key properties.
        @param master_key: The master key.
        @param drive_key: The drive key used for enc/dec of files and filenames.
        @param hash_key: The hash key used for data integrity.
        @param iterations:         """
        self.__key.set_master_key(master_key)
        self.__key.set_drive_key(drive_key)
        self.__key.set_hash_key(hash_key)
        self.__key.set_iterations(iterations)

    def __verify_hash(self, salmon_config: DriveConfig, data: bytearray, hash_key: bytearray):
        """!
        Verify that the hash signature is correct
        
        @param salmon_config:         @param data:         @param hash_key:         """
        hash_signature: bytearray = salmon_config.get_hash_signature()
        v_hash: bytearray = Integrity.calculate_hash(self.__hashProvider, data, 0, len(data), hash_key, None)
        for i in range(0, len(hash_key)):
            if hash_signature[i] != v_hash[i]:
                raise AuthException("Wrong password")

    def get_next_nonce(self) -> bytearray:
        """!
        Get the next nonce from the sequencer. This advanced the sequencer so unique nonce are used.
        @returns The next nonce
        @exception Exception:         """
        if not self.is_unlocked():
            raise AuthException("Not authorized")
        return self.__sequencer.next_nonce(BitConverter.to_hex(self.get_drive_id()))

    def is_unlocked(self) -> bool:
        """!
        Returns True if password authorization has succeeded.
        """
        key: DriveKey = self.get_key()
        if key is None:
            return False
        enc_key: bytearray | None = key.get_drive_key()
        return enc_key is not None

    def get_bytes_from_real_file(self, file: IFile, buffer_size: int) -> bytearray:
        """!
        Get the byte contents of a file from the real filesystem.
        
        @param file: The file
        @param buffer_size: The buffer to be used when reading
        """
        stream: RandomAccessStream = file.get_input_stream()
        ms: MemoryStream = MemoryStream()
        stream.copy_to(ms, buffer_size, None)
        ms.flush()
        ms.set_position(0)
        byte_contents: bytearray = ms.to_array()
        ms.close()
        stream.close()
        return byte_contents

    def get_drive_config_file(self) -> IFile | None:
        """!
        Return the drive configuration file.
        """
        if self.__realRoot is None or not self.__realRoot.exists():
            return None
        file: IFile = self.__realRoot.get_child(AesDrive.__configFilename)
        return file

    def get_export_dir(self) -> IFile:
        """!
        Return the default external export dir that all file can be exported to.
        @returns The file on the real filesystem.
        """
        export_dir: IFile = self.__realRoot.get_child(AesDrive.__exportDirectoryName)
        if export_dir is None or not export_dir.exists():
            export_dir = self.__realRoot.create_directory(AesDrive.__exportDirectoryName)
        return export_dir

    def __get_drive_config(self) -> DriveConfig | None:
        """!
        Return the configuration properties of this drive.
        """
        config_file: IFile = self.get_drive_config_file()
        if config_file is None or not config_file.exists():
            return None
        v_bytes: bytearray = self.get_bytes_from_real_file(config_file, 0)
        drive_config: DriveConfig = DriveConfig(v_bytes)
        return drive_config

    def has_config(self) -> bool:
        """!
        Return True if the drive is already created and has a configuration file.
        """
        salmon_config: DriveConfig | None = None
        try:
            salmon_config = self.__get_drive_config()
        except Exception as ex:
            print(ex, file=sys.stderr)
            return False

        return salmon_config is not None

    def get_drive_id(self) -> bytearray:
        """!
        Get the drive ID.
        @returns The drive id
        """
        return self.__driveID

    def close(self):
        """!
        Close the drive and close associated resources.
        """
        self.__realRoot = None
        self.__virtualRoot = None
        self.__driveID = None
        if self.__key:
            self.__key.clear()
        self.__key = None

    def init_fs(self):
        """!
        Initialize the drive virtual filesystem.
        """
        virtual_root_real_file: IFile = self.get_real_root().get_child(
            AesDrive.get_virtual_drive_directory_name())
        if virtual_root_real_file is None or not virtual_root_real_file.exists():
            try:
                virtual_root_real_file = self.get_real_root().create_directory(
                    AesDrive.get_virtual_drive_directory_name())
            except Exception as ex:
                print(ex, file=sys.stderr)

        self.__virtualRoot = self.get_virtual_file(virtual_root_real_file)

    def get_hash_provider(self) -> IHashProvider:
        """!
        Get the hash provider
        @returns The hash provider
        """
        return self.__hashProvider

    @staticmethod
    def open_drive(v_dir: IFile, drive_class_type: Type, password: str,
                   sequencer: INonceSequencer | None = None) -> AesDrive:
        """!
        Set the drive location to an external directory.
        This requires you previously use SetDriveClass() to provide a class for the drive
        
        @param v_dir: The directory path that will be used for storing the contents of the drive
        @param drive_class_type: The drive class type (ie: PyDrive)
        @param password: The password
        @param sequencer: The sequencer
        """
        drive: AesDrive = AesDrive.__create_drive_instance(v_dir, False, drive_class_type, sequencer)
        if not drive.has_config():
            Exception("Drive does not exist")
        drive.__unlock(password)
        return drive

    @staticmethod
    def create_drive(v_dir: IFile, drive_class_type: Type, password: str,
                     sequencer: INonceSequencer) -> AesDrive:
        """!
        Create a new drive in the provided location.
        
        @param v_dir:  Directory to store the drive configuration and virtual filesystem.
        @param password: Master password to encrypt the drive configuration.
        @param drive_class_type: The drive class type (ie: PyDrive)
        @param sequencer: The sequencer
        @returns The newly created drive.
        @exception IntegrityException: Thrown when data are corrupt or tampered with.
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        drive: AesDrive = AesDrive.__create_drive_instance(v_dir, True, drive_class_type, sequencer)
        if drive.has_config():
            raise SecurityException("Drive already exists")
        drive.set_password(password)
        return drive

    @staticmethod
    def __create_drive_instance(v_dir: IFile, create_if_not_exists: bool,
                                drive_class_type: Type, sequencer: INonceSequencer | None = None) -> AesDrive:
        """!
        Create a drive instance.
        
        @param v_dir: The target directory where the drive is located.
        @param create_if_not_exists: Create the drive if it does not exist
        @returns The drive created
        @exception IntegrityException: Thrown when security error
        """
        drive: AesDrive | None = None
        try:
            drive = drive_class_type()
            drive.initialize(v_dir, create_if_not_exists)
            drive.__sequencer = sequencer
            pass
        except Exception as e:
            raise SecurityException("Could not create drive instance") from e

        return drive

    def get_auth_id_bytes(self) -> bytearray:
        """!
        Get the device authorization byte array for the current drive.
        
        @returns The auth id
        @exception Exception:         """
        drv_str: str = BitConverter.to_hex(self.get_drive_id())
        sequence: NonceSequence | None = self.__sequencer.get_sequence(drv_str)
        if sequence is None:
            auth_id: bytearray = DriveGenerator.generate_auth_id()
            self.create_sequence(self.get_drive_id(), auth_id)

        sequence = self.__sequencer.get_sequence(drv_str)
        return BitConverter.hex_to_bytes(sequence.get_auth_id())

    @staticmethod
    def get_default_auth_config_filename() -> str:
        """!
        Get the default auth config filename.
        
        @returns The default auth config file name
        """
        return AesDrive.get_auth_config_filename()

    def create_sequence(self, drive_id: bytearray, auth_id: bytearray):
        """!
        Create a nonce sequence for the drive id and the authorization id provided. Should be called
        once per drive_id/auth_id combination.
        
        @param drive_id: The drive_id
        @param auth_id:  The auth_id
        @exception Exception:         """
        drv_str: str = BitConverter.to_hex(drive_id)
        auth_str: str = BitConverter.to_hex(auth_id)
        self.__sequencer.create_sequence(drv_str, auth_str)

    def init_sequence(self, drive_id: bytearray, auth_id: bytearray):
        """!
        Initialize the nonce sequencer with the current drive nonce range. Should be called
        once per drive_id/auth_id combination.
        
        @param drive_id: Drive ID.
        @param auth_id:  authorization ID.
        @exception Exception:         """
        starting_nonce: bytearray = DriveGenerator.get_starting_nonce()
        max_nonce: bytearray = DriveGenerator.get_max_nonce()
        drv_str: str = BitConverter.to_hex(drive_id)
        auth_str: str = BitConverter.to_hex(auth_id)
        self.__sequencer.init_sequence(drv_str, auth_str, starting_nonce, max_nonce)

    def revoke_authorization(self):
        """!
        Revoke authorization for this device. This will effectively terminate write operations on the current disk
        by the current device. Warning: If you need to authorize write operations to the device again you will need
        to have another device to export an authorization config file and reimport it.
        
        @exception Exception:         @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
        """
        drive_id: bytearray = self.get_drive_id()
        self.__sequencer.revoke_sequence(BitConverter.to_hex(drive_id))

    def get_auth_id(self) -> str:
        """!
        Get the authorization ID for the current device.
        
        @returns The authorization id
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        @exception AuthException: Thrown when there is a failure in the nonce sequencer.
        """
        return BitConverter.to_hex(self.get_auth_id_bytes())

    def __create_config(self, password: str):
        """!
        Create a configuration file for the drive.
        
        @param password: The new password to be saved in the configuration
                        This password will be used to derive the master key that will be used to
                        encrypt the combined key (encryption key + hash key)
        """
        drive_key: bytearray | None = self.get_key().get_drive_key()
        hash_key: bytearray | None = self.get_key().get_hash_key()

        config_file: IFile = self.get_config_file(self.get_real_root())
        if drive_key is None and config_file and config_file.exists():
            raise AuthException("Not authorized")

        # delete the old config file and create a new one
        if config_file and config_file.exists():
            config_file.delete()
        config_file = self.create_config_file(self.get_real_root())

        magic_bytes: bytearray = Generator.get_magic_bytes()

        version: int = Generator.get_version()

        # if this is a new config file derive a 512-bit key that will be split to:
        # a) drive encryption key (for encrypting filenames and files)
        # b) hash key for file integrity
        new_drive: bool = False
        if drive_key is None:
            new_drive = True
            drive_key: bytearray = bytearray(Generator.KEY_LENGTH)
            hash_key: bytearray = bytearray(Generator.HASH_KEY_LENGTH)
            comb_key: bytearray = DriveGenerator.generate_combined_key()
            drive_key[0: Generator.KEY_LENGTH] = comb_key[0:Generator.KEY_LENGTH]
            length: int = Generator.KEY_LENGTH + Generator.HASH_KEY_LENGTH
            hash_key[0:Generator.HASH_KEY_LENGTH] = comb_key[Generator.KEY_LENGTH:length]
            self.__driveID = DriveGenerator.generate_drive_id()

        # Get the salt that we will use to encrypt the combined key (drive key + hash key)
        salt: bytearray = DriveGenerator.generate_salt()

        iterations: int = DriveGenerator.get_iterations()

        # generate a 128 bit IV that will be used with the master key
        # to encrypt the combined 64-bit key (drive key + hash key)
        master_key_iv: bytearray = DriveGenerator.generate_master_key_iv()

        # create a key that will encrypt both the (drive key and the hash key)
        master_key: bytearray = Password.get_master_key(password, salt, iterations,
                                                        DriveGenerator.MASTER_KEY_LENGTH)

        # encrypt the combined key (drive key + hash key) using the master_key and the masterKeyIv
        ms: MemoryStream = MemoryStream()
        stream: AesStream = AesStream(master_key, master_key_iv, EncryptionMode.Encrypt, ms, EncryptionFormat.Generic)
        stream.write(drive_key, 0, len(drive_key))
        stream.write(hash_key, 0, len(hash_key))
        stream.write(self.get_drive_id(), 0, len(self.get_drive_id()))
        stream.flush()
        stream.close()
        enc_data: bytearray = ms.to_array()

        # generate the hash signature
        hash_signature: bytearray = Integrity.calculate_hash(self.get_hash_provider(), enc_data, 0,
                                                             len(enc_data),
                                                             hash_key, None)

        DriveConfig.write_drive_config(config_file, magic_bytes, version, salt, iterations, master_key_iv,
                                       enc_data, hash_signature)
        self.set_key(master_key, drive_key, hash_key, iterations)

        if new_drive:
            # create a full sequence for nonces
            auth_id: bytearray = DriveGenerator.generate_auth_id()
            self.create_sequence(self.get_drive_id(), auth_id)
            self.init_sequence(self.get_drive_id(), auth_id)

        self.init_fs()

    def set_password(self, password: str):
        """!
        Change the user password.
        @param password: The new password.
        @exception IOError: Thrown if there is an IO error.
        @exception AuthException: Thrown when there is a failure in the nonce sequencer.
        @exception IntegrityException: Thrown when security error
        @exception IntegrityException: Thrown when data are corrupt or tampered with.
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        self.__create_config(password)

    def get_sequencer(self) -> INonceSequencer | None:
        """!
        Get the sequencer
        @returns The nonce sequencer
        """
        return self.__sequencer

    def set_sequencer(self, sequencer: INonceSequencer | None):
        """!
        Set the sequencer
        @param sequencer: The nonce sequencer
        """
        self.__sequencer = sequencer

    def create_config_file(self, real_root: IFile) -> IFile:
        """!
        Create the drive config file
        @param real_root: The real root directory.
        @returns The config file
        """
        config_file: IFile = real_root.create_file(AesDrive.get_config_filename())
        return config_file

    def get_config_file(self, real_root: IFile) -> IFile:
        """!
        Get the drive config file
        @param real_root: The real root directory.
        @returns The config file
        """
        config_file: IFile = real_root.get_child(AesDrive.get_config_filename())
        return config_file
