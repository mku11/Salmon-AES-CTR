#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O data_stream.py

from salmon_core.convert.bit_converter import BitConverter
from salmon_core.salmon.generator import Generator
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.streams.aes_stream import ProviderType
from salmon_core.salmon.bridge.native_proxy import NativeProxy

from samples.data_stream_sample import DataStreamSample
from samples.samples_common import get_key_from_password, generate_random_data

password = "test123"
size = 1 * 1024 * 1024

# Set with the path to the salmon library if you use the native AES providers, see project on github for instructions
# NativeProxy.set_library_path("/path/to/lib/salmon.dll|libsalmon.so|libsalmon.dylib")
AesStream.set_aes_provider_type(ProviderType.Default)

# generate a key
print("generating keys and random data...")
key = get_key_from_password(password)

# Always request a new random secure nonce!
# if you want to you can embed the nonce in the header data
# see Encryptor implementation
nonce = Generator.get_secure_random_bytes(8)  # 64 bit nonce
print("Created nonce: " + BitConverter.to_hex(nonce))

# generate random data
data = generate_random_data(size)

print("starting encryption...")
enc_data = DataStreamSample.encrypt_data_stream(data, key, nonce)
print("starting decryption...")
dec_data = DataStreamSample.decrypt_data_stream(enc_data, key, nonce)
print("done")
