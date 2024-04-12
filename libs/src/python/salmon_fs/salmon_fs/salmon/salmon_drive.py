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

from abc import ABC

from typeguard import typechecked
from typing import Type
from salmon_core.convert.bit_converter import BitConverter
from salmon_fs.file.ireal_file import IRealFile
from salmon_fs.drive.virtual_drive import VirtualDrive
from salmon_fs.file.ivirtual_file import IVirtualFile
from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.integrity.hmac_sha256_provider import HmacSHA256Provider
from salmon_core.integrity.ihash_provider import IHashProvider
from salmon_core.salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.streams.salmon_stream import SalmonStream
from salmon_core.salmon.password.salmon_password import SalmonPassword
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_core.salmon.salmon_security_exception import SalmonSecurityException
from salmon_fs.salmon.salmon_auth_exception import SalmonAuthException
from salmon_fs.salmon.salmon_drive_config import SalmonDriveConfig
from salmon_fs.salmon.salmon_drive_generator import SalmonDriveGenerator
from salmon_fs.salmon.salmon_key import SalmonKey
from salmon_fs.sequence.inonce_sequencer import INonceSequencer
from salmon_fs.sequence.nonce_sequence import NonceSequence


@typechecked
class SalmonDrive(VirtualDrive, ABC):
    """
    Class provides an abstract virtual drive that can be extended for use with
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
        """
        Create a virtual drive
        """
        super().__init__()
        self.__defaultFileChunkSize: int = SalmonDrive.__DEFAULT_FILE_CHUNK_SIZE
        self.__key: SalmonKey | None = None
        self.__driveID: bytearray | None = None
        self.__realRoot: IRealFile | None = None
        self.__virtualRoot: IVirtualFile | None = None
        self.__hashProvider: IHashProvider = HmacSHA256Provider()
        self.__sequencer: INonceSequencer | None = None

    def initialize(self, real_root: IRealFile, create_if_not_exists: bool):
        """
        Initialize a virtual drive at the directory path provided
        
        :param real_root: The directory for the drive
        :param create_if_not_exists: Create the drive if it does not exist
        """

        self.close()
        if real_root is None:
            return
        self.__realRoot = real_root
        if not create_if_not_exists and not self.has_config() \
                and self.__realRoot.get_parent() is not None and self.__realRoot.get_parent().exists():
            # try the parent if this is the filesystem folder
            original_real_root: IRealFile = self.__realRoot
            self.__realRoot = self.__realRoot.get_parent()
            if not self.has_config():
                # revert to original
                self.__realRoot = original_real_root
        if self.__realRoot is None:
            raise Exception("Could not initialize root folder")

        virtual_root_real_file: IRealFile | None = self.__realRoot.get_child(SalmonDrive.__virtualDriveDirectoryName)
        if create_if_not_exists and (virtual_root_real_file is None or not virtual_root_real_file.exists()):
            virtual_root_real_file = self.__realRoot.create_directory(SalmonDrive.__virtualDriveDirectoryName)

        if virtual_root_real_file is None:
            raise Exception("Could not create directory for the virtual file system")

        self.__virtualRoot = self.get_file(virtual_root_real_file)
        self.__register_on_process_close()
        self.__key = SalmonKey()

    @staticmethod
    def get_config_filename() -> str:
        return SalmonDrive.__configFilename

    @staticmethod
    def set_config_filename(config_filename: str):
        SalmonDrive.__configFilename = config_filename

    @staticmethod
    def get_auth_config_filename() -> str:
        return SalmonDrive.__authConfigFilename

    @staticmethod
    def set_auth_config_filename(auth_config_filename: str):
        SalmonDrive.__authConfigFilename = auth_config_filename

    @staticmethod
    def get_virtual_drive_directory_name() -> str:
        return SalmonDrive.__virtualDriveDirectoryName

    @staticmethod
    def set_virtual_drive_directory_name(virtual_drive_directory_name: str):
        SalmonDrive.__virtualDriveDirectoryName = virtual_drive_directory_name

    @staticmethod
    def get_export_directory_name() -> str:
        return SalmonDrive.__exportDirectoryName

    @staticmethod
    def set_export_directory_name(export_directory_name: str):
        SalmonDrive.__exportDirectoryName = export_directory_name

    @staticmethod
    def get_share_directory_name() -> str:
        return SalmonDrive.__shareDirectoryName

    @staticmethod
    def set_share_directory_name(share_directory_name: str):
        SalmonDrive.__shareDirectoryName = share_directory_name

    def __register_on_process_close(self):
        """
       Clear sensitive information when app is close.
        """
        # TODO:
        # Runtime.getRuntime().addShutdownHook(new Thread(this::close))
        pass

    def get_default_file_chunk_size(self) -> int:
        """
        Return the default file chunk size
        :return: The default chunk size.
        """
        return self.__defaultFileChunkSize

    def set_default_file_chunk_size(self, file_chunk_size: int):
        """
        Set the default file chunk size to be used with hash integrity.
        :param file_chunk_size:         """
        self.__defaultFileChunkSize = file_chunk_size

    def get_key(self) -> SalmonKey | None:
        """
        Return the encryption key that is used for encryption / decryption
        :return: The key
        """
        return self.__key

    def get_root(self) -> IVirtualFile | None:
        """
        Return the virtual root directory of the drive.
        :return: The drive root directory
        :raises SalmonAuthException: Thrown when there is a failure in the nonce sequencer.
        """
        if self.__realRoot is None or not self.__realRoot.exists():
            return None
        if not self.is_unlocked():
            raise SalmonAuthException("Not authorized")
        return self.__virtualRoot

    def get_real_root(self) -> IRealFile:
        return self.__realRoot

    def __unlock(self, password: str):
        """
        Verify if the user password is correct otherwise it throws a SalmonAuthException
        
        :param password: The password.
        """
        stream: SalmonStream | None = None
        try:
            if password is None:
                raise SalmonSecurityException("Password is missing")

            salmon_config: SalmonDriveConfig = self.__get_drive_config()
            iterations: int = salmon_config.get_iterations()
            salt: bytearray = salmon_config.get_salt()

            # derive the master key from the text password
            master_key: bytearray = SalmonPassword.get_master_key(password, salt, iterations,
                                                                  SalmonDriveGenerator.MASTER_KEY_LENGTH)

            # get the master Key Iv
            master_key_iv: bytearray = salmon_config.get_iv()

            # get the encrypted combined key and drive id
            enc_data: bytearray = salmon_config.get_encrypted_data()

            # decrypt the combined key (drive key + hash key) using the master key
            ms: MemoryStream = MemoryStream(enc_data)
            stream = SalmonStream(master_key, master_key_iv, EncryptionMode.Decrypt, ms,
                                  None, False, None, None)

            drive_key: bytearray = bytearray(SalmonGenerator.KEY_LENGTH)
            stream.read(drive_key, 0, len(drive_key))

            hash_key: bytearray = bytearray(SalmonGenerator.HASH_KEY_LENGTH)
            stream.read(hash_key, 0, len(hash_key))

            drive_id: bytearray = bytearray(SalmonDriveGenerator.DRIVE_ID_LENGTH)
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
            if stream is not None:
                stream.close()

    def set_key(self, master_key: bytearray, drive_key: bytearray, hash_key: bytearray, iterations: int):
        """
        Sets the key properties.
        :param master_key: The master key.
        :param drive_key: The drive key used for enc/dec of files and filenames.
        :param hash_key: The hash key used for data integrity.
        :param iterations:         """
        self.__key.set_master_key(master_key)
        self.__key.set_drive_key(drive_key)
        self.__key.set_hash_key(hash_key)
        self.__key.set_iterations(iterations)

    def __verify_hash(self, salmon_config: SalmonDriveConfig, data: bytearray, hash_key: bytearray):
        """
        Verify that the hash signature is correct
        
        :param salmon_config:         :param data:         :param hash_key:         """
        hash_signature: bytearray = salmon_config.get_hash_signature()
        v_hash: bytearray = SalmonIntegrity.calculate_hash(self.__hashProvider, data, 0, len(data), hash_key, None)
        for i in range(0, len(hash_key)):
            if hash_signature[i] != v_hash[i]:
                raise SalmonAuthException("Wrong password")

    def get_next_nonce(self) -> bytearray:
        """
        Get the next nonce from the sequencer. This advanced the sequencer so unique nonce are used.
        :return: The next nonce
        :raises Exception:         """
        if not self.is_unlocked():
            raise SalmonAuthException("Not authorized")
        return self.__sequencer.next_nonce(BitConverter.to_hex(self.get_drive_id()))

    def is_unlocked(self) -> bool:
        """
        Returns True if password authorization has succeeded.
        """
        key: SalmonKey = self.get_key()
        if key is None:
            return False
        enc_key: bytearray | None = key.get_drive_key()
        return enc_key is not None

    def get_bytes_from_real_file(self, file: IRealFile, buffer_size: int) -> bytearray:
        """
        Get the byte contents of a file from the real filesystem.
        
        :param file: The file
        :param buffer_size: The buffer to be used when reading
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

    def get_drive_config_file(self) -> IRealFile | None:
        """
        Return the drive configuration file.
        """
        if self.__realRoot is None or not self.__realRoot.exists():
            return None
        file: IRealFile = self.__realRoot.get_child(SalmonDrive.__configFilename)
        return file

    def get_export_dir(self) -> IRealFile:
        """
        Return the default external export dir that all file can be exported to.
        :return: The file on the real filesystem.
        """
        export_dir: IRealFile = self.__realRoot.get_child(SalmonDrive.__exportDirectoryName)
        if export_dir is None or not export_dir.exists():
            export_dir = self.__realRoot.create_directory(SalmonDrive.__exportDirectoryName)
        return export_dir

    def __get_drive_config(self) -> SalmonDriveConfig | None:
        """
        Return the configuration properties of this drive.
        """
        config_file: IRealFile = self.get_drive_config_file()
        if config_file is None or not config_file.exists():
            return None
        v_bytes: bytearray = self.get_bytes_from_real_file(config_file, 0)
        drive_config: SalmonDriveConfig = SalmonDriveConfig(v_bytes)
        return drive_config

    def has_config(self) -> bool:
        """
        Return True if the drive is already created and has a configuration file.
        """
        salmon_config: SalmonDriveConfig | None = None
        try:
            salmon_config = self.__get_drive_config()
        except Exception as ex:
            print(ex)
            return False

        return salmon_config is not None

    def get_drive_id(self) -> bytearray:
        """
        Get the drive ID.
        :return: The drive id
        """
        return self.__driveID

    def close(self):
        """
        Close the drive and close associated resources.
        """
        self.__realRoot = None
        self.__virtualRoot = None
        self.__driveID = None
        if self.__key is not None:
            self.__key.clear()
        self.__key = None

    def init_fs(self):
        """
        Initialize the drive virtual filesystem.
        """
        virtual_root_real_file: IRealFile = self.get_real_root().get_child(
            SalmonDrive.get_virtual_drive_directory_name())
        if virtual_root_real_file is None or not virtual_root_real_file.exists():
            try:
                virtual_root_real_file = self.get_real_root().create_directory(
                    SalmonDrive.get_virtual_drive_directory_name())
            except Exception as ex:
                print(ex)

        self.__virtualRoot = self.get_file(virtual_root_real_file)

    def get_hash_provider(self):
        return self.__hashProvider

    @staticmethod
    def open_drive(v_dir: IRealFile, drive_class_type: Type, password: str,
                   sequencer: INonceSequencer | None = None) -> SalmonDrive:
        """
        Set the drive location to an external directory.
        This requires you previously use SetDriveClass() to provide a class for the drive
        
        :param v_dir: The directory path that will be used for storing the contents of the drive
        :param drive_class_type: The drive class type (ie: PyDrive)
        :param password: The password
        :param sequencer: The sequencer
        """
        drive: SalmonDrive = SalmonDrive.__create_drive_instance(v_dir, False, drive_class_type, sequencer)
        if not drive.has_config():
            Exception("Drive does not exist")
        drive.__unlock(password)
        return drive

    @staticmethod
    def create_drive(v_dir: IRealFile, drive_class_type: Type, password: str,
                     sequencer: INonceSequencer) -> SalmonDrive:
        """
        Create a new drive in the provided location.
        
        :param v_dir:  Directory to store the drive configuration and virtual filesystem.
        :param password: Master password to encrypt the drive configuration.
        :param drive_class_type: The drive class type (ie: PyDrive)
        :param sequencer: The sequencer
        :return: The newly created drive.
        :raises IntegrityException: Thrown when data are corrupt or tampered with.
        :raises SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        drive: SalmonDrive = SalmonDrive.__create_drive_instance(v_dir, True, drive_class_type, sequencer)
        if drive.has_config():
            raise SalmonSecurityException("Drive already exists")
        drive.set_password(password)
        return drive

    @staticmethod
    def __create_drive_instance(v_dir: IRealFile, create_if_not_exists: bool,
                                drive_class_type: Type, sequencer: INonceSequencer | None = None) -> SalmonDrive:
        """
        Create a drive instance.
        
        :param v_dir: The target directory where the drive is located.
        :param create_if_not_exists: Create the drive if it does not exist
        :return: The drive created
        :raises IntegrityException: Thrown when security error
        """
        drive: SalmonDrive | None = None
        try:
            drive = drive_class_type()
            drive.initialize(v_dir, create_if_not_exists)
            drive.__sequencer = sequencer
            pass
        except Exception as e:
            raise SalmonSecurityException("Could not create drive instance") from e

        return drive

    def get_auth_id_bytes(self) -> bytearray:
        """
        Get the device authorization byte array for the current drive.
        
        :return: The auth id
        :raises Exception:         """
        drv_str: str = BitConverter.to_hex(self.get_drive_id())
        sequence: NonceSequence | None = self.__sequencer.get_sequence(drv_str)
        if sequence is None:
            auth_id: bytearray = SalmonDriveGenerator.generate_auth_id()
            self.create_sequence(self.get_drive_id(), auth_id)

        sequence = self.__sequencer.get_sequence(drv_str)
        return BitConverter.hex_to_bytes(sequence.get_auth_id())

    @staticmethod
    def get_default_auth_config_filename() -> str:
        """
        Get the default auth config filename.
        
        :return: The default auth config file name
        """
        return SalmonDrive.get_auth_config_filename()

    def create_sequence(self, drive_id: bytearray, auth_id: bytearray):
        """
        Create a nonce sequence for the drive id and the authorization id provided. Should be called
        once per drive_id/auth_id combination.
        
        :param drive_id: The drive_id
        :param auth_id:  The auth_id
        :raises Exception:         """
        drv_str: str = BitConverter.to_hex(drive_id)
        auth_str: str = BitConverter.to_hex(auth_id)
        self.__sequencer.create_sequence(drv_str, auth_str)

    def init_sequence(self, drive_id: bytearray, auth_id: bytearray):
        """
        Initialize the nonce sequencer with the current drive nonce range. Should be called
        once per drive_id/auth_id combination.
        
        :param drive_id: Drive ID.
        :param auth_id:  authorization ID.
        :raises Exception:         """
        starting_nonce: bytearray = SalmonDriveGenerator.get_starting_nonce()
        max_nonce: bytearray = SalmonDriveGenerator.get_max_nonce()
        drv_str: str = BitConverter.to_hex(drive_id)
        auth_str: str = BitConverter.to_hex(auth_id)
        self.__sequencer.init_sequence(drv_str, auth_str, starting_nonce, max_nonce)

    def revoke_authorization(self):
        """
        Revoke authorization for this device. This will effectively terminate write operations on the current disk
        by the current device. Warning: If you need to authorize write operations to the device again you will need
        to have another device to export an authorization config file and reimport it.
        
        :raises Exception:         @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
        """
        drive_id: bytearray = self.get_drive_id()
        self.__sequencer.revoke_sequence(BitConverter.to_hex(drive_id))

    def get_auth_id(self) -> str:
        """
        Get the authorization ID for the current device.
        
        :return: The authorization id
        :raises SequenceException: Thrown when there is a failure in the nonce sequencer.
        :raises SalmonAuthException: Thrown when there is a failure in the nonce sequencer.
        """
        return BitConverter.to_hex(self.get_auth_id_bytes())

    def __create_config(self, password: str):
        """
        Create a configuration file for the drive.
        
        :param password: The new password to be saved in the configuration
                        This password will be used to derive the master key that will be used to
                        encrypt the combined key (encryption key + hash key)
        """
        drive_key: bytearray | None = self.get_key().get_drive_key()
        hash_key: bytearray | None = self.get_key().get_hash_key()

        config_file: IRealFile = self.get_real_root().get_child(SalmonDrive.get_config_filename())
        if drive_key is None and config_file is not None and config_file.exists():
            raise SalmonAuthException("Not authorized")

        # delete the old config file and create a new one
        if config_file is not None and config_file.exists():
            config_file.delete()
        config_file = self.get_real_root().create_file(SalmonDrive.get_config_filename())

        magic_bytes: bytearray = SalmonGenerator.get_magic_bytes()

        version: int = SalmonGenerator.get_version()

        # if this is a new config file derive a 512-bit key that will be split to:
        # a) drive encryption key (for encrypting filenames and files)
        # b) hash key for file integrity
        new_drive: bool = False
        if drive_key is None:
            new_drive = True
            drive_key: bytearray = bytearray(SalmonGenerator.KEY_LENGTH)
            hash_key: bytearray = bytearray(SalmonGenerator.HASH_KEY_LENGTH)
            comb_key: bytearray = SalmonDriveGenerator.generate_combined_key()
            drive_key[0: SalmonGenerator.KEY_LENGTH] = comb_key[0:SalmonGenerator.KEY_LENGTH]
            length: int = SalmonGenerator.KEY_LENGTH + SalmonGenerator.HASH_KEY_LENGTH
            hash_key[0:SalmonGenerator.HASH_KEY_LENGTH] = comb_key[SalmonGenerator.KEY_LENGTH:length]
            self.__driveID = SalmonDriveGenerator.generate_drive_id()

        # Get the salt that we will use to encrypt the combined key (drive key + hash key)
        salt: bytearray = SalmonDriveGenerator.generate_salt()

        iterations: int = SalmonDriveGenerator.get_iterations()

        # generate a 128 bit IV that will be used with the master key
        # to encrypt the combined 64-bit key (drive key + hash key)
        master_key_iv: bytearray = SalmonDriveGenerator.generate_master_key_iv()

        # create a key that will encrypt both the (drive key and the hash key)
        master_key: bytearray = SalmonPassword.get_master_key(password, salt, iterations,
                                                              SalmonDriveGenerator.MASTER_KEY_LENGTH)

        # encrypt the combined key (drive key + hash key) using the master_key and the masterKeyIv
        ms: MemoryStream = MemoryStream()
        stream: SalmonStream = SalmonStream(master_key, master_key_iv, EncryptionMode.Encrypt, ms,
                                            None, False, None, None)
        stream.write(drive_key, 0, len(drive_key))
        stream.write(hash_key, 0, len(hash_key))
        stream.write(self.get_drive_id(), 0, len(self.get_drive_id()))
        stream.flush()
        stream.close()
        enc_data: bytearray = ms.to_array()

        # generate the hash signature
        hash_signature: bytearray = SalmonIntegrity.calculate_hash(self.get_hash_provider(), enc_data, 0,
                                                                   len(enc_data),
                                                                   hash_key, None)

        SalmonDriveConfig.write_drive_config(config_file, magic_bytes, version, salt, iterations, master_key_iv,
                                             enc_data, hash_signature)
        self.set_key(master_key, drive_key, hash_key, iterations)

        if new_drive:
            # create a full sequence for nonces
            auth_id: bytearray = SalmonDriveGenerator.generate_auth_id()
            self.create_sequence(self.get_drive_id(), auth_id)
            self.init_sequence(self.get_drive_id(), auth_id)

        self.init_fs()

    def set_password(self, password: str):
        """
        Change the user password.
        :param password: The new password.
        :raises IOError: Thrown if there is an IO error.
        :raises SalmonAuthException: Thrown when there is a failure in the nonce sequencer.
        :raises IntegrityException: Thrown when security error
        :raises IntegrityException: Thrown when data are corrupt or tampered with.
        :raises SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        self.__create_config(password)

    def get_sequencer(self) -> INonceSequencer | None:
        """
        Get the sequencer
        :return: The nonce sequencer
        """
        return self.__sequencer
        
    def get_sequencer(self, sequencer: INonceSequencer | None):
        """
        Set the sequencer
        :param sequencer: The nonce sequencer
        """
        self.__sequencer = sequencer
