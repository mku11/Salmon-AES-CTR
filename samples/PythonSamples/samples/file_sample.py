#!/usr/bin/env python3
from salmon_core.salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_fs.file.ireal_file import IRealFile
from salmon_fs.salmon.salmon_file import SalmonFile


class FileSample:
    BUFFER_SIZE = 256 * 1024  # recommended buffer size aligned to internal buffers

    @staticmethod
    def encrypt_text_to_file(text: str, key: bytearray, integrity_key: bytearray, file: IRealFile):
        # encrypt to a file, the SalmonFile has a virtual file system API
        print("Encrypting text to file: " + file.get_base_name())

        data = bytearray(text.encode())

        # Always request a new random secure nonce
        nonce = SalmonGenerator.get_secure_random_bytes(8)  # 64 bit nonce

        enc_file = SalmonFile(file)
        enc_file.set_encryption_key(key)
        enc_file.set_requested_nonce(nonce)

        if integrity_key:
            enc_file.set_apply_integrity(True, integrity_key, SalmonIntegrity.DEFAULT_CHUNK_SIZE)
        else:
            enc_file.set_apply_integrity(False, None, None)

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
    def decrypt_text_from_file(key: bytearray, integrity_key: bytearray | None, file: IRealFile) -> str:
        print("Decrypting text from file: " + file.get_base_name())

        # Wrap the file with a SalmonFile
        # the nonce is already embedded in the header
        enc_file = SalmonFile(file)

        # set the key
        enc_file.set_encryption_key(key)

        if integrity_key:
            enc_file.set_verify_integrity(True, integrity_key)
        else:
            enc_file.set_verify_integrity(False, None)

        # open a read stream
        dec_stream = enc_file.get_input_stream()

        # decrypt the data
        dec_data = bytearray(dec_stream.length())
        total_bytes_read = 0
        while bytes_read := dec_stream.read(dec_data, total_bytes_read, FileSample.BUFFER_SIZE) > 0:
            total_bytes_read += bytes_read

        dec_text = dec_data[0:total_bytes_read].decode()
        dec_stream.close()

        return dec_text
