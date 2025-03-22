#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O text.py

from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.streams.aes_stream import ProviderType
from salmon_core.salmon.bridge.native_proxy import NativeProxy

from samples.samples_common import get_key_from_password
from samples.text_sample import TextSample

password = "test123"
text = "This is a plain text that will be encrypted"

# Set with the path to the salmon library if you use the native AES providers, see project on github for instructions
# NativeProxy.set_library_path("/path/to/lib/salmon.dll|libsalmon.so|libsalmon.dylib")
AesStream.set_aes_provider_type(ProviderType.Default)

# generate an encryption key from the text password
key = get_key_from_password(password)
print("Plain Text: " + "\n" + text + "\n")

enc_text = TextSample.encrypt_text(text, key)
print("Encrypted Text: " + "\n" + enc_text + "\n")

dec_text = TextSample.decrypt_text(enc_text, key)
print("Decrypted Text: " + "\n" + dec_text + "\n")
