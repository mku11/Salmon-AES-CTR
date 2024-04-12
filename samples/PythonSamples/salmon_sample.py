#!/usr/bin/env python3
from __future__ import annotations

import os
import random
import traceback

from salmon_core.convert.bit_converter import BitConverter
from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.streams.salmon_stream import SalmonStream
from salmon_core.salmon.password.salmon_password import SalmonPassword
from salmon_core.salmon.salmon_decryptor import SalmonDecryptor
from salmon_core.salmon.salmon_encryptor import SalmonEncryptor
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_core.salmon.text.salmon_text_decryptor import SalmonTextDecryptor
from salmon_core.salmon.text.salmon_text_encryptor import SalmonTextEncryptor
from salmon_fs.file.ireal_file import IRealFile
from salmon_fs.salmon.drive.py_drive import PyDrive
from salmon_fs.file.py_file import PyFile
from salmon_fs.salmon.salmon_drive import SalmonDrive
from salmon_fs.salmon.salmon_file import SalmonFile
from salmon_fs.salmon.streams.salmon_file_input_stream import SalmonFileInputStream
from salmon_fs.salmon.sequence.salmon_file_sequencer import SalmonFileSequencer
from salmon_fs.salmon.sequence.salmon_sequence_serializer import SalmonSequenceSerializer
from salmon_fs.salmon.utils.salmon_file_commander import SalmonFileCommander

text: str = "This is a plaintext that will be used for testing"
# some data to encrypt
random.seed()
data: bytearray = bytearray(os.urandom(1 * 1024 * 1024))
password: str = "MYS@LMONP@$$WORD"

def main():
    # uncomment to load the AES intrinsics for better performance
    # you can download the native libraries for your architecture from:
    # https:#github.com/mku11/Salmon-AES-CTR
    # NativeProxy.set_library_path("../../projects/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/salmon.dll")
    # SalmonStream.set_aes_provider_type(ProviderType.AesIntrinsics)

    # use the password to create a drive and import a file
    vault_path: str = "vault_" + BitConverter.to_hex(SalmonGenerator.get_secure_random_bytes(6))
    vault_dir: PyFile = PyFile(vault_path)
    vault_dir.mkdir()
    files_to_import: list[PyFile] = [PyFile("data/file.txt")]
    create_drive_and_import_file(vault_dir, files_to_import)

    # or encrypt text into a standalone file without a drive:
    file_path: str = "data_" + BitConverter.to_hex(SalmonGenerator.get_secure_random_bytes(6))
    file: PyFile = PyFile(file_path)
    encrypt_and_decrypt_text_to_file(file)

    # misc stream samples
    stream_samples()


def get_key_from_password(v_password: str) -> bytearray:
    # get a key from a text password:
    salt: bytearray = SalmonGenerator.get_secure_random_bytes(24)
    # make sure the iterations are a large enough number
    key: bytearray = SalmonPassword.get_key_from_password(v_password, salt, 60000, 32)
    return key


def stream_samples():
    # get a fresh key
    key: bytearray = SalmonGenerator.get_secure_random_bytes(32)

    # encrypt and decrypt a text string:
    encrypt_and_decrypt_text_embedding_nonce(text, key)

    # encrypt and decrypt data to a byte array stream:
    encrypt_and_decrypt_data_to_byte_array_stream(bytearray(text.encode('utf-8')), key)

    # encrypt and decrypt byte array using multiple threads:
    encrypt_and_decrypt_using_multiple_threads(data, key)


def encrypt_and_decrypt_using_multiple_threads(v_bytes: bytearray, key: bytearray):
    print("Encrypting bytes using multiple threads: " + BitConverter.to_hex(v_bytes)[0:24] + "...")

    # Always request a new random secure nonce.
    nonce: bytearray = SalmonGenerator.get_secure_random_bytes(8)

    # encrypt a byte array using 2 threads
    encryptor: SalmonEncryptor = SalmonEncryptor(2)
    enc_bytes: bytearray = encryptor.encrypt(v_bytes, key, nonce, False)
    print("Encrypted bytes: " + BitConverter.to_hex(enc_bytes)[0:24] + "...")
    encryptor.close()

    # decrypt byte array using 2 threads
    decryptor: SalmonDecryptor = SalmonDecryptor(2)
    dec_bytes: bytearray = decryptor.decrypt(enc_bytes, key, nonce, False)
    print("Decrypted bytes: " + BitConverter.to_hex(dec_bytes)[0:24] + "...")
    print()
    decryptor.close()


def encrypt_and_decrypt_text_embedding_nonce(v_text: str, key: bytearray):
    print("Encrypting text with nonce embedded: " + v_text)

    # Always request a new random secure nonce.
    nonce: bytearray = SalmonGenerator.get_secure_random_bytes(8)

    # encrypt string and save the nonce in the header
    enc_text: str = SalmonTextEncryptor.encrypt_string(v_text, key, nonce, True)
    print("Encrypted text: " + enc_text)

    # decrypt string without the need to provide the nonce since it's stored in the header
    dec_text: str = SalmonTextDecryptor.decrypt_string(enc_text, key, None, True)
    print("Decrypted text: " + dec_text)
    print()


def encrypt_and_decrypt_data_to_byte_array_stream(v_bytes: bytearray, key: bytearray):
    print("Encrypting data to byte array stream: " + BitConverter.to_hex(v_bytes))

    # Always request a new random secure nonce!
    nonce: bytearray = SalmonGenerator.get_secure_random_bytes(8)  # 64 bit nonce

    # encrypt data to an byte output stream
    enc_out_stream: MemoryStream = MemoryStream()  # or use your custom output stream by extending RandomAccessStream

    # pass the output stream to the SalmonStream
    enc_stream: SalmonStream = SalmonStream(key, nonce, EncryptionMode.Encrypt,
                                            enc_out_stream, None,
                                            False, None, None)

    # encrypt/write data in a single call, you can also Seek() and Write()
    enc_stream.write(v_bytes, 0, len(v_bytes))

    # encrypted data are now written to the encOutStream.
    enc_out_stream.set_position(0)
    enc_data: bytearray = enc_out_stream.to_array()
    enc_stream.flush()
    enc_stream.close()
    enc_out_stream.close()

    # decrypt a stream with encoded data
    enc_input_stream: MemoryStream = MemoryStream(enc_data)  # or use your custom input stream by extending AbsStream
    dec_stream: SalmonStream = SalmonStream(key, nonce, EncryptionMode.Decrypt,
                                            enc_input_stream, None,
                                            False, None, None)
    dec_buffer: bytearray = bytearray(dec_stream.length())

    # seek to the beginning or any position in the stream
    dec_stream.seek(0, RandomAccessStream.SeekOrigin.Begin)

    # read/decrypt data in a single call, you can also Seek() before Read()
    bytes_read: int = dec_stream.read(dec_buffer, 0, len(dec_buffer))
    dec_stream.close()
    enc_input_stream.close()

    print("Decrypted data: " + BitConverter.to_hex(dec_buffer))
    print()


def encrypt_and_decrypt_text_to_file(file: IRealFile):
    # encrypt to a file, the SalmonFile has a virtual file system API
    print("Encrypting text to File: " + text)

    v_bytes: bytearray = bytearray(text.encode('utf-8'))

    # derive the key from the password
    key: bytearray = get_key_from_password(password)

    # Always request a new random secure nonce
    nonce: bytearray = SalmonGenerator.get_secure_random_bytes(8)  # 64 bit nonce

    enc_file: SalmonFile = SalmonFile(file)
    enc_file.set_encryption_key(key)
    enc_file.set_requested_nonce(nonce)
    stream: RandomAccessStream = enc_file.get_output_stream()

    # encrypt/write data in a single call
    stream.write(v_bytes, 0, len(v_bytes))
    stream.flush()
    stream.close()

    # Decrypt the file
    enc_file2: SalmonFile = SalmonFile(file)
    enc_file2.set_encryption_key(key)
    stream2: RandomAccessStream = enc_file2.get_input_stream()

    # decrypt/read data in a single call
    dec_buff: bytearray = bytearray(1024)
    enc_bytes_read: int = stream2.read(dec_buff, 0, len(dec_buff))
    dec_string2: str = dec_buff[0:enc_bytes_read].decode('utf-8')
    print("Decrypted text: " + dec_string2)
    stream2.close()
    print()


def create_drive_and_import_file(vault_dir: IRealFile, files_to_import: list[IRealFile]):
    # create a file sequencer:
    sequencer: SalmonFileSequencer = create_sequencer()

    # create a drive
    drive: PyDrive = PyDrive.create(vault_dir, password, sequencer)
    commander: SalmonFileCommander = SalmonFileCommander(256 * 1024, 256 * 1024, 2)

    # import multiple files
    files_imported: list[SalmonFile] = commander.import_files(files_to_import, drive.get_root(), False, True,
                                                              import_progress, IRealFile.auto_rename,
                                                              real_file_failed_to_import)
    print("Files imported")

    # query for the file from the drive
    root: SalmonFile = drive.get_root()
    files: list[SalmonFile] = root.list_files()

    # read from a native stream wrapper with parallel threads and caching
    # or use file.getInputStream() to get a low level RandomAccessStream
    file: SalmonFile = files[0]

    input_stream: SalmonFileInputStream = SalmonFileInputStream(file,
                                                                4, 4 * 1024 * 1024, 2, 256 * 1024)
    # inputStream.read(...)
    input_stream.close()

    # export the files
    files_exported: list[IRealFile] = commander.export_files(files, drive.get_export_dir(), False, True,
                                                             export_progress, IRealFile.auto_rename,
                                                             salmon_file_failed_to_export)

    print("Files exported")

    # close the file commander
    commander.close()

    # close the drive
    drive.close()


def create_sequencer():
    # create a file nonce sequencer and place it in a private space
    # make sure you never edit or back up this file.
    seq_filename: str = "sequencer.xml"
    private_dir: IRealFile = PyFile(os.environ['LOCALAPPDATA'])
    sequencer_dir: IRealFile = private_dir.get_child("PythonSalmonSequencer")
    if not sequencer_dir.exists():
        sequencer_dir.mkdir()
    sequence_file: IRealFile = sequencer_dir.get_child(seq_filename)
    file_sequencer: SalmonFileSequencer = SalmonFileSequencer(sequence_file, SalmonSequenceSerializer())
    return file_sequencer


def real_file_failed_to_import(rfile: IRealFile, ex: Exception):
    print("file failed to import: " + rfile.get_base_name() + ", Error: " + str(ex))
    traceback.print_exc()


def salmon_file_failed_to_export(sfile: SalmonFile, ex: Exception):
    print("file failed to export: " + sfile.get_base_name())
    traceback.print_exc()


def export_progress(task_progress: SalmonFileCommander.SalmonFileTaskProgress):
    print(
        "file exporting: " + task_progress.get_file().get_base_name() + ": " + str(task_progress.get_processed_bytes())
        + "/" + str(task_progress.get_total_bytes()) + " bytes")


def import_progress(task_progress: SalmonFileCommander.RealFileTaskProgress):
    print(
        "file importing: " + task_progress.get_file().get_base_name() + ": " + str(task_progress.get_processed_bytes())
        + "/" + str(task_progress.get_total_bytes()) + " bytes")


main()
