#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O data.py

from salmon_core.salmon.generator import Generator
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.streams.aes_stream import ProviderType
from salmon_core.salmon.bridge.native_proxy import NativeProxy

from samples.data_sample import DataSample
from samples.samples_common import get_key_from_password, generate_random_data

password = "test123"
size = 8 * 1024 * 1024
threads = 1
integrity = True

# Set with the path to the salmon library if you use the native AES providers, see project on github for instructions
# NativeProxy.set_library_path("/path/to/lib/salmon.dll|libsalmon.so|libsalmon.dylib")
AesStream.set_aes_provider_type(ProviderType.Aes)

# generate a key
print("generating keys and random data...")
key = get_key_from_password(password)

# enable integrity (optional)
if integrity:
    # generate an HMAC key
    integrity_key = Generator.get_secure_random_bytes(32)
else:
    integrity_key = None

# generate random data
data = generate_random_data(size)

print("starting encryption...")
enc_data = DataSample.encrypt_data(data, key, integrity_key, threads)
print("starting decryption...")
dec_data = DataSample.decrypt_data(enc_data, key, integrity_key, threads)
print("done")
