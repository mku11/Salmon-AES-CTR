#!/usr/bin/env python3
from salmon_core.salmon.generator import Generator
from salmon_core.salmon.text.text_decryptor import TextDecryptor
from salmon_core.salmon.text.text_encryptor import TextEncryptor


class TextSample:
    @staticmethod
    def encrypt_text(text, key):
        # Always request a new random secure nonce.
        nonce = Generator.get_secure_random_bytes(8)

        # encrypt string and embed the nonce in the header
        enc_text = TextEncryptor.encrypt_string(text, key, nonce)
        return enc_text

    @staticmethod
    def decrypt_text(enc_text, key):
        # decrypt string, the nonce is already embedded
        dec_text = TextDecryptor.decrypt_string(enc_text, key)
        return dec_text
