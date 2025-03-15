#!/usr/bin/env python3

# Make sure you run with -O option to disable type checks during runtime
# python -O file.py

from salmon_core.salmon.generator import Generator
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.streams.aes_stream import ProviderType
from salmon_fs.fs.file.ifile import IFile
from salmon_fs.fs.file.file import File

from samples.file_sample import FileSample
from samples.samples_common import get_key_from_password

password = "test123"
text = "This is a plain text that will be encrypted"
integrity = True

AesStream.set_aes_provider_type(ProviderType.Default)

# generate an encryption key from the text password
key = get_key_from_password(password)

# enable integrity (optional)
if integrity:
    # generate an HMAC key
    integrity_key = Generator.get_secure_random_bytes(32)
else:
    integrity_key = None

v_dir: IFile = File("./output")
if not v_dir.exists():
    v_dir.mkdir()
file = v_dir.get_child("data.dat")
if file.exists():
    file.delete()

FileSample.encrypt_text_to_file(text, key, integrity_key, file)
dec_text = FileSample.decrypt_text_from_file(key, integrity_key, file)
