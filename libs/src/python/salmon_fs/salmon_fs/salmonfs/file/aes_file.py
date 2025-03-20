#!/usr/bin/env python3
"""!@brief A virtual file backed by an encrypted file.
"""

from __future__ import annotations

__license__ = """
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
"""

from typing import Callable, Any
import sys
from typeguard import typechecked
from wrapt import synchronized

from salmon_core.convert.bit_converter import BitConverter
from salmon_fs.fs.file.ifile import IFile
from salmon_fs.fs.file.ivirtual_file import IVirtualFile
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.salmon.integrity.integrity import Integrity
from salmon_core.salmon.integrity.integrity_exception import IntegrityException
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.streams.encryption_format import EncryptionFormat
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.generator import Generator
from salmon_core.salmon.header import Header
from salmon_core.salmon.security_exception import SecurityException
from salmon_core.salmon.text.text_decryptor import TextDecryptor
from salmon_core.salmon.text.text_encryptor import TextEncryptor
from salmon_fs.salmonfs.drive.aes_drive import AesDrive


@typechecked
class AesFile(IVirtualFile):
    """!
    A virtual file backed by an encrypted file.
    Supports operations for retrieving {@link AesStream} for reading/decrypting
    and writing/encrypting contents.
    """

    separator: str = "/"
    """
    The directory separator
    """

    def __init__(self, real_file: IFile, drive: AesDrive | None = None,
                 enc_format: EncryptionFormat = EncryptionFormat.Salmon):
        """!
        Provides a file handle that can be used to create encrypted files.
        Requires a virtual drive that supports the underlying filesystem, see PyFile implementation.
        
        @param drive:    The file virtual system that will be used with file operations
        @param real_file: The real file
        """

        super().__init__(real_file, drive)
        self.__drive: AesDrive | None = None
        self.__format: EncryptionFormat = EncryptionFormat.Salmon
        self.__real_file: IFile | None = None

        # cached values
        self.__baseName: str | None = None
        self.__header: Header | None = None

        self.__overwrite: bool = False
        self.__integrity: bool = False
        self.__req_chunk_size: int = 0
        self.__encryptionKey: bytearray | None = None
        self.__hash_key: bytearray | None = None
        self.__requested_nonce: bytearray | None = None
        self.__tag: object | None = None

        self.__real_file = real_file
        self.__drive = drive
        self.__format = enc_format
        if drive and drive.get_key():
            self.__hash_key = drive.get_key().get_hash_key()

    @synchronized
    def get_requested_chunk_size(self) -> int:
        """!
        Return the current chunk size requested that will be used for integrity
        """
        return self.__req_chunk_size

    def get_file_chunk_size(self) -> int:
        """!
        Get the file chunk size from the header.
        
        @returns The chunk size.
        @exception IOError: Throws exceptions if the format is corrupt.
        """
        header: Header | None = self.get_header()
        if header is None:
            return 0
        return header.get_chunk_size()

    def get_header(self) -> Header | None:
        """!
        Get the custom {@link SalmonHeader} from this file.
        
        @returns The header
        @exception IOError: Thrown if there is an IO error.
        """
        if not self.exists():
            return None
        if self.__header:
            return self.__header
        header: Header | None = Header(bytearray())
        stream: RandomAccessStream | None = None
        try:
            stream = self.__real_file.get_input_stream()
            header = Header.read_header_data(stream)
        except Exception as ex:
            print(ex, file=sys.stderr)
            raise IOError("Could not get file header") from ex
        finally:
            if stream:
                stream.close()

        _header = header
        return header

    def get_input_stream(self) -> AesStream:
        """!
        Retrieves a AesStream that will be used for decrypting the file contents.
        
        @returns The stream
        @exception IOError: Thrown if there is an IO error.
        @exception IntegrityException: Thrown when security error
        @exception IntegrityException: Thrown when data are corrupt or tampered with.
        """
        if not self.exists():
            raise IOError("File does not exist")

        real_stream: RandomAccessStream = self.__real_file.get_input_stream()
        real_stream.seek(Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH,
                         RandomAccessStream.SeekOrigin.Begin)

        file_chunk_size_bytes: bytearray = bytearray(self.__get_chunk_size_length())
        bytes_read: int = real_stream.read(file_chunk_size_bytes, 0, len(file_chunk_size_bytes))
        if bytes_read == 0:
            raise IOError("Could not parse chunks size from file header")
        chunk_size: int = BitConverter.to_long(file_chunk_size_bytes, 0, 4)
        if self.__integrity and chunk_size == 0:
            raise SecurityException("Cannot check integrity if file doesn't support it")

        nonce_bytes: bytearray = bytearray(Generator.NONCE_LENGTH)
        iv_bytes_read: int = real_stream.read(nonce_bytes, 0, len(nonce_bytes))
        if iv_bytes_read == 0:
            raise IOError("Could not parse nonce from file header")

        real_stream.set_position(0)
        header_data: bytearray = bytearray(self.__get_header_length())
        real_stream.read(header_data, 0, len(header_data))

        stream: AesStream = AesStream(self.get_encryption_key(),
                                      nonce_bytes, EncryptionMode.Decrypt, real_stream, self.__format,
                                      self.__integrity, self.get_hash_key(), self.get_file_chunk_size())
        return stream

    @synchronized
    def get_output_stream(self, nonce: bytearray | None = None) -> AesStream:
        """!
        Get a {@link AesStream} for encrypting/writing contents to this file.
        
        @param nonce: Nonce to be used for encryption. Note that each file should have
                     a unique nonce see {@link AesDrive#getNextNonce()}.
        @returns The output stream.
        @exception Exception:         """

        # check if we have an existing iv in the header
        header: Header = self.get_header()
        nonce_bytes: bytearray | None = None
        if header:
            nonce_bytes = self.get_file_nonce()

        if nonce_bytes and not self.__overwrite:
            raise SecurityException(
                "You should not overwrite existing files for security instead delete the existing file and create a "
                "new file. If this is a new file and you want to use parallel streams call set_allow_overwrite(True)")

        if nonce_bytes is None:
            # set it to zero (disabled integrity) or get the default chunk
            # size defined by the drive
            if self.__integrity and self.__req_chunk_size == 0 and self.__drive:
                self.__req_chunk_size = self.__drive.get_default_file_chunk_size()
            elif not self.__integrity:
                self.__req_chunk_size = 0

            if nonce:
                self.__requested_nonce = nonce
            elif self.__requested_nonce is None and self.__drive:
                self.__requested_nonce = self.__drive.get_next_nonce()

            if not self.__requested_nonce:
                raise SecurityException("File requires a nonce")

            nonce_bytes = self.__requested_nonce

        # create a stream with the file chunk size specified which will be used to host the integrity hash
        # we also specify if stream ranges can be overwritten which is generally dangerous if the file is existing
        # but practical if the file is brand new and multithreaded writes for performance need to be used.
        real_stream: RandomAccessStream = self.__real_file.get_output_stream()

        key: bytearray | None = self.get_encryption_key()
        if not key:
            raise IOError("Set an encryption key to the file first")
        if not nonce_bytes:
            raise IOError("No nonce provided and no nonce found in file")
        stream: AesStream = AesStream(self.get_encryption_key(), nonce_bytes,
                                      EncryptionMode.Encrypt, real_stream, self.__format,
                                      self.__integrity,
                                      self.get_hash_key(),
                                      self.get_requested_chunk_size())
        stream.set_allow_range_write(self.__overwrite)
        return stream

    def get_encryption_key(self) -> bytearray | None:
        """!
        Returns the current encryption key
        """
        if self.__encryptionKey:
            return self.__encryptionKey
        if self.__drive and self.__drive.get_key():
            return self.__drive.get_key().get_drive_key()
        return None

    def set_encryption_key(self, encryption_key: bytearray | None):
        """!
        Sets the encryption key
        
        @param encryption_key: The AES encryption key to be used
        """
        self.__encryptionKey = encryption_key

    def __get_real_file_header_data(self, real_file: IFile) -> bytearray:
        """!
        Return the current header data that are stored in the file
        
        @param real_file: The real file containing the data
        """
        real_stream: RandomAccessStream = self.__real_file.get_input_stream()
        header_data: bytearray = bytearray(self.__get_header_length())
        real_stream.read(header_data, 0, len(header_data))
        real_stream.close()
        return header_data

    def get_hash_key(self) -> bytearray | None:
        """!
        Retrieve the current hash key that is used to encrypt / decrypt the file contents.
        """
        return self.__hash_key

    def set_verify_integrity(self, integrity: bool, hash_key: bytearray | None = None):

        """
        Enabled verification of file integrity during read() and write()
        
        @param integrity: True if enable integrity verification
        @param hash_key:   The hash key to be used for verification
        """
        header: Header = self.get_header()
        if not header and integrity:
            raise IntegrityException("File does not support integrity")

        if integrity and hash_key is None and self.__drive:
            hash_key = self.__drive.get_key().get_hash_key()
        self.__integrity = integrity
        self.__hash_key = hash_key
        self.__req_chunk_size = header.get_chunk_size()

    def set_apply_integrity(self, integrity: bool, hash_key: bytearray | None = None,
                            request_chunk_size: int = 0):
        """!
        Apply integrity when writing to file
        @param integrity: True to apply integrity
        @param hash_key: The hash key
        @param request_chunk_size: 0 use default file chunk. A positive number to specify integrity chunks.
        """
        if not hash_key:
            hash_key = self.__hash_key

        header: Header | None = self.get_header()
        if header and header.get_chunk_size() > 0 and not self.__overwrite:
            raise IntegrityException("Cannot redefine chunk size")

        if request_chunk_size < 0:
            raise IntegrityException("Chunk size needs to be zero for default chunk size or a positive value")

        if integrity and hash_key is None and self.__drive:
            hash_key = self.__drive.get_key().get_hash_key()

        if integrity and not hash_key:
            raise SecurityException("Integrity needs a hashKey")

        self.__integrity = integrity
        self.__req_chunk_size = request_chunk_size
        if integrity and self.__req_chunk_size is None and self.__drive:
            self.__req_chunk_size = self.__drive.get_default_file_chunk_size()
        self.__hash_key = hash_key

    def set_allow_overwrite(self, value: bool):
        """!
        Warning! Allow overwriting on a current stream. Overwriting is not a good idea because it will
        re-use the same IV.
        This is not recommended if you use the stream on storing files or generally data if prior version can
        be inspected by others.
        You should only use this setting for initial encryption with parallel streams and not for overwriting!
        
        @param value: True to allow overwriting operations
        """
        self.__overwrite = value

    def __get_chunk_size_length(self) -> int:
        """!
        Returns the file chunk size
        """
        return Generator.CHUNK_SIZE_LENGTH

    def __get_header_length(self) -> int:
        """!
        Returns the length of the header in bytes
        """
        return Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH + self.__get_chunk_size_length() \
            + Generator.NONCE_LENGTH

    def get_file_nonce(self) -> bytearray | None:
        """!
        Returns the initial vector that is used for encryption / decryption
        """
        header: Header | None = self.get_header()
        if header is None:
            return None
        return self.get_header().get_nonce()

    def set_requested_nonce(self, nonce: bytearray):
        """!
        Set the nonce for encryption/decryption for this file.
        
        @param nonce: Nonce to be used.
        @exception IntegrityException: Thrown when security error
        """
        if self.__drive:
            raise SecurityException("Nonce is already set by the drive")
        self.__requested_nonce = nonce

    def get_requested_nonce(self) -> bytearray | None:
        """!
        Get the nonce that is used for encryption/decryption of this file.
        
        @returns The requested nonce
        """
        return self.__requested_nonce

    def get_block_size(self) -> int:
        """!
        Return the AES block size for encryption / decryption
        """
        return Generator.BLOCK_SIZE

    def get_children_count(self) -> int:
        """!
        Get the count of files and subdirectories
        
        @returns The children count
        """
        return self.__real_file.get_children_count()

    def list_files(self) -> list[AesFile]:
        """!
        Lists files and directories under this directory
        """
        files: list[IFile] = self.__real_file.list_files()
        salmon_files: list[AesFile] = []
        for iRealFile in files:
            file: AesFile = AesFile(iRealFile, self.__drive)
            salmon_files.append(file)

        return salmon_files

    def get_child(self, filename: str) -> AesFile | None:
        """!
        Get a child with this filename.
        
        @param filename: The filename to search for
        @returns The child file
        @exception IntegrityException: Thrown when security error
        @exception IntegrityException: Thrown when data are corrupt or tampered with.
        @exception IOError: Thrown if there is an IO error.
        @exception AuthException: Thrown when there is a failure in the nonce sequencer.
        """
        files: list[AesFile] = self.list_files()
        for file in files:
            if file.get_name() == filename:
                return file
        return None

    def create_directory(self, dir_name: str, key: bytearray | None = None,
                         dir_name_nonce: bytearray | None = None) -> AesFile:
        """!
        Creates a directory under this directory
        
        @param dir_name:      The name of the directory to be created
        @param key:          The key that will be used to encrypt the directory name
        @param dir_name_nonce: The nonce to be used for encrypting the directory name
        """
        if self.__drive is None:
            raise SecurityException("Need to pass the key and dir_name_nonce nonce if not using a drive")

        encrypted_dir_name: str = self._get_encrypted_filename(dir_name, key, dir_name_nonce)
        real_dir: IFile = self.__real_file.create_directory(encrypted_dir_name)
        return AesFile(real_dir, self.__drive)

    def get_real_file(self) -> IFile:
        """!
        Return the real file
        """
        return self.__real_file

    def is_file(self) -> bool:
        """!
        Returns True if this is a file
        """
        return self.__real_file.is_file()

    def is_directory(self) -> bool:
        """!
        Returns True if this is a directory
        """
        return self.__real_file.is_directory()

    def get_path(self) -> str:
        """!
        Return the path of the real file stored
        """
        real_path: str = self.__real_file.get_display_path()
        return self.__get_path(real_path)

    def __get_path(self, real_path: str) -> str:
        """!
        Returns the virtual path for the drive and the file provided
        
        @param real_path: The path of the real file
        """
        relative_path: str = self.__get_relative_path(real_path)
        path: str = ""
        parts: list[str] = relative_path.split("\\|/")
        for part in parts:
            if part != "":
                path += AesFile.separator
                path += self.__get_decrypted_filename(part)

        return path

    def get_real_path(self) -> str:
        """!
        Return the path of the real file
        """
        return self.__real_file.get_path()

    def __get_relative_path(self, real_path: str) -> str:
        """!
        Return the virtual relative path of the file belonging to a drive
        
        @param real_path: The path of the real file
        """
        if not self.__drive:
            return self.get_real_file().get_name()
        virtual_root: IVirtualFile = self.__drive.get_root()
        virtual_root_path: str = virtual_root.get_real_file().get_display_path()
        if real_path.startswith(virtual_root_path):
            return real_path.replace(virtual_root_path, "")

        return real_path

    def get_name(self) -> str:
        """!
        Returns the basename for the file
        """
        if self.__baseName:
            return self.__baseName
        if self.__drive and self.get_real_path() == self.__drive.get_root().get_real_path():
            return ""

        real_base_name: str = self.__real_file.get_name()
        _baseName = self.__get_decrypted_filename(real_base_name)
        return _baseName

    def get_parent(self) -> AesFile | None:
        """!
        Returns the virtual parent directory
        """
        try:
            if self.__drive is None or self.__drive.get_root().get_real_file().get_path() == \
                    self.get_real_file().get_path():
                return None
        except Exception as exception:
            print(exception, file=sys.stderr)
            return None

        real_dir: IFile = self.__real_file.get_parent()
        v_dir: AesFile = AesFile(real_dir, self.__drive)
        return v_dir

    def delete(self):
        """!
        Delete this file.
        """
        self.__real_file.delete()

    def mkdir(self):
        """!
        Create this directory. Currently Not Supported
        """
        raise NotImplemented()

    def get_last_date_modified(self) -> int:
        """!
        Returns the last modified date in milliseconds
        """
        return self.__real_file.get_last_date_modified()

    def get_length(self) -> int:
        """!
        Return the virtual size of the file excluding the header and hash signatures.
        """
        r_size: int = self.__real_file.get_length()
        if r_size == 0:
            return r_size
        return r_size - self.__get_header_length() - self.__get_hash_total_bytes_length()

    def __get_hash_total_bytes_length(self) -> int:
        """!
        Returns the hash total bytes occupied by signatures
        """
        # file does not support integrity
        if self.get_file_chunk_size() <= 0:
            return 0

        # integrity has been requested but hash is missing
        if self.__integrity and self.get_hash_key() is None:
            raise IntegrityException("File requires hash_key, use set_verify_integrity() to provide one")

        real_length: int = self.__real_file.get_length()
        header_length: int = self.__get_header_length()
        return Integrity.get_total_hash_data_length(EncryptionMode.Decrypt, real_length - header_length,
                                                    self.get_file_chunk_size(),
                                                    Generator.HASH_RESULT_LENGTH,
                                                    Generator.HASH_KEY_LENGTH)

    # TODO: files with real same name can exists we can add checking all files in the dir
    #  and throw an Exception though this could be an expensive operation
    def create_file(self, real_filename: str, key: bytearray | None = None, file_name_nonce: bytearray | None = None,
                    file_nonce: bytearray | None = None) -> AesFile:
        """!
        Create a file under this directory
        
        @param real_filename:  The real file name of the file (encrypted)
        @param key:           The key that will be used for encryption
        @param file_name_nonce: The nonce for the encrypting the filename
        @param file_nonce:     The nonce for the encrypting the file contents
        """

        if self.__drive is None and (key is None or file_name_nonce is None or file_nonce is None):
            raise SecurityException("Need to pass the key, filename nonce, and file nonce if not using a drive")

        encrypted_filename: str = self._get_encrypted_filename(real_filename, key, file_name_nonce)
        file: IFile = self.__real_file.create_file(encrypted_filename)
        salmon_file: AesFile = AesFile(file, self.__drive)
        salmon_file.set_encryption_key(key)
        salmon_file.__integrity = self.__integrity
        if self.__drive and (file_nonce or file_name_nonce):
            SecurityException("Nonce is already set by the drive")
        if self.__drive and key:
            SecurityException("Key is already set by the drive")
        salmon_file.__requested_nonce = file_nonce
        return salmon_file

    def rename(self, new_filename: str, nonce: bytearray = None):
        """!
        Rename the virtual file name
        
        @param new_filename: The new filename this file will be renamed to
        @param nonce:       The nonce to use
        """

        if self.__drive is None and (self.__encryptionKey is None or self.__requested_nonce is None):
            raise SecurityException("Need to pass a nonce if not using a drive")
        self.rename(new_filename, None)

        new_encrypted_filename: str = self._get_encrypted_filename(new_filename, None, nonce)
        self.__real_file.rename_to(new_encrypted_filename)
        _baseName = None

    def exists(self) -> bool:
        """!
        Returns True if this file exists
        """
        if self.__real_file is None:
            return False
        return self.__real_file.exists()

    def __get_decrypted_filename(self, filename: str) -> str:
        """!
        Return the decrypted filename of a real filename
        
        @param filename: The filename of a real file
        """
        if self.__drive is None and (self.__encryptionKey is None or self.__requested_nonce is None):
            SecurityException("Need to use a drive or pass key and nonce")
        return self._get_decrypted_filename(filename, None, None)

    def _get_decrypted_filename(self, filename: str, key: bytearray | None, nonce: bytearray | None) -> str:
        """!
        Return the decrypted filename of a real filename
        
        @param filename: The filename of a real file
        @param key:      The encryption key if the file doesn't belong to a drive
        @param nonce:    The nonce if the file doesn't belong to a drive
        """
        rfilename: str = filename.replace("-", "/")
        if self.__drive and nonce:
            SecurityException("Filename nonce is already set by the drive")
        if self.__drive and key:
            SecurityException("Key is already set by the drive")

        if key is None:
            key = self.__encryptionKey
        if key is None and self.__drive:
            key = self.__drive.get_key().get_drive_key()
        decfilename: str = TextDecryptor.decrypt_string(rfilename, key, nonce)
        return decfilename

    def _get_encrypted_filename(self, filename: str, key: bytearray | None, nonce: bytearray | None) -> str:
        """!
        Return the encrypted filename of a virtual filename
        
        @param filename: The virtual filename
        @param key:      The encryption key if the file doesn't belong to a drive
        @param nonce:    The nonce if the file doesn't belong to a drive
        """
        if self.__drive and nonce:
            raise SecurityException("Filename nonce is already set by the drive")
        if self.__drive:
            nonce = self.__drive.get_next_nonce()
        if self.__drive and key:
            raise SecurityException("Key is already set by the drive")
        if self.__drive:
            key = self.__drive.get_key().get_drive_key()
        encrypted_path: str = TextEncryptor.encrypt_string(filename, key, nonce)
        encrypted_path = encrypted_path.replace("/", "-")
        return encrypted_path

    def get_drive(self) -> AesDrive:
        """!
        Get the drive.
        
        @returns The drive
        """
        return self.__drive

    def set_tag(self, tag: object):
        """!
        Set the tag for this file.
        
        @param tag:         """
        self.__tag = tag

    def get_tag(self) -> object:
        """!
        Get the file tag.
        
        @returns The file tag.
        """
        return self.__tag

    def move(self, v_dir: AesFile, options: IFile.MoveOptions | None = None) -> AesFile:
        """!
        Move file to another directory.
        
        @param v_dir:                Target directory.
        @param options: The options
        @returns The new file
        @exception IOError: Thrown if there is an IO error.
        """
        new_real_file: IFile = self.__real_file.move(v_dir.__real_file, options)
        return AesFile(new_real_file, self.__drive)

    def copy(self, v_dir: AesFile, options: IFile.CopyOptions | None = None) -> AesFile:
        """!
        Copy a file to another directory.
        
        @param v_dir:                Target directory.
        @param options: The options
        @returns The new file
        @exception IOError: Thrown if there is an IO error.
        """
        new_real_file: IFile = self.__real_file.copy(v_dir.__real_file, options)
        return AesFile(new_real_file, self.__drive)

    def copy_recursively(self, dest: AesFile, options: IVirtualFile.VirtualRecursiveCopyOptions | None = None):
        """!
        Copy a directory recursively
        
        @param dest: Destination directory
        @param options: The options
        """
        if not options:
            options = IFile.RecursiveCopyOptions()
        on_failed_real_file: Callable[[IFile, Exception], Any] | None = None
        if options.on_failed:
            on_failed_real_file = lambda file, ex: options.on_failed(AesFile(file, self.get_drive()), ex)

        rename_real_file: Callable[[IFile], str] | None = None
        # use auto rename only when we are using a drive
        if options.auto_rename and self.get_drive():
            rename_real_file = lambda file: self.__delegate_rename(file, options.auto_rename)
        copy_options: IFile.RecursiveCopyOptions = IFile.RecursiveCopyOptions()
        copy_options.auto_rename = rename_real_file
        copy_options.auto_rename_folders = options.auto_rename_folders
        copy_options.on_failed = on_failed_real_file
        copy_options.on_progress_changed = lambda file, position, length: \
            self.__notify_progress(file, position, length, options.on_progress_changed)

        self.__real_file.copy_recursively(dest.__real_file, copy_options)

    def move_recursively(self, dest: AesFile, options: IVirtualFile.VirtualRecursiveMoveOptions | None = None):
        """!
        Move a directory recursively
        
        @param dest: The destination directory
        @param options: The options
        """

        if not options:
            options = IFile.RecursiveMoveOptions()

        on_failed_real_file: Callable[[IFile, Exception], Any] | None = None
        if options.on_failed:
            on_failed_real_file = lambda file, ex: self.__notify_failed(file, ex, options.on_failed)

        rename_real_file: Callable[[IFile], str] | None = None
        # use auto rename only when we are using a drive
        if options.auto_rename and self.get_drive():
            rename_real_file = lambda file: self.__delegate_rename(file, options.auto_rename)

        move_options: IFile.RecursiveCopyOptions = IFile.RecursiveCopyOptions()
        move_options.auto_rename = rename_real_file
        move_options.auto_rename_folders = options.auto_rename_folders
        move_options.on_failed = on_failed_real_file
        move_options.on_progress_changed = lambda file, position, length: \
            self.__notify_progress(file, position, length, options.on_progress_changed)
        self.__real_file.move_recursively(dest.get_real_file(), move_options)

    def delete_recursively(self, options: IVirtualFile.VirtualRecursiveDeleteOptions | None = None):
        """!
        Delete directory (Recursively)
        @param options: The options
        """
        if not options:
            options = IVirtualFile.VirtualRecursiveDeleteOptions()
        on_failed_real_file: Callable[[IFile, Exception], Any] | None = None
        if options.on_failed:
            on_failed_real_file = lambda file, ex: self.__notify_failed(file, ex, options.on_failed)

        delete_options = IVirtualFile.VirtualRecursiveDeleteOptions()
        delete_options.on_failed = options.on_failed
        delete_options.on_progress_changed = lambda file, position, length: \
            self.__notify_progress(file, position, length, options.on_progress_changed)
        self.get_real_file().delete_recursively(delete_options)

    def get_minimum_part_size(self) -> int:
        """!
        Get the minimum size a file can be split for parallel processing
        """
        curr_chunk_size = self.get_file_chunk_size()
        if curr_chunk_size and curr_chunk_size != 0:
            return curr_chunk_size
        if self.get_requested_chunk_size() and self.get_requested_chunk_size() != 0:
            return self.get_requested_chunk_size()
        return self.get_block_size()

    @staticmethod
    def auto_rename(file: AesFile) -> str:
        """!
        Default auto rename a file
        @param file: The file
        @returns The new file name
        """
        try:
            return AesFile.auto_rename_file(file)
        except Exception as ex:
            try:
                return file.get_name()
            except Exception as ex1:
                return ""

    @staticmethod
    def auto_rename_file(file: AesFile) -> str:
        """!
        <summary>
        Get an auto generated copy of the name for the file.
        </summary>
        <param name="file"></param>
        <returns></returns>
        """
        filename: str = IFile.auto_rename(file.get_name())
        nonce: bytearray = file.get_drive().get_next_nonce()
        key: bytearray = file.get_drive().get_key().get_drive_key()
        encrypted_path: str = TextEncryptor.encrypt_string(filename, key, nonce)
        encrypted_path = encrypted_path.replace("/", "-")
        return encrypted_path

    def __notify_progress(self, file: IFile, position, length,
                          progress_listener: Callable[[AesFile, int, int], Any]):
        if progress_listener:
            progress_listener(AesFile(file, self.__drive), position, length)

    def __delegate_rename(self, file: IFile, auto_rename: Callable[[AesFile], str]):
        try:
            return auto_rename(AesFile(file, self.get_drive()))
        except Exception as e:
            return file.get_name()

    def __notify_failed(self, file: IFile, ex: Exception,
                        on_failed: Callable[[AesFile, Exception], Any] | None = None):
        if on_failed:
            on_failed(AesFile(file, self.get_drive()), ex)
