#!/usr/bin/env python3
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_core.salmon.text.salmon_text_decryptor import SalmonTextDecryptor
from salmon_core.salmon.text.salmon_text_encryptor import SalmonTextEncryptor


class TextSample:
    @staticmethod
    def encrypt_text(text, key):
        # Always request a new random secure nonce.
        nonce = SalmonGenerator.get_secure_random_bytes(8)

        # encrypt string and embed the nonce in the header
        enc_text = SalmonTextEncryptor.encrypt_string(text, key, nonce, True)
        return enc_text

    @staticmethod
    def decrypt_text(enc_text, key):
        # decrypt string, the nonce is already embedded
        dec_text = SalmonTextDecryptor.decrypt_string(enc_text, key, None, True)
        return dec_text
