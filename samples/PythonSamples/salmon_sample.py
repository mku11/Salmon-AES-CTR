#!/usr/bin/env python3
from __future__ import annotations

import os
import random

from salmon_core.convert.bit_converter import BitConverter
from salmon_core.iostream.memory_stream import MemoryStream
from salmon_core.iostream.random_access_stream import RandomAccessStream
from salmon_core.salmon.iostream.salmon_stream import SalmonStream
from salmon_core.salmon.password.salmon_password import SalmonPassword
from salmon_core.salmon.salmon_decryptor import SalmonDecryptor
from salmon_core.salmon.salmon_default_options import SalmonDefaultOptions
from salmon_core.salmon.salmon_encryptor import SalmonEncryptor
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_core.salmon.text.salmon_text_decryptor import SalmonTextDecryptor
from salmon_core.salmon.text.salmon_text_encryptor import SalmonTextEncryptor
from salmon_fs.file.ireal_file import IRealFile
from salmon_fs.file.py_drive import PyDrive
from salmon_fs.file.py_file import PyFile
from salmon_fs.salmonfs.salmon_drive import SalmonDrive
from salmon_fs.salmonfs.salmon_drive_manager import SalmonDriveManager
from salmon_fs.salmonfs.salmon_file import SalmonFile
from salmon_fs.salmonfs.salmon_file_input_stream import SalmonFileInputStream
from salmon_fs.sequence.salmon_file_sequencer import SalmonFileSequencer
from salmon_fs.sequence.salmon_sequence_serializer import SalmonSequenceSerializer
from salmon_fs.utils.salmon_file_commander import SalmonFileCommander


def main():
    random.seed()

    # uncomment to load the AES intrinsics for better performance
    # you can download the native libraries for your architecture from:
    # https://github.com/mku11/Salmon-AES-CTR
    # NativeProxy.set_library_path("../../projects/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/salmon.dll")
    # SalmonStream.set_aes_provider_type(ProviderType.AesIntrinsics)

    password: str = "MYS@LMONP@$$WORD"

    # some test to encrypt
    text: str = "This is a plaintext that will be used for testing"
    v_bytes: bytearray = bytearray(text.encode('utf-8'))

    # some data to encrypt
    data: bytearray = bytearray(os.urandom(1 * 1024 * 1024))

    # you can create a key and reuse it:
    # byte[] key = SalmonGenerator.getSecureRandomBytes(32)
    # or get one derived from a text password:
    salt: bytearray = SalmonGenerator.getSecureRandomBytes(24)

    # make sure the iterations are a large enough number
    key: bytearray = SalmonPassword.getKeyFromPassword(password, salt, 60000, 32)

    # encrypt and decrypt byte array using multiple threads:
    encrypt_and_decrypt_using_multiple_threads(data, key)

    # encrypt and decrypt a text string:
    encrypt_and_decrypt_text_embedding_nonce(text, key)

    # encrypt and decrypt data to a byte array stream:
    encrypt_and_decrypt_data_to_byte_array_stream(v_bytes, key)

    # encrypt and decrypt text to a file:
    encrypt_and_decrypt_text_to_file(text, key)

    # create a drive import, read the encrypted content, and export
    create_drive_and_import_file(password)


def encrypt_and_decrypt_using_multiple_threads(v_bytes: bytearray, key: bytearray):
    print("Encrypting bytes using multiple threads: " + BitConverter.toHex(v_bytes).substring(0, 24) + "...")

    # Always request a new random secure nonce.
    nonce: bytearray = SalmonGenerator.getSecureRandomBytes(8)

    # encrypt a byte array using 2 threads
    enc_bytes: bytearray = SalmonEncryptor(2).encrypt(v_bytes, key, nonce, False)
    print("Encrypted bytes: " + BitConverter.toHex(enc_bytes).substring(0, 24) + "...")

    # decrypt byte array using 2 threads
    dec_bytes: bytearray = SalmonDecryptor(2).decrypt(enc_bytes, key, nonce, False)
    print("Decrypted bytes: " + BitConverter.toHex(dec_bytes).substring(0, 24) + "...")
    print()


def encrypt_and_decrypt_text_embedding_nonce(text: str, key: bytearray):
    print("Encrypting text with nonce embedded: " + text)

    # Always request a new random secure nonce.
    nonce: bytearray = SalmonGenerator.getSecureRandomBytes(8)

    # encrypt string and save the nonce in the header
    enc_text: str = SalmonTextEncryptor.encryptString(text, key, nonce, True)
    print("Encrypted text: " + enc_text)

    # decrypt string without the need to provide the nonce since it's stored in the header
    dec_text: str = SalmonTextDecryptor.decryptString(enc_text, key, None, True)
    print("Decrypted text: " + dec_text)
    print()


def encrypt_and_decrypt_data_to_byte_array_stream(v_bytes: bytearray, key: bytearray):
    print("Encrypting data to byte array stream: " + BitConverter.toHex(v_bytes))

    # Always request a new random secure nonce!
    nonce: bytearray = SalmonGenerator.getSecureRandomBytes(8)  # 64 bit nonce

    # encrypt data to a byte output stream
    enc_out_stream: MemoryStream = MemoryStream()  # or use your custom output stream by extending RandomAccessStream

    # pass the output stream to the SalmonStream
    encrypter: SalmonStream = SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, enc_out_stream, None, False,
                                           None, None)

    # encrypt and write with a single call, you can also Seek() and Write()
    encrypter.write(v_bytes, 0, len(v_bytes))

    # encrypted data are now written to the encOutStream.
    enc_out_stream.position(0)
    enc_data: bytearray = enc_out_stream.toArray()
    encrypter.flush()
    encrypter.close()
    enc_out_stream.close()

    # decrypt a stream with encoded data
    # or use your custom input stream by extending AbsStream
    enc_input_stream: RandomAccessStream = MemoryStream(enc_data)
    decrypter: SalmonStream = SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt, enc_input_stream, None,
                                           False,
                                           None, None)
    dec_buffer: bytearray = bytearray(decrypter.length())

    # seek to the beginning or any position in the stream
    decrypter.seek(0, RandomAccessStream.SeekOrigin.Begin)

    # decrypt and read data with a single call, you can also Seek() before Read()
    bytes_read: int = decrypter.read(dec_buffer, 0, len(dec_buffer))
    decrypter.close()
    enc_input_stream.close()

    print("Decrypted data: " + BitConverter.toHex(dec_buffer))
    print()


def encrypt_and_decrypt_text_to_file(text: str, key: bytearray):
    # encrypt to a file, the SalmonFile has a virtual file system API
    print("Encrypting text to File: " + text)
    test_file: str = "D:/tmp/salmontestfile.txt"

    # the real file:
    t_file: IRealFile = PyFile(test_file)
    if t_file.exists():
        t_file.delete()

    v_bytes: bytearray = bytearray(text.encode('utf-8'))

    # Always request a new random secure nonce. Though if you will be re-using
    # the same key you should create a SalmonDrive to keep the nonces unique.
    nonce: bytearray = SalmonGenerator.getSecureRandomBytes(8)  # 64 bit nonce

    enc_file: SalmonFile = SalmonFile(PyFile(test_file), None)
    nonce = SalmonGenerator.getSecureRandomBytes(8)  # always get a fresh nonce!
    enc_file.setEncryptionKey(key)
    enc_file.setRequestedNonce(nonce)
    stream: RandomAccessStream = enc_file.getOutputStream()

    # encrypt data and write with a single call
    stream.write(v_bytes, 0, len(v_bytes))
    stream.flush()
    stream.close()

    # Decrypt the file
    enc_file2: SalmonFile = SalmonFile(PyFile(test_file), None)
    enc_file2.setEncryptionKey(key)
    stream2: RandomAccessStream = enc_file2.getInputStream()
    dec_buff: bytearray = bytearray(1024)

    # read data with a single call
    enc_bytes_read: int = stream2.read(dec_buff, 0, len(dec_buff))
    dec_string2: str = dec_buff[0:enc_bytes_read].decode('utf-8')
    print("Decrypted text: " + dec_string2)
    stream2.close()
    print()


def create_drive_and_import_file(password: str):
    # create a file nonce sequencer
    seq_filename: str = "sequencer.xml"
    v_dir: PyFile = PyFile("output")
    if not v_dir.exists():
        v_dir.mkdir()
    sequence_file: IRealFile = v_dir.getChild(seq_filename)
    file_sequencer: SalmonFileSequencer = SalmonFileSequencer(sequence_file, SalmonSequenceSerializer())
    SalmonDriveManager.setVirtualDriveClass(PyDrive)
    SalmonDriveManager.setSequencer(file_sequencer)

    # create a drive
    drive: SalmonDrive = SalmonDriveManager.createDrive(v_dir.getPath() + "/vault" + random.randint(0, 2 ** 31),
                                                        password)
    commander: SalmonFileCommander = SalmonFileCommander(SalmonDefaultOptions.getBufferSize(),
                                                         SalmonDefaultOptions.getBufferSize(), 2)
    files: list[PyFile] = [PyFile("data/file.txt")]

    # import multiple files
    commander.importFiles(files, drive.getVirtualRoot(), False, True, import_progress, IRealFile.autoRename,
                          failed_to_import)

    # query for the file from the drive
    file: SalmonFile = drive.getVirtualRoot().getChild("file.txt")

    # read from the stream with parallel threads and caching
    input_stream: SalmonFileInputStream = SalmonFileInputStream(file, 4, 4 * 1024 * 1024, 2, 256 * 1024)
    # inputStream.read(...)
    input_stream.close()

    # export the file
    commander.export_files([file], PyFile("output"), False, True, export_progress, IRealFile.autoRename,
                           failed_to_import)

    # close the file commander
    commander.close()

    # close the drive
    drive.close()


def failed_to_import(sfile: SalmonFile, ex: Exception):
    print("file failed to import: " + sfile.get_base_name() + str(ex))


def export_progress(task_progress: SalmonFileCommander.SalmonFileTaskProgress):
    print(
        "file exporting: " + task_progress.getFile().getBaseName() + ": " + task_progress.getProcessedBytes()
        + "/" + task_progress.getTotalBytes() + " bytes")


def import_progress(task_progress: SalmonFileCommander.RealFileTaskProgress):
    print(
        "file importing: " + task_progress.getFile().getBaseName() + ": " + task_progress.getProcessedBytes()
        + "/" + task_progress.getTotalBytes() + " bytes")


main()
