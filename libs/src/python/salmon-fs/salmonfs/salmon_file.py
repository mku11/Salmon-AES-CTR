#!/usr/bin/env python3
'''
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
'''
from __future__ import annotations
import os
from typing import Callable, Any

from typeguard import typechecked
from wrapt import synchronized

from convert.bit_converter import BitConverter
from file.ireal_file import IRealFile
from file.virtual_file import VirtualFile
from iostream.random_access_stream import RandomAccessStream
from salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon.integrity.salmon_integrity_exception import SalmonIntegrityException
from salmon.iostream.encryption_mode import EncryptionMode
from salmon.iostream.salmon_stream import SalmonStream
from salmon.salmon_generator import SalmonGenerator
from salmon.salmon_header import SalmonHeader
from salmon.salmon_security_exception import SalmonSecurityException
from salmon.text.salmon_text_decryptor import SalmonTextDecryptor
from salmon.text.salmon_text_encryptor import SalmonTextEncryptor
from salmonfs.salmon_drive import SalmonDrive


@typechecked
class SalmonFile(VirtualFile):
    """
     * A virtual file backed by an encrypted {@link IRealFile} on the real filesystem.
     * Supports operations for retrieving {@link SalmonStream} for reading/decrypting
     * and writing/encrypting contents.
    """

    separator: str = "/"

    def __init__(self, real_file: IRealFile, drive: SalmonDrive | None = None):
        """
         * Provides a file handle that can be used to create encrypted files.
         * Requires a virtual drive that supports the underlying filesystem, see PyFile implementation.
         *
         * @param drive    The file virtual system that will be used with file operations
         * @param real_file The real file
        """

        super().__init__(real_file, drive)
        self.__drive: SalmonDrive | None = None
        self.__realFile: IRealFile | None = None

        # cached values
        self.__baseName: str | None = None
        self.__header: SalmonHeader | None = None

        self.__overwrite: bool = False
        self.__integrity: bool = False
        self.__reqChunkSize: int | None = None
        self.__encryptionKey: bytearray | None = None
        self.__hash_key: bytearray | None = None
        self.__requestedNonce: bytearray | None = None
        self.__tag: object | None = None

        self.__drive = drive
        self.__realFile = real_file
        if self.__integrity:
            self.__reqChunkSize = drive.get_default_file_chunk_size()
        if drive is not None and drive.get_key() is not None:
            self.__hash_key = drive.get_key().get_hash_key()

    @synchronized
    def get_requested_chunk_size(self) -> int | None:
        """
         * Return the current chunk size requested that will be used for integrity
        """
        return self.__reqChunkSize

    def get_file_chunk_size(self) -> int | None:
        """
         * Get the file chunk size from the header.
         *
         * @return The chunk size.
         * @throws IOError Throws exceptions if the format is corrupt.
        """
        header: SalmonHeader | None = self.get_header()
        if header is None:
            return None
        return header.get_chunk_size()

    def get_header(self) -> SalmonHeader | None:
        """
         * Get the custom {@link SalmonHeader} from this file.
         *
         * @return
         * @throws IOError
        """
        if not self.exists():
            return None
        if self.__header is not None:
            return self.__header
        header: SalmonHeader = SalmonHeader()
        stream: RandomAccessStream | None = None
        try:
            stream = self.__realFile.get_input_stream()
            bytes_read: int = stream.read(header.get_magic_bytes(), 0, len(header.get_magic_bytes()))
            if bytes_read != len(header.get_magic_bytes()):
                return None
            buff: bytearray = bytearray(8)
            bytes_read = stream.read(buff, 0, SalmonGenerator.VERSION_LENGTH)
            if bytes_read != SalmonGenerator.VERSION_LENGTH:
                return None
            header.set_version(buff[0])
            bytes_read = stream.read(buff, 0, self.__get_chunk_size_length())
            if bytes_read != self.__get_chunk_size_length():
                return None
            header.set_chunk_size(BitConverter.to_long(buff, 0, bytes_read))
            header.set_nonce(bytearray(SalmonGenerator.NONCE_LENGTH))
            bytes_read = stream.read(header.get_nonce(), 0, SalmonGenerator.NONCE_LENGTH)
            if bytes_read != SalmonGenerator.NONCE_LENGTH:
                return None
        except Exception as ex:
            print(ex)
            raise IOError("Could not get file header") from ex
        finally:
            if stream is not None:
                stream.close()

        _header = header
        return header

    def get_input_stream(self) -> SalmonStream:
        """
         * Retrieves a SalmonStream that will be used for decrypting the file contents.
         *
         * @return
         * @throws IOError
         * @throws SalmonSecurityException
         * @throws SalmonIntegrityException
        """
        if not self.exists():
            raise IOError("File does not exist")

        real_stream: RandomAccessStream = self.__realFile.get_input_stream()
        real_stream.seek(SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH,
                         RandomAccessStream.SeekOrigin.Begin)

        file_chunk_size_bytes: bytearray = bytearray(self.__get_chunk_size_length())
        bytes_read: int = real_stream.read(file_chunk_size_bytes, 0, len(file_chunk_size_bytes))
        if bytes_read == 0:
            raise IOError("Could not parse chunks size from file header")
        chunk_size: int = BitConverter.to_long(file_chunk_size_bytes, 0, 4)
        if self.__integrity and chunk_size == 0:
            raise SalmonSecurityException("Cannot check integrity if file doesn't support it")

        nonce_bytes: bytearray = bytearray(SalmonGenerator.NONCE_LENGTH)
        iv_bytes_read: int = real_stream.read(nonce_bytes, 0, len(nonce_bytes))
        if iv_bytes_read == 0:
            raise IOError("Could not parse nonce from file header")

        real_stream.set_position(0)
        header_data: bytearray = bytearray(self.__get_header_length())
        real_stream.read(header_data, 0, len(header_data))

        stream: SalmonStream = SalmonStream(self.get_encryption_key(),
                                            nonce_bytes, EncryptionMode.Decrypt, real_stream, header_data,
                                            self.__integrity, self.get_file_chunk_size(), self.__get_hash_key())
        return stream

    @synchronized
    def get_output_stream(self, nonce: bytearray | None = None) -> SalmonStream:
        """
         * Get a {@link SalmonStream} for encrypting/writing contents to this file.
         *
         * @param nonce Nonce to be used for encryption. Note that each file should have
         *              a unique nonce see {@link SalmonDrive#getNextNonce()}.
         * @return The output stream.
         * @throws Exception
        """

        # check if we have an existing iv in the header
        nonce_bytes: bytearray = self.get_file_nonce()
        if nonce_bytes is not None and not self.__overwrite:
            raise SalmonSecurityException(
                "You should not overwrite existing files for security instead delete the existing file and create a "
                "new file. If this is a new file and you want to use parallel streams you can do "
                "this with set_allow_overwrite(True)")

        if nonce_bytes is None:
            self.__create_header(nonce)
        nonce_bytes = self.get_file_nonce()

        # we also get the header data to include in the hash
        header_data: bytearray = self.__get_real_file_header_data(self.__realFile)

        # create a stream with the file chunk size specified which will be used to host the integrity hash
        # we also specify if stream ranges can be overwritten which is generally dangerous if the file is existing
        # but practical if the file is brand new and multithreaded writes for performance need to be used.
        real_stream: RandomAccessStream = self.__realFile.get_output_stream()
        real_stream.seek(self.__get_header_length(), RandomAccessStream.SeekOrigin.Begin)

        stream: SalmonStream = SalmonStream(self.get_encryption_key(), nonce_bytes,
                                            EncryptionMode.Encrypt, real_stream, header_data,
                                            self.__integrity,
                                            self.get_requested_chunk_size() if self.get_requested_chunk_size() > 0
                                            else None,
                                            self.__get_hash_key())
        stream.set_allow_range_write(self.__overwrite)
        return stream

    def get_encryption_key(self) -> bytearray | None:
        """
         * Returns the current encryption key
        """
        if self.__encryptionKey is not None:
            return self.__encryptionKey
        if self.__drive is not None and self.__drive.get_key() is not None:
            return self.__drive.get_key().get_drive_key()
        return None

    def set_encryption_key(self, encryption_key: bytearray):
        """
         * Sets the encryption key
         *
         * @param encryption_key The AES encryption key to be used
        """
        self.__encryptionKey = encryption_key

    def __get_real_file_header_data(self, real_file: IRealFile) -> bytearray:
        """
         * Return the current header data that are stored in the file
         *
         * @param real_file The real file containing the data
        """
        real_stream: RandomAccessStream = self.__realFile.get_input_stream()
        header_data: bytearray = bytearray(self.__get_header_length())
        real_stream.read(header_data, 0, len(header_data))
        real_stream.close()
        return header_data

    def __get_hash_key(self) -> bytearray | None:
        """
         * Retrieve the current hash key that is used to encrypt / decrypt the file contents.
        """
        return self.__hash_key

    def set_verify_integrity(self, integrity: bool, hash_key: bytearray | None):

        """
         * Enabled verification of file integrity during read() and write()
         *
         * @param integrity True if enable integrity verification
         * @param hash_key   The hash key to be used for verification
        """
        if integrity and hash_key is None and self.__drive is not None:
            hash_key = self.__drive.get_key().get_hash_key()
        self.__integrity = integrity
        self.__hash_key = hash_key
        self.__reqChunkSize = self.get_file_chunk_size()

    def set_apply_integrity(self, integrity: bool, hash_key: bytearray | None, request_chunk_size: int | None):
        """
         * @param integrity
         * @param hash_key
         * @param request_chunk_size 0 use default file chunk.
         *                         A positive number to specify integrity chunks.
        """

        file_chunk_size: int | None = self.get_file_chunk_size()

        if file_chunk_size is not None:
            raise SalmonIntegrityException("Cannot redefine chunk size, delete file and recreate")
        if request_chunk_size is not None and request_chunk_size < 0:
            raise SalmonIntegrityException("Chunk size needs to be zero for default chunk size or a positive value")
        if integrity and file_chunk_size is not None and file_chunk_size == 0:
            raise SalmonIntegrityException(
                "Cannot enable integrity if the file is not created with integrity, export file "
                "and reimport with integrity")

        if integrity and hash_key is None and self.__drive is not None:
            hash_key = self.__drive.get_key().get_hash_key()
        self.__integrity = integrity
        self.__reqChunkSize = request_chunk_size
        if integrity and self.__reqChunkSize is None and self.__drive is not None:
            self.__reqChunkSize = self.__drive.get_default_file_chunk_size()
        self.__hash_key = hash_key

    def set_allow_overwrite(self, value: bool):
        """
         * Warning! Allow overwriting on a current stream. Overwriting is not a good idea because it will
         * re-use the same IV.
         * This is not recommended if you use the stream on storing files or generally data if prior version can
         * be inspected by others.
         * You should only use this setting for initial encryption with parallel streams and not for overwriting!
         *
         * @param value True to allow overwriting operations
        """
        self.__overwrite = value

    def __get_chunk_size_length(self) -> int:
        """
         * Returns the file chunk size
        """
        return SalmonGenerator.CHUNK_SIZE_LENGTH

    def __get_header_length(self) -> int:
        """
         * Returns the length of the header in bytes
        """
        return SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH + self.__get_chunk_size_length() \
            + SalmonGenerator.NONCE_LENGTH

    def get_file_nonce(self) -> bytearray | None:
        """
         * Returns the initial vector that is used for encryption / decryption
        """
        header: SalmonHeader | None = self.get_header()
        if header is None:
            return None
        return self.get_header().get_nonce()

    def set_requested_nonce(self, nonce: bytearray):
        """
         * Set the nonce for encryption/decryption for this file.
         *
         * @param nonce Nonce to be used.
         * @throws SalmonSecurityException
        """
        if self.__drive is not None:
            raise SalmonSecurityException("Nonce is already set by the drive")
        self.__requestedNonce = nonce

    def get_requested_nonce(self) -> bytearray:
        """
         * Get the nonce that is used for encryption/decryption of this file.
         *
         * @return
        """
        return self.__requestedNonce

    def __create_header(self, nonce: bytearray | None):
        """
         * Create the header for the file
        """
        # set it to zero (disabled integrity) or get the default chunk
        # size defined by the drive
        if self.__integrity and self.__reqChunkSize is None and self.__drive is not None:
            self.__reqChunkSize = self.__drive.get_default_file_chunk_size()
        elif not self.__integrity:
            self.__reqChunkSize = 0
        if self.__reqChunkSize is None:
            raise SalmonIntegrityException("File requires a chunk size")

        if nonce is not None:
            self.__requestedNonce = nonce
        elif self.__requestedNonce is None and self.__drive is not None:
            self.__requestedNonce = self.__drive.get_next_nonce()

        if self.__requestedNonce is None:
            raise SalmonSecurityException("File requires a nonce")

        real_stream: RandomAccessStream = self.__realFile.get_output_stream()
        magic_bytes: bytearray = SalmonGenerator.get_magic_bytes()
        real_stream.write(magic_bytes, 0, len(magic_bytes))

        version: int = SalmonGenerator.get_version()
        real_stream.write(bytearray([version]), 0, SalmonGenerator.VERSION_LENGTH)

        chunk_size_bytes: bytearray = BitConverter.to_bytes(self.__reqChunkSize, 4)
        real_stream.write(chunk_size_bytes, 0, len(chunk_size_bytes))

        real_stream.write(self.__requestedNonce, 0, len(self.__requestedNonce))

        real_stream.flush()
        real_stream.close()

    def get_block_size(self) -> int:
        """
         * Return the AES block size for encryption / decryption
        """
        return SalmonGenerator.BLOCK_SIZE

    def get_children_count(self) -> int:
        """
         * Get the count of files and subdirectories
         *
         * @return
        """
        return self.__realFile.get_children_count()

    def list_files(self) -> [SalmonFile]:
        """
         * Lists files and directories under this directory
        """
        files: [IRealFile] = self.__realFile.list_files()
        salmon_files: [SalmonFile] = []
        for iRealFile in files:
            file: SalmonFile = SalmonFile(iRealFile, self.__drive)
            salmon_files.append(file)

        return salmon_files

    def get_child(self, filename: str) -> SalmonFile | None:
        """
         * Get a child with this filename.
         *
         * @param filename The filename to search for
         * @return
         * @throws SalmonSecurityException
         * @throws SalmonIntegrityException
         * @throws IOError
         * @throws SalmonAuthException
        """
        files: [SalmonFile] = self.list_files()
        for file in files:
            if file.get_base_name() == filename:
                return file
        return None

    def create_directory(self, dir_name: str, key: bytearray | None = None,
                         dir_name_nonce: bytearray | None = None) -> SalmonFile:
        """
         * Creates a directory under this directory
         *
         * @param dir_name      The name of the directory to be created
         * @param key          The key that will be used to encrypt the directory name
         * @param dir_name_nonce The nonce to be used for encrypting the directory name
        """
        if self.__drive is None:
            raise SalmonSecurityException("Need to pass the key and dir_name_nonce nonce if not using a drive")

        encrypted_dir_name: str = self._get_encrypted_filename(dir_name, key, dir_name_nonce)
        real_dir: IRealFile = self.__realFile.create_directory(encrypted_dir_name)
        return SalmonFile(real_dir, self.__drive)

    def get_real_file(self) -> IRealFile:
        """
         * Return the real file
        """
        return self.__realFile

    def is_file(self) -> bool:
        """
         * Returns True if this is a file
        """
        return self.__realFile.is_file()

    def is_directory(self) -> bool:
        """
         * Returns True if this is a directory
        """
        return self.__realFile.is_directory()

    def get_path(self) -> str:
        """
         * Return the path of the real file stored
        """
        real_path: str = self.__realFile.get_absolute_path()
        return self.__get_path(real_path)

    def __get_path(self, real_path: str) -> str:
        """
         * Returns the virtual path for the drive and the file provided
         *
         * @param real_path The path of the real file
        """
        relative_path: str = self.__get_relative_path(real_path)
        path: str = ""
        # TODO: test if char is escaped
        parts: [str] = relative_path.split(os.sep)
        for part in parts:
            if part != "":
                path += SalmonFile.separator
                path += self.__get_decrypted_filename(part)

        return path

    def get_real_path(self) -> str:
        """
         * Return the path of the real file
        """
        return self.__realFile.get_path()

    def __get_relative_path(self, real_path: str) -> str:
        """
         * Return the virtual relative path of the file belonging to a drive
         *
         * @param real_path The path of the real file
        """
        virtual_root: VirtualFile = self.__drive.get_virtual_root()
        virtual_root_path: str = virtual_root.get_real_file().get_absolute_path()
        if real_path.startswith(virtual_root_path):
            return real_path.replace(virtual_root_path, "")

        return real_path

    def get_base_name(self) -> str:
        """
         * Returns the basename for the file
        """
        if self.__baseName is not None:
            return self.__baseName
        if self.__drive is not None and self.get_real_path() == self.__drive.get_virtual_root().get_real_path():
            return ""

        real_base_name: str = self.__realFile.get_base_name()
        _baseName = self.__get_decrypted_filename(real_base_name)
        return _baseName

    def get_parent(self) -> SalmonFile | None:
        """
         * Returns the virtual parent directory
        """
        try:
            if self.__drive is None or self.__drive.get_virtual_root().get_real_file().get_path() == \
                    self.get_real_file().get_path():
                return None
        except Exception as exception:
            print(exception)
            return None

        real_dir: IRealFile = self.__realFile.get_parent()
        v_dir: SalmonFile = SalmonFile(real_dir, self.__drive)
        return v_dir

    def delete(self):
        """
         * Delete this file.
        """
        self.__realFile.delete()

    def mkdir(self):
        """
         * Create this directory. Currently Not Supported
        """
        raise NotImplemented()

    def get_last_date_time_modified(self) -> int:
        """
         * Returns the last date modified in milliseconds
        """
        return self.__realFile.last_modified()

    def get_size(self) -> int:
        """
         * Return the virtual size of the file excluding the header and hash signatures.
        """
        r_size: int = self.__realFile.length()
        if r_size == 0:
            return r_size
        return r_size - self.__get_header_length() - self.__get_hash_total_bytes_length()

    def __get_hash_total_bytes_length(self) -> int:
        """
         * Returns the hash total bytes occupied by signatures
        """
        # file does not support integrity
        if self.get_file_chunk_size() is None or self.get_file_chunk_size() <= 0:
            return 0

        # integrity has been requested but hash is missing
        if self.__integrity and self.__get_hash_key() is None:
            raise SalmonIntegrityException("File requires hash_key, use SetVerifyIntegrity() to provide one")

        return SalmonIntegrity.get_total_hash_data_length(self.__realFile.length(), self.get_file_chunk_size(),
                                                      SalmonGenerator.HASH_RESULT_LENGTH,
                                                      SalmonGenerator.HASH_KEY_LENGTH)

    # TODO: files with real same name can exists we can add checking all files in the dir
    #  and throw an Exception though this could be an expensive operation
    def create_file(self, real_filename: str, key: bytearray | None = None, file_name_nonce: bytearray | None = None,
                    file_nonce: bytearray | None = None) -> SalmonFile:
        """
         * Create a file under this directory
         *
         * @param real_filename  The real file name of the file (encrypted)
         * @param key           The key that will be used for encryption
         * @param file_name_nonce The nonce for the encrypting the filename
         * @param file_nonce     The nonce for the encrypting the file contents
        """

        if self.__drive is None and (key is None or file_name_nonce is None or file_nonce is None):
            raise SalmonSecurityException("Need to pass the key, filename nonce, and file nonce if not using a drive")

        encrypted_filename: str = self._get_encrypted_filename(real_filename, key, file_name_nonce)
        file: IRealFile = self.__realFile.create_file(encrypted_filename)
        salmon_file: SalmonFile = SalmonFile(file, self.__drive)
        salmon_file.set_encryption_key(key)
        salmon_file.__integrity = self.__integrity
        if self.__drive is not None and (file_nonce is not None or file_name_nonce is not None):
            SalmonSecurityException("Nonce is already set by the drive")
        if self.__drive is not None and key is not None:
            SalmonSecurityException("Key is already set by the drive")
        salmon_file.__requestedNonce = file_nonce
        return salmon_file

    def rename(self, new_filename: str, nonce: bytearray = None):
        """
         * Rename the virtual file name
         *
         * @param new_filename The new filename this file will be renamed to
         * @param nonce       The nonce to use
        """

        if self.__drive is None and (self.__encryptionKey is None or self.__requestedNonce is None):
            raise SalmonSecurityException("Need to pass a nonce if not using a drive")
        self.rename(new_filename, None)

        new_encrypted_filename: str = self._get_encrypted_filename(new_filename, None, nonce)
        self.__realFile.rename_to(new_encrypted_filename)
        _baseName = None

    def exists(self) -> bool:
        """
         * Returns True if this file exists
        """
        if self.__realFile is None:
            return False
        return self.__realFile.exists()

    def __get_decrypted_filename(self, filename: str) -> str:
        """
         * Return the decrypted filename of a real filename
         *
         * @param filename The filename of a real file
        """
        if self.__drive is None and (self.__encryptionKey is None or self.__requestedNonce is None):
            SalmonSecurityException("Need to use a drive or pass key and nonce")
        return self._get_decrypted_filename(filename, None, None)

    def _get_decrypted_filename(self, filename: str, key: bytearray | None, nonce: bytearray | None) -> str:
        """
         * Return the decrypted filename of a real filename
         *
         * @param filename The filename of a real file
         * @param key      The encryption key if the file doesn't belong to a drive
         * @param nonce    The nonce if the file doesn't belong to a drive
        """
        rfilename: str = filename.replace("-", "/")
        if self.__drive is not None and nonce is not None:
            SalmonSecurityException("Filename nonce is already set by the drive")
        if self.__drive is not None and key is not None:
            SalmonSecurityException("Key is already set by the drive")

        if key is None:
            key = self.__encryptionKey
        if key is None and self.__drive is not None:
            key = self.__drive.get_key().get_drive_key()
        decfilename: str = SalmonTextDecryptor.decrypt_string(rfilename, key, nonce, True)
        return decfilename

    def _get_encrypted_filename(self, filename: str, key: bytearray | None, nonce: bytearray) -> str:
        """
         * Return the encrypted filename of a virtual filename
         *
         * @param filename The virtual filename
         * @param key      The encryption key if the file doesn't belong to a drive
         * @param nonce    The nonce if the file doesn't belong to a drive
        """
        if self.__drive is not None and nonce is not None:
            raise SalmonSecurityException("Filename nonce is already set by the drive")
        if self.__drive is not None:
            nonce = self.__drive.get_next_nonce()
        if self.__drive is not None and key is not None:
            raise SalmonSecurityException("Key is already set by the drive")
        if self.__drive is not None:
            key = self.__drive.get_key().get_drive_key()
        encrypted_path: str = SalmonTextEncryptor.encrypt_string(filename, key, nonce, True)
        encrypted_path = encrypted_path.replace("/", "-")
        return encrypted_path

    def get_drive(self) -> SalmonDrive:
        """
         * Get the drive.
         *
         * @return
        """
        return self.__drive

    def set_tag(self, tag: object):
        """
         * Set the tag for this file.
         *
         * @param tag
        """
        self.__tag = tag

    def get_tag(self) -> object:
        """
         * Get the file tag.
         *
         * @return The file tag.
        """
        return self.__tag

    def move(self, v_dir: SalmonFile, on_progress_listener: RandomAccessStream.OnProgressListener = None) -> SalmonFile:
        """
         * Move file to another directory.
         *
         * @param dir                Target directory.
         * @param on_progress_listener Observer to notify when move progress changes.
         * @return
         * @throws IOError
        """
        new_real_file: IRealFile = self.__realFile.move(v_dir.__realFile, None, on_progress_listener)
        return SalmonFile(new_real_file, self.__drive)

    def copy(self, v_dir: SalmonFile, on_progress_listener: RandomAccessStream.OnProgressListener = None) -> SalmonFile:
        """
         * Copy a file to another directory.
         *
         * @param dir                Target directory.
         * @param on_progress_listener Observer to notify when copy progress changes.
         * @return
         * @throws IOError
        """
        new_real_file: IRealFile = self.__realFile.copy(v_dir.__realFile, None, on_progress_listener)
        return SalmonFile(new_real_file, self.__drive)

    def copy_recursively(self, dest: SalmonFile,
                         progress_listener: Callable[[SalmonFile, int, int], Any],
                         auto_rename: Callable[[SalmonFile], str],
                         auto_rename_folders: bool,
                         on_failed: Callable[[SalmonFile, Exception], Any]):
        """
         * Copy a directory recursively
         *
         * @param dest
         * @param progress_listener
         * @param auto_rename
         * @param on_failed
        """
        on_failed_real_file: Callable[[IRealFile, Exception], Any] | None = None
        if on_failed is not None:
            on_failed_real_file = lambda file, ex: \
                on_failed(SalmonFile(file, self.get_drive()), ex)

        rename_real_file: Callable[[IRealFile], str] | None = None
        # use auto rename only when we are using a drive
        if auto_rename is not None and self.get_drive() is not None:
            rename_real_file = lambda file: self.__delegate_rename(file, auto_rename)

        self.__realFile.copy_recursively(dest.__realFile,
                                         lambda file, position, length, progress_listener1:
                                         self.__notify_progress(file, position, length, progress_listener1),
                                         rename_real_file, auto_rename_folders, on_failed_real_file)

    def move_recursively(self, dest: SalmonFile,
                         progress_listener: Callable[[SalmonFile, int, int], Any],
                         auto_rename: Callable[[SalmonFile], str],
                         auto_rename_folders: bool,
                         on_failed: Callable[[SalmonFile, Exception], Any]):
        """
         * Move a directory recursively
         *
         * @param dest
         * @param progress_listener
         * @param auto_rename
         * @param on_failed
        """

        on_failed_real_file: Callable[[IRealFile, Exception], Any] | None = None
        if on_failed is not None:
            on_failed_real_file = lambda file, ex: self.__notify_failed(file, ex, on_failed)

        rename_real_file: Callable[[IRealFile], str] | None = None
        # use auto rename only when we are using a drive
        if auto_rename is not None and self.get_drive() is not None:
            rename_real_file = lambda file: self.__delegate_rename(file, auto_rename)

        self.__realFile.move_recursively(dest.get_real_file(),
                                         lambda file, position, length:
                                         self.__notify_progress(file, position, length, progress_listener),
                                         rename_real_file, auto_rename_folders, on_failed_real_file)

    def delete_recursively(self, progress_listener: Callable[[SalmonFile, int, int], Any],
                           on_failed: Callable[[SalmonFile, Exception], Any]):
        on_failed_real_file: Callable[[IRealFile, Exception], Any] | None = None
        if on_failed is not None:
            on_failed_real_file = lambda file, ex: self.__notify_failed(file, ex, on_failed)

        self.get_real_file().delete_recursively(
            lambda file, position, length: self.__notify_progress(file, position, length, progress_listener),
            on_failed_real_file)

    @staticmethod
    def auto_rename(file: SalmonFile) -> str:
        try:
            return SalmonFile.auto_rename_file(file)
        except Exception as ex:
            try:
                return file.get_base_name()
            except Exception as ex1:
                return ""

    @staticmethod
    def auto_rename_file(file: SalmonFile) -> str:
        """
        <summary>
        Get an auto generated copy of the name for the file.
        </summary>
        <param name="file"></param>
        <returns></returns>
        """
        filename: str = IRealFile.auto_rename(file.get_base_name())
        nonce: bytearray = file.get_drive().get_next_nonce()
        key: bytearray = file.get_drive().get_key().get_drive_key()
        encrypted_path: str = SalmonTextEncryptor.encryptstr(filename, key, nonce, True)
        encrypted_path = encrypted_path.replace("/", "-")
        return encrypted_path

    def __notify_progress(self, file: IRealFile, position, length,
                          progress_listener: Callable[[SalmonFile, int, int], Any]):
        if progress_listener is not None:
            progress_listener(SalmonFile(file, self.__drive), position, length)

    def __delegate_rename(self, file: IRealFile, auto_rename: Callable[[SalmonFile], str]):
        try:
            return auto_rename(SalmonFile(file, self.get_drive()))
        except Exception as e:
            return file.get_base_name()

    def __notify_failed(self, file: IRealFile, ex: Exception,
                        on_failed: Callable[[SalmonFile, Exception], Any] | None = None):
        if on_failed is not None:
            on_failed(SalmonFile(file, self.get_drive()), ex)
