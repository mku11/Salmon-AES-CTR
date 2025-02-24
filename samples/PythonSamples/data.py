#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O data.py

from salmon_core.salmon.salmon_generator import SalmonGenerator

from samples.data_sample import DataSample
from samples.samples_common import get_key_from_password, generate_random_data

password = "test123"
size = 8 * 1024 * 1024
threads = 1
integrity = True

# generate a key
print("generating keys and random data...")
key = get_key_from_password(password)

# enable integrity (optional)
if integrity:
    # generate an HMAC key
    integrity_key = SalmonGenerator.get_secure_random_bytes(32)
else:
    integrity_key = None

# generate random data
data = generate_random_data(size)

print("starting encryption...")
encData = DataSample.encrypt_data(data, key, integrity_key, threads)
print("starting decryption...")
decData = DataSample.decrypt_data(encData, key, integrity_key, threads)
print("done")
