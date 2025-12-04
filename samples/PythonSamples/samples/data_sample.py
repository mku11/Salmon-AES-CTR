#!/usr/bin/env python3
from simple_io.convert.bit_converter import BitConverter
from salmon_core.salmon.decryptor import Decryptor
from salmon_core.salmon.encryptor import Encryptor
from salmon_core.salmon.generator import Generator
from salmon_core.salmon.streams.encryption_format import EncryptionFormat


class DataSample:

    @staticmethod
    def encrypt_data(data, key, integrity_key, threads):
        print("Encrypting bytes: " + BitConverter.to_hex(data[0:24]) + "...")

        # Always request a new random secure nonce.
        nonce = Generator.get_secure_random_bytes(8)

        encryptor = Encryptor(threads)
        enc_data = encryptor.encrypt(data, key, nonce, EncryptionFormat.Salmon,
                                     True if integrity_key else False, integrity_key)
        encryptor.close()

        print("Bytes encrypted: " + BitConverter.to_hex(enc_data[0:24]) + "...")
        return enc_data

    @staticmethod
    def decrypt_data(data, key, integrity_key, threads):
        print("Decrypting bytes: " + BitConverter.to_hex(data[0:24]) + "...")

        decryptor = Decryptor(threads)
        dec_bytes = decryptor.decrypt(data, key, None, EncryptionFormat.Salmon,
                                      True if integrity_key else False, integrity_key)
        decryptor.close()

        print("Bytes decrypted: " + BitConverter.to_hex(dec_bytes[0:24]) + "...")
        return dec_bytes
