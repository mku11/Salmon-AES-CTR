#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O text.py

from salmon_core.salmon.streams.salmon_stream import SalmonStream
from salmon_core.salmon.streams.salmon_stream import ProviderType

from samples.samples_common import get_key_from_password
from samples.text_sample import TextSample

password = "test123"
text = "This is a plain text that will be encrypted"

SalmonStream.set_aes_provider_type(ProviderType.Default)

# generate an encryption key from the text password
key = get_key_from_password(password)
print("Plain Text: " + "\n" + text + "\n")

enc_text = TextSample.encrypt_text(text, key)
print("Encrypted Text: " + "\n" + enc_text + "\n")

dec_text = TextSample.decrypt_text(enc_text, key)
print("Decrypted Text: " + "\n" + dec_text + "\n")
