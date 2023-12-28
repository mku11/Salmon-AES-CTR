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
from typing import Type

from convert.bit_converter import BitConverter
from file.ireal_file import IRealFile
from iostream.memory_stream import MemoryStream
from salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon.iostream.encryption_mode import EncryptionMode
from salmon.iostream.salmon_stream import SalmonStream
from salmon.password.salmon_password import SalmonPassword
from salmon.salmon_generator import SalmonGenerator
from salmon.salmon_nonce import SalmonNonce
from salmon.salmon_security_exception import SalmonSecurityException
from salmonfs.salmon_auth_config import SalmonAuthConfig
from salmonfs.salmon_auth_exception import SalmonAuthException
from salmonfs.salmon_drive import SalmonDrive
from salmonfs.salmon_drive_config import SalmonDriveConfig
from salmonfs.salmon_drive_generator import SalmonDriveGenerator
from salmonfs.salmon_file import SalmonFile
from sequence.isalmon_sequencer import ISalmonSequencer
from sequence.salmon_sequence import SalmonSequence
from threading import RLock


class SalmonDriveManager:
    """
     * Manages the drive and nonce sequencer to be used.
     * Currently only one drive and one nonce sequencer are supported.
    """

    __drive_class_type: Type | None = None
    __drive: SalmonDrive | None = None
    __sequencer: ISalmonSequencer | None = None
    __lock = RLock()

    @staticmethod
    def set_virtual_drive_class(drive_class_type: Type):
        """
         * Set the global drive class. Currently only one drive is supported.
         *
         * @param drive_class_type
        """
        SalmonDriveManager.__drive_class_type = drive_class_type

    @staticmethod
    def get_sequencer() -> ISalmonSequencer:
        """
         * Get the nonce sequencer used for the current drive.
         *
         * @return
        """
        return SalmonDriveManager.__sequencer

    @staticmethod
    def set_sequencer(sequencer: ISalmonSequencer):
        """
         * Set the nonce sequencer used for the current drive.
         *
         * @param sequencer
        """
        SalmonDriveManager.__sequencer = sequencer

    @staticmethod
    def get_drive() -> SalmonDrive:
        """
         * Get the current virtual drive.
        """
        return SalmonDriveManager.__drive

    @staticmethod
    def open_drive(dir_path: str) -> SalmonDrive:
        """
         * Set the drive location to an external directory.
         * This requires you previously use SetDriveClass() to provide a class for the drive
         *
         * @param dir_path The directory path that will be used for storing the contents of the drive
        """
        SalmonDriveManager.close_drive()
        drive: SalmonDrive = SalmonDriveManager.__create_drive_instance(dir_path, False)
        if not drive.has_config():
            Exception("Drive does not exist")

        SalmonDriveManager.__drive = drive
        return drive

    @staticmethod
    def create_drive(dir_path: str, password: str) -> SalmonDrive:
        """
         * Create a new drive in the provided location.
         *
         * @param dir_path  Directory to store the drive configuration and virtual filesystem.
         * @param password Master password to encrypt the drive configuration.
         * @return The newly created drive.
         * @throws SalmonIntegrityException
         * @throws SalmonSequenceException
        """
        SalmonDriveManager.close_drive()
        drive: SalmonDrive = SalmonDriveManager.__create_drive_instance(dir_path, True)
        if drive.has_config():
            raise SalmonSecurityException("Drive already exists")
        SalmonDriveManager.__drive = drive
        SalmonDriveManager.set_password(drive, password)
        return drive

    @staticmethod
    def __create_drive_instance(dir_path: str, create_if_not_exists: bool) -> SalmonDrive:
        """
         * Create a drive instance.
         *
         * @param dir_path The target directory where the drive is located.
         * @param create_if_not_exists Create the drive if it does not exist
         * @return
         * @throws SalmonSecurityException
        """
        drive: SalmonDrive | None = None
        try:
            drive = SalmonDriveManager.__drive_class_type(dir_path, create_if_not_exists)
            drive.set_sequencer(SalmonDriveManager.__sequencer)
            pass
        except Exception as e:
            raise SalmonSecurityException("Could not create drive instance") from e

        return drive

    @staticmethod
    def close_drive():
        """
         * Close the current drive.
        """
        if SalmonDriveManager.__drive is not None:
            SalmonDriveManager.__drive.close()
            SalmonDriveManager.__drive = None

    @staticmethod
    def __get_auth_id_bytes() -> bytearray:
        """
         * Get the device authorization byte array for the current drive.
         *
         * @return
         * @throws Exception
        """
        drv_str: str = BitConverter.to_hex(SalmonDriveManager.get_drive().get_drive_id())
        sequence: SalmonSequence = SalmonDriveManager.__sequencer.get_sequence(drv_str)
        if sequence is None:
            auth_id: bytearray = SalmonDriveGenerator.generate_auth_id()
            SalmonDriveManager.create_sequence(SalmonDriveManager.get_drive().get_drive_id(), auth_id)

        sequence = SalmonDriveManager.__sequencer.get_sequence(drv_str)
        return BitConverter.hex_to_bytes(sequence.get_auth_id())

    @staticmethod
    def import_auth_file(file_path: str):
        """
         * Import the device authorization file.
         *
         * @param file_path The filepath to the authorization file.
         * @throws Exception
        """
        sequence: SalmonSequence = SalmonDriveManager.__sequencer.get_sequence(
            BitConverter.to_hex(SalmonDriveManager.get_drive().get_drive_id()))
        if sequence is not None and sequence.get_status() == SalmonSequence.Status.Active:
            raise Exception("Device is already authorized")

        auth_config_file: IRealFile = SalmonDriveManager.get_drive().get_real_file(file_path, False)
        if auth_config_file is None or not auth_config_file.exists():
            raise Exception("Could not import file")

        auth_config: SalmonAuthConfig = SalmonDriveManager.get_auth_config(auth_config_file)

        if not SalmonDriveManager.__arrays_equal(
                auth_config.get_auth_id(), SalmonDriveManager.__get_auth_id_bytes()) \
                or not SalmonDriveManager.__arrays_equal(auth_config.get_drive_id(),
                                                         SalmonDriveManager.get_drive().get_drive_id()):
            raise Exception("Auth file doesn't match drive_id or auth_id")

        SalmonDriveManager.import_sequence(auth_config)

    @staticmethod
    def get_default_auth_config_filename() -> str:
        """
         * Get the default auth config filename.
         *
         * @return
        """
        return SalmonDrive.get_auth_config_filename()

    @staticmethod
    def export_auth_file(target_auth_id: str, target_dir: str, filename: str):
        """
         * @param target_auth_id The authentication id of the target device.
         * @param target_dir    The target dir the file will be written to.
         * @param filename     The filename of the auth config file.
         * @throws Exception
        """
        cfg_nonce: bytearray = SalmonDriveManager.__sequencer.next_nonce(
            BitConverter.to_hex(SalmonDriveManager.get_drive().get_drive_id()))

        sequence: SalmonSequence = SalmonDriveManager.__sequencer.get_sequence(
            BitConverter.to_hex(SalmonDriveManager.get_drive().get_drive_id()))
        if sequence is None:
            raise Exception("Device is not authorized to export")
        v_dir: IRealFile = SalmonDriveManager.get_drive().get_real_file(target_dir, True)
        target_app_drive_config_file: IRealFile = v_dir.get_child(filename)
        if target_app_drive_config_file is None or not target_app_drive_config_file.exists():
            target_app_drive_config_file = v_dir.create_file(filename)

        pivot_nonce: bytearray = SalmonNonce.split_nonce_range(sequence.get_next_nonce(), sequence.get_max_nonce())
        SalmonDriveManager.__sequencer.set_max_nonce(sequence.get_drive_id(), sequence.get_auth_id(), pivot_nonce)
        SalmonAuthConfig.write_auth_file(target_app_drive_config_file, SalmonDriveManager.get_drive(),
                                         BitConverter.hex_to_bytes(target_auth_id),
                                         pivot_nonce, sequence.get_max_nonce(),
                                         cfg_nonce)

    @staticmethod
    def create_sequence(drive_id: bytearray, auth_id: bytearray):
        """
         * Create a nonce sequence for the drive id and the authentication id provided. Should be called
         * once per drive_id/auth_id combination.
         *
         * @param drive_id The drive_id
         * @param auth_id  The auth_id
         * @throws Exception
        """
        drv_str: str = BitConverter.to_hex(drive_id)
        auth_str: str = BitConverter.to_hex(auth_id)
        SalmonDriveManager.__sequencer.create_sequence(drv_str, auth_str)

    @staticmethod
    def init_sequence(drive_id: bytearray, auth_id: bytearray):
        """
         * Initialize the nonce sequencer with the current drive nonce range. Should be called
         * once per drive_id/auth_id combination.
         *
         * @param drive_id Drive ID.
         * @param auth_id  Authentication ID.
         * @throws Exception
        """
        starting_nonce: bytearray = SalmonDriveGenerator.get_starting_nonce()
        max_nonce: bytearray = SalmonDriveGenerator.get_max_nonce()
        drv_str: str = BitConverter.to_hex(drive_id)
        auth_str: str = BitConverter.to_hex(auth_id)
        SalmonDriveManager.__sequencer.init_sequence(drv_str, auth_str, starting_nonce, max_nonce)

    @staticmethod
    def revoke_authorization():
        """
         * Revoke authorization for this device. This will effectively terminate write operations on the current disk
         * by the current device. Warning: If you need to authorize write operations to the device again you will need
         * to have another device to export an authorization config file and reimport it.
         *
         * @throws Exception
         * @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
        """
        drive_id: bytearray = SalmonDriveManager.__drive.get_drive_id()
        SalmonDriveManager.__sequencer.revoke_sequence(BitConverter.to_hex(drive_id))

    @staticmethod
    def __verify_auth_id(auth_id: bytearray) -> bool:
        """
         * Verify the authentication id with the current drive auth id.
         *
         * @param auth_id The authentication id to verify.
         * @return
         * @throws Exception
        """
        return SalmonDriveManager.__arrays_equal(auth_id, SalmonDriveManager.__get_auth_id_bytes())

    @staticmethod
    def import_sequence(auth_config: SalmonAuthConfig):
        """
         * Import sequence into the current drive.
         *
         * @param auth_config
         * @throws Exception
        """
        drv_str: str = BitConverter.to_hex(auth_config.get_drive_id())
        auth_str: str = BitConverter.to_hex(auth_config.get_auth_id())
        SalmonDriveManager.__sequencer.init_sequence(drv_str, auth_str, auth_config.get_start_nonce(),
                                                     auth_config.get_max_nonce())

    @staticmethod
    def get_auth_config(auth_file: IRealFile) -> SalmonAuthConfig:
        """
         * Get the app drive pair configuration properties for this drive
         *
         * @param auth_file The encrypted authentication file.
         * @return The decrypted authentication file.
         * @throws Exception
        """
        salmon_file: SalmonFile = SalmonFile(auth_file, SalmonDriveManager.get_drive())
        stream: SalmonStream = salmon_file.get_input_stream()
        ms: MemoryStream = MemoryStream()
        stream.copy_to(ms)
        ms.close()
        stream.close()
        drive_config: SalmonAuthConfig = SalmonAuthConfig(ms.to_array())
        if not SalmonDriveManager.__verify_auth_id(drive_config.get_auth_id()):
            raise SalmonSecurityException("Could not authorize this device, the authentication id does not match")
        return drive_config

    @staticmethod
    def get_auth_id() -> str:
        """
         * Get the authentication ID for the current device.
         *
         * @return
         * @throws SalmonSequenceException
         * @throws SalmonAuthException
        """
        return BitConverter.to_hex(SalmonDriveManager.__get_auth_id_bytes())

    @staticmethod
    def __arrays_equal(array1: bytearray, array2: bytearray) -> bool:
        if len(array1) != len(array2):
            return False
        for i in range(0, len(array1)):
            if array1[i] != array2[i]:
                return False
        return True

    @staticmethod
    def __create_config(drive: SalmonDrive, password: str):
        """
         * Create a configuration file for the drive.
         *
         * @param password The new password to be saved in the configuration
         *                 This password will be used to derive the master key that will be used to
         *                 encrypt the combined key (encryption key + hash key)
        """
        drive_key: bytearray = drive.get_key().get_drive_key()
        hash_key: bytearray = drive.get_key().get_hash_key()

        config_file: IRealFile = drive.get_real_root().get_child(SalmonDrive.get_config_filename())

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
        config_file = drive.get_real_root().create_file(SalmonDrive.get_config_filename())

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
            hash_key[0:SalmonGenerator.HASH_KEY_LENGTH] = comb_key[
                                                          SalmonGenerator.KEY_LENGTH:SalmonGenerator.KEY_LENGTH
                                                                                     + SalmonGenerator.HASH_KEY_LENGTH]
            drive.set_drive_id(SalmonDriveGenerator.generate_drive_id())

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
        stream.write(drive.get_drive_id(), 0, len(drive.get_drive_id()))
        stream.flush()
        stream.close()
        enc_data: bytearray = ms.to_array()

        # generate the hash signature
        hash_signature: bytearray = SalmonIntegrity.calculate_hash(drive.get_hash_provider(), enc_data, 0,
                                                                   len(enc_data),
                                                                   hash_key, None)

        SalmonDriveConfig.write_drive_config(config_file, magic_bytes, version, salt, iterations, master_key_iv,
                                             enc_data, hash_signature)
        drive.set_key(master_key, drive_key, hash_key, iterations)

        if new_drive:
            # create a full sequence for nonces
            auth_id: bytearray = SalmonDriveGenerator.generate_auth_id()
            SalmonDriveManager.create_sequence(drive.get_drive_id(), auth_id)
            SalmonDriveManager.init_sequence(drive.get_drive_id(), auth_id)

        drive.init_fs()

    @staticmethod
    def set_password(drive: SalmonDrive, password: str):
        """
         * Change the user password.
         * @param pass The new password.
         * @throws IOError
         * @throws SalmonAuthException
         * @throws SalmonSecurityException
         * @throws SalmonIntegrityException
         * @throws SalmonSequenceException
        """
        with (SalmonDriveManager.__lock):
            SalmonDriveManager.__create_config(drive, password)
