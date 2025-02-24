#!/usr/bin/env python3
import random

from salmon_core.salmon.password.salmon_password import SalmonPassword
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_core.streams.memory_stream import MemoryStream


# create an encryption key from a text password
def get_key_from_password(password):
    # generate a salt
    salt = SalmonGenerator.get_secure_random_bytes(24)
    # make sure the iterations are a large enough number
    iterations = 60000

    # generate a 256bit key from the text password
    key = SalmonPassword.get_key_from_password(password, salt, iterations, 32)
    return key


def generate_random_data(size):
    memory_stream: MemoryStream = MemoryStream()
    while size > 0:
        buffer = random.randbytes(65536)
        length = min(size, len(buffer))
        memory_stream.write(bytearray(buffer), 0, length)
        size -= length

    memory_stream.flush()
    memory_stream.close()
    return memory_stream.to_array()
