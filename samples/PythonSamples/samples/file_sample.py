#!/usr/bin/env python3
from salmon_core.salmon.integrity.integrity import Integrity
from salmon_core.salmon.generator import Generator
from salmon_fs.fs.file.ifile import IFile
from salmon_fs.salmonfs.file.aes_file import AesFile


class FileSample:
    BUFFER_SIZE = 256 * 1024  # recommended buffer size aligned to internal buffers

    @staticmethod
    def encrypt_text_to_file(text: str, key: bytearray, integrity_key: bytearray, file: IFile):
        # encrypt to a file, the AesFile has a virtual file system API
        print("Encrypting text to file: " + file.get_name())

        data = bytearray(text.encode())

        # Always request a new random secure nonce
        nonce = Generator.get_secure_random_bytes(8)  # 64 bit nonce

        enc_file = AesFile(file)
        enc_file.set_encryption_key(key)
        enc_file.set_requested_nonce(nonce)

        if integrity_key:
            enc_file.set_apply_integrity(True, integrity_key, Integrity.DEFAULT_CHUNK_SIZE)
        else:
            enc_file.set_apply_integrity(False)

        enc_stream = enc_file.get_output_stream()

        # now write the data you want to decrypt
        # it is recommended to use a large enough buffer while writing the data
        # for better performance
        total_bytes_written = 0

        while total_bytes_written < len(data):
            length = min(len(data) - total_bytes_written, FileSample.BUFFER_SIZE)
            enc_stream.write(data, total_bytes_written, length)
            total_bytes_written += length
        enc_stream.flush()
        enc_stream.close()

    @staticmethod
    def decrypt_text_from_file(key: bytearray, integrity_key: bytearray | None, file: IFile) -> str:
        print("Decrypting text from file: " + file.get_name())

        # Wrap the file with a AesFile
        # the nonce is already embedded in the header
        enc_file = AesFile(file)

        # set the key
        enc_file.set_encryption_key(key)

        if integrity_key:
            enc_file.set_verify_integrity(True, integrity_key)
        else:
            enc_file.set_verify_integrity(False, None)

        # open a read stream
        dec_stream = enc_file.get_input_stream()

        # decrypt the data
        dec_data = bytearray(dec_stream.get_length())
        total_bytes_read = 0
        while bytes_read := dec_stream.read(dec_data, total_bytes_read, FileSample.BUFFER_SIZE) > 0:
            total_bytes_read += bytes_read

        dec_text = dec_data[0:total_bytes_read].decode()
        dec_stream.close()

        return dec_text
