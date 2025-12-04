#!/usr/bin/env python3
from simple_io.convert.bit_converter import BitConverter
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.streams.aes_stream import AesStream
from simple_io.streams.memory_stream import MemoryStream
from simple_io.streams.random_access_stream import RandomAccessStream


class DataStreamSample:
    BUFFER_SIZE = 256 * 1024  # recommended buffer size aligned to internal buffers

    @staticmethod
    def encrypt_data_stream(data, key, nonce):
        print("Encrypting bytes: " + BitConverter.to_hex(data[0:24]) + "...")

        # we use a memory stream to host the encrypted data
        memory_stream: MemoryStream = MemoryStream()

        # and wrap it with a AesStream that will do the encryption
        enc_stream = AesStream(key, nonce, EncryptionMode.Encrypt, memory_stream)

        # now write the data you want to decrypt
        # it is recommended to use a large enough buffer while writing the data
        # for better performance
        total_bytes_written = 0
        while total_bytes_written < len(data):
            length = min(len(data) - total_bytes_written, DataStreamSample.BUFFER_SIZE)
            enc_stream.write(data, total_bytes_written, length)
            total_bytes_written += length
        enc_stream.flush()

        # the encrypted data are now written to the memory_stream/enc_data.
        enc_stream.close()
        enc_data: bytearray = memory_stream.to_array()
        memory_stream.close()

        print("Bytes encrypted: " + BitConverter.to_hex(enc_data[0:24]) + "...")
        return enc_data

    @staticmethod
    def decrypt_data_stream(data, key, nonce):
        print("Decrypting bytes: " + BitConverter.to_hex(data[0:24]) + "...")

        # we use a stream that contains the encrypted data
        memory_stream: MemoryStream = MemoryStream(data)

        # and wrap it with a salmon stream to do the decryption
        dec_stream: RandomAccessStream = AesStream(key, nonce, EncryptionMode.Decrypt, memory_stream)

        # decrypt the data
        dec_data = bytearray(dec_stream.get_length())
        total_bytes_read = 0
        while (bytes_read := dec_stream.read(dec_data, total_bytes_read, DataStreamSample.BUFFER_SIZE)) > 0:
            total_bytes_read += bytes_read

        dec_stream.close()
        memory_stream.close()

        print("Bytes decrypted: " + BitConverter.to_hex(dec_data[0:24]) + "...")
        return dec_data
