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
from abc import ABC, abstractmethod
from threading import RLock

from file.ireal_file import IRealFile
from iostream.memory_stream import MemoryStream
from iostream.random_access_stream import RandomAccessStream
from salmon.integrity.hmac_sha256_provider import HmacSHA256Provider
from salmon.integrity.ihash_provider import IHashProvider
from salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon.iostream.salmon_stream import SalmonStream
from salmon.password.salmon_password import SalmonPassword
from salmon.salmon_generator import SalmonGenerator
from salmon.salmon_security_exception import SalmonSecurityException
from salmonfs.salmon_auth_exception import SalmonAuthException
from salmonfs.salmon_drive_config import SalmonDriveConfig
from salmonfs.salmon_drive_generator import SalmonDriveGenerator
from salmonfs.salmon_drive_manager import SalmonDriveManager
from salmonfs.salmon_file import SalmonFile
from salmonfs.salmon_key import SalmonKey


class SalmonDrive(ABC):
    """
     * Class provides an abstract virtual drive that can be extended for use with
     * any filesystem ie disk, net, cloud, etc.
     * Drive implementations needs to be realized together with {@link IRealFile}.
    """

    __DEFAULT_FILE_CHUNK_SIZE: int = 256 * 1024

    __configFilename: str = "vault.slmn"
    __auth_configFilename: str = "auth.slma"
    __virtualDriveDirectoryName: str = "fs"
    __shareDirectoryName: str = "share"
    __exportDirectoryName: str = "export"

    def __init__(self, real_root_path: str, create_if_not_exists: bool):
        """
         * Create a virtual drive at the directory path provided
         *
         * @param real_root_path The path of the real directory
         * @param create_if_not_exists Create the drive if it does not exist
        """
        self.__defaultFileChunkSize: int = SalmonDrive.__DEFAULT_FILE_CHUNK_SIZE
        self.__key: SalmonKey | None = None
        self.__driveID: bytearray | None = None
        self.__realRoot: IRealFile | None = None
        self.__virtualRoot: SalmonFile | None = None

        self.__hashProvider: IHashProvider = HmacSHA256Provider()

        self.__lock = RLock()

        self.close()
        if real_root_path is None:
            return
        real_root = self.get_real_file(real_root_path, True)
        if not create_if_not_exists and not self.has_config() \
                and real_root.get_parent() is not None and real_root.get_parent().exists():
            # try the parent if this is the filesystem folder
            original_real_root: IRealFile = real_root
            real_root = real_root.get_parent()
            if not self.has_config():
                # revert to original
                real_root = original_real_root

        virtual_root_real_file: IRealFile = real_root.get_child(SalmonDrive.__virtualDriveDirectoryName)
        if create_if_not_exists and (virtual_root_real_file is None or not virtual_root_real_file.exists()):
            virtual_root_real_file = real_root.create_directory(SalmonDrive.__virtualDriveDirectoryName)

        self.__virtualRoot = self._create_virtual_root(virtual_root_real_file)
        self.__register_on_process_close()
        key = SalmonKey()

    @abstractmethod
    def get_real_file(self, filepath: str, is_directory: bool) -> IRealFile:
        """
         * Get a file or directory from the current real filesystem. Used internally
         * for accessing files from the real filesystem.
         * @param filepath
         * @param is_directory True if filepath corresponds to a directory.
         * @return
        """
        pass

    @abstractmethod
    def _on_authentication_success(self):
        """
         * Method is called when the user is authenticated
        """
        pass

    def _on_authentication_error(self):
        """
         * Method is called when the user authentication has failed
        """
        pass

    @staticmethod
    def get_config_filename() -> str:
        return SalmonDrive.__configFilename

    @staticmethod
    def set_config_filename(config_filename: str):
        SalmonDrive.__configFilename = config_filename

    @staticmethod
    def get_auth_config_filename() -> str:
        return SalmonDrive.__auth_configFilename

    @staticmethod
    def set_auth_config_filename(auth_config_filename: str):
        SalmonDrive.__auth_configFilename = auth_config_filename

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
        * Clear sensitive information when app is close.
        """
        # TODO:
        # Runtime.getRuntime().addShutdownHook(new Thread(this::close))
        pass

    def get_default_file_chunk_size(self) -> int:
        """
         * Return the default file chunk size
         * @return The default chunk size.
        """
        return self.__defaultFileChunkSize

    def set_default_file_chunk_size(self, file_chunk_size: int):
        """
         * Set the default file chunk size to be used with hash integrity.
         * @param file_chunk_size
        """
        self.__defaultFileChunkSize = file_chunk_size

    def get_key(self) -> SalmonKey:
        """
         * Return the encryption key that is used for encryption / decryption
         * @return
        """
        return self.__key

    def set_password(self, password: str):
        """
         * Change the user password.
         * @param pass The new password.
         * @throws IOError
         * @throws SalmonAuthException
         * @throws SalmonSecurityException
         * @throws SalmonIntegrityException
         * @throws SalmonSequenceException
        """
        with (self.__lock):
            self.__create_config(password)

    def _init_fs(self):
        """
         * Initialize the drive virtual filesystem.
        """
        virtual_root_real_file: IRealFile = self.__realRoot.get_child(SalmonDrive.__virtualDriveDirectoryName)
        if virtual_root_real_file is None or not virtual_root_real_file.exists():
            try:
                virtual_root_real_file = self.__realRoot.create_directory(SalmonDrive.__virtualDriveDirectoryName)
            except Exception as ex:
                print(ex)

        self.__virtualRoot = self._create_virtual_root(virtual_root_real_file)

    def _create_virtual_root(self, virtual_root_real_file: IRealFile) -> SalmonFile:
        return SalmonFile(virtual_root_real_file, self)

    # TODO: partial refactor to SalmonDriveConfig
    def __create_config(self, password: str):
        """
         * Create a configuration file for the drive.
         *
         * @param password The new password to be saved in the configuration
         *                 This password will be used to derive the master key that will be used to
         *                 encrypt the combined key (encryption key + hash key)
        """
        drive_key: bytearray = self.get_key().get_drive_key()
        hash_key: bytearray = self.get_key().get_hash_key()

        config_file: IRealFile = self.__realRoot.get_child(SalmonDrive.__configFilename)

        # if it's an existing config that we need to update with
        # the new password then we prefer to be authenticate
        # TODO: we should probably call Authenticate() rather than assume
        #  that the key is not None. Though the user can anyway manually delete the config file
        #  so it doesn't matter.
        if drive_key is None and config_file is not None and config_file.exists():
            raise SalmonAuthException("Not authenticated")

        # delete the old config file and create a new one
        if config_file is not None and config_file.exists():
            config_file.delete()
        config_file = self.__realRoot.create_file(SalmonDrive.__configFilename)

        magic_bytes: bytearray = SalmonGenerator.getMagicBytes()

        version: int = SalmonGenerator.getVersion()

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
            hash_key[0:SalmonGenerator.HASH_KEY_LENGTH] = comb_key[
                                                          SalmonGenerator.KEY_LENGTH:SalmonGenerator.HASH_KEY_LENGTH]
            self.__driveID = SalmonDriveGenerator.generate_drive_id()

        # Get the salt that we will use to encrypt the combined key (drive key + hash key)
        salt: bytearray = SalmonDriveGenerator.generate_salt()

        iterations: int = SalmonDriveGenerator.get_iterations()

        # generate a 128 bit IV that will be used with the master key
        # to encrypt the combined 64-bit key (drive key + hash key)
        master_key_iv: bytearray = SalmonDriveGenerator.generate_master_key_iv()

        # create a key that will encrypt both the (drive key and the hash key)
        master_key: bytearray = SalmonPassword.getMasterKey(password, salt, iterations,
                                                            SalmonDriveGenerator.MASTER_KEY_LENGTH)

        # encrypt the combined key (drive key + hash key) using the master_key and the masterKeyIv
        ms: MemoryStream = MemoryStream()
        stream: SalmonStream = SalmonStream(master_key, master_key_iv, SalmonStream.EncryptionMode.Encrypt, ms,
                                            None, False, None, None)
        stream.write(drive_key, 0, len(drive_key))
        stream.write(hash_key, 0, len(hash_key))
        stream.write(self.__driveID, 0, len(self.__driveID))
        stream.flush()
        stream.close()
        enc_data: bytearray = ms.toArray()

        # generate the hash signature
        hash_signature: bytearray = SalmonIntegrity.calculateHash(self.__hashProvider, enc_data, 0, len(enc_data),
                                                                  hash_key, None)

        SalmonDriveConfig.write_drive_config(config_file, magic_bytes, version, salt, iterations, master_key_iv,
                                             enc_data, hash_signature)
        self.__set_key(master_key, drive_key, hash_key, iterations)

        if new_drive:
            # create a full sequence for nonces
            auth_id: bytearray = SalmonDriveGenerator.generate_auth_id()
            SalmonDriveManager.create_sequence(self.__driveID, auth_id)
            SalmonDriveManager.init_sequence(self.__driveID, auth_id)

        self._init_fs()

    def get_virtual_root(self) -> SalmonFile | None:
        """
         * Return the virtual root directory of the drive.
         * @return
         * @throws SalmonAuthException
        """
        if self.__realRoot is None or not self.__realRoot.exists():
            return None
        if not self.is_authenticated():
            raise SalmonAuthException("Not authenticated")
        return self.__virtualRoot

    def _get_real_root(self) -> IRealFile:
        return self.__realRoot

    def authenticate(self, password: str):
        """
         * Verify if the user password is correct otherwise it throws a SalmonAuthException
         *
         * @param password The password.
        """
        stream: SalmonStream | None = None
        try:
            if password is None:
                raise SalmonSecurityException("Password is missing")

            salmon_config: SalmonDriveConfig = self.__get_drive_config()
            iterations: int = salmon_config.get_iterations()
            salt: bytearray = salmon_config.get_salt()

            # derive the master key from the text password
            master_key: bytearray = SalmonPassword.getMasterKey(password, salt, iterations,
                                                                SalmonDriveGenerator.MASTER_KEY_LENGTH)

            # get the master Key Iv
            master_key_iv: bytearray = salmon_config.get_iv()

            # get the encrypted combined key and drive id
            enc_data: bytearray = salmon_config.get_encrypted_data()

            # decrypt the combined key (drive key + hash key) using the master key
            ms: MemoryStream = MemoryStream(enc_data)
            stream = SalmonStream(master_key, master_key_iv, SalmonStream.EncryptionMode.Decrypt, ms,
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
            self.__set_key(master_key, drive_key, hash_key, iterations)
            self.__driveID = drive_id
            self._init_fs()
            self._on_authentication_success()
        except Exception as ex:
            self._on_authentication_error()
            raise ex
        finally:
            if stream is not None:
                stream.close()

    def __set_key(self, master_key: bytearray, drive_key: bytearray, hash_key: bytearray, iterations: int):
        """
         * Sets the key properties.
         * @param master_key The master key.
         * @param drive_key The drive key used for enc/dec of files and filenames.
         * @param hash_key The hash key used for data integrity.
         * @param iterations
        """
        self.__key.set_master_key(master_key)
        self.__key.set_drive_key(drive_key)
        self.__key.set_hash_key(hash_key)
        self.__key.set_iterations(iterations)

    def __verify_hash(self, salmon_config: SalmonDriveConfig, data: bytearray, hash_key: bytearray):
        """
         * Verify that the hash signature is correct
         *
         * @param salmon_config
         * @param data
         * @param hash_key
        """
        hash_signature: bytearray = salmon_config.get_hash_signature()
        v_hash: bytearray = SalmonIntegrity.calculateHash(self.__hashProvider, data, 0, len(data), hash_key, None)
        for i in range(0, len(hash_key)):
            if hash_signature[i] != v_hash[i]:
                raise SalmonAuthException("Could not authenticate")

    def get_next_nonce(self) -> bytearray:
        """
         * Get the next nonce from the sequencer. This advanced the sequencer so unique nonce are used.
         * @return
         * @throws Exception
        """
        if not self.is_authenticated():
            raise SalmonAuthException("Not authenticated")
        return SalmonDriveManager.get_next_nonce(self)

    def is_authenticated(self) -> bool:
        """
         * Returns True if password authentication has succeeded.
        """
        key: SalmonKey = self.get_key()
        if key is None:
            return False
        enc_key: bytearray = key.get_drive_key()
        return enc_key is not None

    def get_bytes_from_real_file(self, source_path: str, buffer_size: int) -> bytearray:
        """
         * Get the byte contents of a file from the real filesystem.
         *
         * @param source_path The path of the file
         * @param buffer_size The buffer to be used when reading
        """
        file: IRealFile = self.get_real_file(source_path, False)
        stream: RandomAccessStream = file.get_input_stream()
        ms: MemoryStream = MemoryStream()
        stream.copyTo(ms, buffer_size, None)
        ms.flush()
        ms.position(0)
        byte_contents: bytearray = ms.toArray()
        ms.close()
        stream.close()
        return byte_contents

    def get_drive_config_file(self) -> IRealFile | None:
        """
         * Return the drive configuration file.
        """
        if self.__realRoot is None or not self.__realRoot.exists():
            return None
        file: IRealFile = self.__realRoot.get_child(SalmonDrive.__configFilename)
        return file

    def get_export_dir(self) -> IRealFile:
        """
         * Return the default external export dir that all file can be exported to.
         * @return The file on the real filesystem.
        """
        virtual_thumbnails_real_dir: IRealFile = self.__realRoot.get_child(SalmonDrive.__exportDirectoryName)
        if virtual_thumbnails_real_dir is None or not virtual_thumbnails_real_dir.exists():
            virtual_thumbnails_real_dir = self.__realRoot.create_directory(SalmonDrive.__exportDirectoryName)
        return virtual_thumbnails_real_dir

    def __get_drive_config(self) -> SalmonDriveConfig | None:
        """
         * Return the configuration properties of this drive.
        """
        config_file: IRealFile = self.get_drive_config_file()
        if config_file is None or not config_file.exists():
            return None
        v_bytes: bytearray = self.get_bytes_from_real_file(config_file.get_path(), 0)
        drive_config: SalmonDriveConfig = SalmonDriveConfig(v_bytes)
        return drive_config

    def has_config(self) -> bool:
        """
         * Return True if the drive is already created and has a configuration file.
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
         * Get the drive ID.
         * @return
        """
        return self.__driveID

    def close(self):
        """
         * Close the drive and associated resources.
        """
        self.__realRoot = None
        self.__virtualRoot = None
        self.__driveID = None
        if self.__key is not None:
            self.__key.clear()
        self.__key = None
