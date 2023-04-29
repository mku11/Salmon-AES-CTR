/*
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
*/
using Salmon.Streams;
using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.CompilerServices;
using System.Text;
using static Salmon.Streams.SalmonStream;

namespace Salmon.FS
{
    public class SalmonFile
    {
        public static readonly string separator = "/";

        private SalmonDrive drive;
        private IRealFile realFile;
        private string _baseName;
        private string _path;
        private string _realPath;
        private long? _size = null;
        private long? _lastDateModified = null;
        private bool? _isFile = null;
        private bool? _isDirectory = null;
        private bool overwrite;
        private bool integrity;
        private int? reqChunkSize;
        private byte[] encryptionKey;
        private byte[] hmacKey;
        private byte[] requestedNonce;
        private Object tag;

        private class SalmonFileHeader
        {
            internal byte[] mgc;
            internal byte version;
            internal int chunkSize;
            internal byte[] nonce;
        }

        /// <summary>
        /// Provides a file handle that can be used to create encrypted files.
        /// Requires a virtual drive that supports the underlying filestem, see DotNetFile implementation.
        /// </summary>
        /// <param name="drive">The file virtual system that will be used with file operations</param>
        /// <param name="realFile">The real file</param>
        public SalmonFile(IRealFile realFile, SalmonDrive drive = null)
        {
            this.drive = drive;
            this.realFile = realFile;
            if (drive != null)
                integrity = drive.GetEnableIntegrityCheck();
            if (integrity)
                reqChunkSize = drive.GetDefaultFileChunkSize();
            if (drive != null && drive.GetKey() != null)
                hmacKey = drive.GetKey().GetHMACKey();
        }

        /// <summary>
        /// Retrieves a SalmonStream that will be used for decrypting the data stored in the file
        /// </summary>
        /// <param name="bufferSize">The buffer size that will be used for reading from the real file</param>
        /// <returns></returns>
        public SalmonStream GetInputStream()
        {
            if (!Exists())
                throw new Exception("File does not exist");

            Stream realStream = realFile.GetInputStream();
            realStream.Seek(SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH, SeekOrigin.Begin);

            byte[] fileChunkSizeBytes = new byte[GetChunkSizeLength()];
            int bytesRead = realStream.Read(fileChunkSizeBytes, 0, fileChunkSizeBytes.Length);
            if (bytesRead == 0)
                throw new Exception("Could not parse chunks size from file header");
            int chunkSize = (int)BitConverter.ToLong(fileChunkSizeBytes, 0, 4);
            if (integrity && chunkSize == 0)
                throw new Exception("Cannot check integrity if file doesn't support it");

            byte[] nonceBytes = new byte[SalmonGenerator.NONCE_LENGTH];
            int ivBytesRead = realStream.Read(nonceBytes, 0, nonceBytes.Length);
            if (ivBytesRead == 0)
                throw new Exception("Could not parse nonce from file header");

            realStream.Position = 0;
            byte[] headerData = new byte[GetHeaderLength()];
            realStream.Read(headerData, 0, headerData.Length);

            SalmonStream stream = new SalmonStream(GetEncryptionKey(),
                nonceBytes, EncryptionMode.Decrypt, realStream, headerData: headerData,
                integrity: integrity, chunkSize: GetFileChunkSize(), hmacKey: GetHMACKey());
            return stream;
        }



        public SalmonStream GetOutputStream()
        {
            return GetOutputStream(null);
        }

        /// <summary>
        /// Retrieves a stream that can be used to encrypt data and write to the file.
        /// </summary>
        /// <param name="bufferSize">The buffer size that will be used to write data to the real file</param>
        /// <returns></returns>
        [MethodImpl(MethodImplOptions.Synchronized)]

        internal SalmonStream GetOutputStream(byte[] nonce)
        {
            // check if we have an existing iv in the header
            byte[] nonceBytes = GetFileNonce();
            if (nonceBytes != null && !overwrite)
                throw new SalmonSecurityException("You should not overwrite existing files for security instead delete the existing file and create a new file. If this is a new file and you want to use parallel streams you can override this with SetAllowOverwrite(true)");

            if (nonceBytes == null)
                CreateHeader(nonce);
            nonceBytes = GetFileNonce();

            // we also get the header data to include in HMAC
            byte[] headerData = GetRealFileHeaderData(realFile);

            // create a stream with the file chunk size specified which will be used to host the integrity HMACs
            // we also specify if stream ranges can be overwritten which is generally dangerous if the file is existing
            // but practical if the file is brand new and multithreaded writes for performance need to be used.
            Stream realStream = realFile.GetOutputStream();
            realStream.Seek(GetHeaderLength(), SeekOrigin.Begin);

            SalmonStream stream = new SalmonStream(GetEncryptionKey(), nonceBytes,
                EncryptionMode.Encrypt, realStream, headerData: headerData,
                integrity: integrity, chunkSize: GetRequestedChunkSize() > 0 ? GetRequestedChunkSize() : null, hmacKey: GetHMACKey());
            stream.SetAllowRangeWrite(overwrite);
            return stream;
        }

        /// <summary>
        /// Returns the current encryption key
        /// </summary>
        /// <returns></returns>
        public byte[] GetEncryptionKey()
        {
            if (this.encryptionKey != null)
                return encryptionKey;
            if (drive != null && drive.GetKey() != null)
                return drive.GetKey().GetDriveKey();
            return null;
        }

        /// <summary>
        /// Sets the encryption key
        /// </summary>
        /// <param name="encyptionKey">The AES encryption key to be used</param>
        public void SetEncryptionKey(byte[] encyptionKey)
        {
            this.encryptionKey = encyptionKey;
        }

        /// <summary>
        /// Return the current header data that are stored in the file
        /// </summary>
        /// <param name="realFile">The real file containing the data</param>
        /// <returns></returns>
        private byte[] GetRealFileHeaderData(IRealFile realFile)
        {
            Stream realStream = realFile.GetInputStream();
            byte[] headerData = new byte[GetHeaderLength()];
            realStream.Read(headerData, 0, headerData.Length);
            realStream.Close();
            return headerData;
        }

        /// <summary>
        /// Retrieve the current HMAC key that is used to encrypt / decrypt the file contents.
        /// </summary>
        /// <returns></returns>
        private byte[] GetHMACKey()
        {
            return hmacKey;
        }

        /// <summary>
        /// Enabled verification of file integrity during read() and write()
        /// </summary>
        /// <param name="integrity">True if enable integrity verification</param>
        /// <param name="hmacKey">The HMAC key to be used for verification</param>
        public void SetVerifyIntegrity(bool integrity, byte[] hmacKey = null)
        {
            if (integrity && hmacKey == null && drive != null)
                hmacKey = drive.GetKey().GetHMACKey();
            this.integrity = integrity;
            this.hmacKey = hmacKey;
            this.reqChunkSize = GetFileChunkSize();
        }


        /// <summary>
        /// 
        /// </summary>
        /// <param name="integrity"></param>
        /// <param name="hmacKey"></param>
        /// <param name="requestChunkSize">
        /// 0 use default file chunk
        /// >0 to specify integrity chunks
        /// </param>
        public void SetApplyIntegrity(bool integrity, byte[] hmacKey = null, int? requestChunkSize = null)
        {
            int? fileChunkSize = GetFileChunkSize();

            if (fileChunkSize != null)
                throw new Exception("Cannot redefine chunk size, delete file and recreate");
            if (requestChunkSize != null && requestChunkSize < 0)
                throw new Exception("Chunk size needs to be zero for default chunk size or a positive value");
            if (integrity && fileChunkSize == 0)
                throw new Exception("Cannot enable integrity if the file is not created with integrity, export file and reimport with integrity");

            if (integrity && hmacKey == null && drive != null)
                hmacKey = drive.GetKey().GetHMACKey();
            this.integrity = integrity;
            this.reqChunkSize = requestChunkSize;
            if (integrity && this.reqChunkSize == null && drive != null)
                this.reqChunkSize = drive.GetDefaultFileChunkSize();
            this.hmacKey = hmacKey;
        }

        /// <summary>
        /// Warning! Allow overwrite on a current stream. Overwriting is not a good idea because it will re-use the same IV.
        /// This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
        /// You should only use this setting for initial encryption with parallel streams and not for overwriting!
        /// </summary>
        /// <param name="value">True to allow overwrite operations</param>
        public void SetAllowOverwrite(bool value)
        {
            overwrite = value;
        }

        private int GetChunkSizeLength()
        {
            return sizeof(int);
        }


        /// <summary>
        /// Return the current chunk size requested that will be used for integrity
        /// </summary>
        /// <returns></returns>
        public int? GetRequestedChunkSize()
        {
            return reqChunkSize;
        }

        /// <summary>
        /// Returns the file chunk size
        /// </summary>
        /// <returns></returns>
        public int? GetFileChunkSize()
        {
            SalmonFileHeader header = GetHeader();
            if (header == null)
                return null;
            return GetHeader().chunkSize;
        }


        SalmonFileHeader GetHeader()
        {
			if(!Exists())
				return null;
            SalmonFileHeader header = new SalmonFileHeader();
            Stream stream = null;
            try
            {
                stream = realFile.GetInputStream();
                header.mgc = new byte[SalmonGenerator.MAGIC_LENGTH];
                int bytesRead = stream.Read(header.mgc, 0, header.mgc.Length);
                if (bytesRead != header.mgc.Length)
                    return null;
                byte[] buff = new byte[8];
                bytesRead = stream.Read(buff, 0, SalmonGenerator.VERSION_LENGTH);
                if (bytesRead != SalmonGenerator.VERSION_LENGTH)
                    return null;
                header.version = buff[0];
                bytesRead = stream.Read(buff, 0, GetChunkSizeLength());
                if (bytesRead != GetChunkSizeLength())
                    return null;
                header.chunkSize = (int)BitConverter.ToLong(buff, 0, bytesRead);
                header.nonce = new byte[SalmonGenerator.NONCE_LENGTH];
                bytesRead = stream.Read(header.nonce, 0, SalmonGenerator.NONCE_LENGTH);
                if (bytesRead != SalmonGenerator.NONCE_LENGTH)
                    return null;
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
            finally
            {
                if (stream != null)
                    stream.Close();
            }
            return header;
        }


        /// <summary>
        /// Returns the minimum part size that can be encrypted / decrypted in parallel
        /// </summary>
        /// <returns></returns>
        public int GetMinimumPartSize()
        {
            int? currChunkSize = GetFileChunkSize();
            if (currChunkSize != null && currChunkSize != 0)
                return (int)currChunkSize;
            if (reqChunkSize != null && reqChunkSize != 0)
                return (int)reqChunkSize;
            return GetBlockSize();
        }


        /// <summary>
        /// Returns the length of the header in bytes
        /// </summary>
        /// <returns></returns>
        private int GetHeaderLength()
        {
            return SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH +
                GetChunkSizeLength() + SalmonGenerator.NONCE_LENGTH;
        }

        /// <summary>
        /// Returns the initial vector that is used for encryption / decryption
        /// </summary>
        /// <returns></returns>
        public byte[] GetFileNonce()
        {
            SalmonFileHeader header = GetHeader();
            if (header == null)
                return null;
            return GetHeader().nonce;
        }

        public void SetRequestedNonce(byte[] nonce)
        {
            this.requestedNonce = nonce;
        }

        public byte[] GetRequestedNonce()
        {
            return requestedNonce;
        }

        /// <summary>
        /// Create the header for the file
        /// </summary>
        public void CreateHeader(byte[] nonce)
        {
            // set it to zero (disabled integrity) or get the default chunk
            // size defined by the drive
            if (integrity && reqChunkSize == null && drive != null)
                reqChunkSize = drive.GetDefaultFileChunkSize();
            else if (!integrity)
                reqChunkSize = 0;
            if (reqChunkSize == null)
                throw new Exception("File requires a chunk size");

            if (nonce != null)
                requestedNonce = nonce;
            else if (requestedNonce == null && drive != null)
                requestedNonce = drive.GetNextNonce();
            if (requestedNonce == null)
                throw new Exception("File requires a nonce, use SetRequestedNonce");

            Stream realStream = realFile.GetOutputStream();
            byte[] magicBytes = SalmonGenerator.GetMagicBytes();
            realStream.Write(magicBytes, 0, magicBytes.Length);

            byte version = SalmonGenerator.GetVersion();
            realStream.Write(new byte[] { version }, 0, SalmonGenerator.VERSION_LENGTH);

            byte[] chunkSizeBytes = BitConverter.ToBytes((int)reqChunkSize, 4);
            realStream.Write(chunkSizeBytes, 0, chunkSizeBytes.Length);

            realStream.Write(requestedNonce, 0, requestedNonce.Length);

            realStream.Flush();
            realStream.Close();
        }

        /// <summary>
        /// Return the AES block size for encryption / decryption
        /// </summary>
        /// <returns></returns>
        public int GetBlockSize()
        {
            return SalmonGenerator.BLOCK_SIZE;
        }

        /// <summary>
        /// Lists files and directories under this directory
        /// </summary>
        /// <returns></returns>
        public SalmonFile[] ListFiles()
        {
            IRealFile[] files = realFile.ListFiles();
            List<SalmonFile> salmonFiles = new List<SalmonFile>();
            for (int i = 0; i < files.Length; i++)
            {
                salmonFiles.Add(new SalmonFile(files[i], drive));
            }
            return salmonFiles.ToArray();
        }

        /// <summary>
        /// Creates a directory under this directory
        /// </summary>
        /// <param name="dirName">The name of the directory to be created</param>
        public SalmonFile CreateDirectory(string dirName, byte[] key = null, byte[] dirNameNonce = null)
        {
            string encryptedDirName = GetEncryptedFilename(dirName, key, dirNameNonce);
            IRealFile realDir = realFile.CreateDirectory(encryptedDirName);
            return new SalmonFile(realDir, drive);
        }

        /// <summary>
        /// Return the real file
        /// </summary>
        /// <returns></returns>
        public IRealFile GetRealFile()
        {
            return realFile;
        }

        /// <summary>
        /// Returns true if this is a file
        /// </summary>
        /// <returns></returns>
        public bool IsFile()
        {
            if (_isFile == null)
                _isFile = realFile.IsFile();
            return (bool)_isFile;
        }

        /// <summary>
        /// Returns True if this is a directory
        /// </summary>
        /// <returns></returns>
        public bool IsDirectory()
        {
            if (_isDirectory == null)
                _isDirectory = realFile.IsDirectory();
            return (bool)_isDirectory;
        }

        /// <summary>
        /// Return the path of the real file stored
        /// </summary>
        /// <returns></returns>
        public string GetPath()
        {
            if (_path == null)
            {
                string realPath = realFile.GetAbsolutePath();
                _path = GetPath(realPath);
            }
            return _path;
        }

        /// <summary>
        /// Returns the virtual path for the drive and the file provided
        /// </summary>
        /// <param name="realPath">The path of the real file </param>
        /// <returns></returns>
        private string GetPath(string realPath)
        {
            string relativePath = GetRelativePath(realPath);
            StringBuilder path = new StringBuilder();
            string[] parts = relativePath.Split(new string[] { Path.DirectorySeparatorChar + "" },
                StringSplitOptions.RemoveEmptyEntries);
            foreach (string part in parts)
            {
                if (!part.Equals(""))
                {
                    path.Append(separator);
                    path.Append(GetDecryptedFilename(part));
                }
            }
            return path.ToString();
        }

        /// <summary>
        /// Return the path of the real file
        /// </summary>
        /// <returns></returns>
        public string GetRealPath()
        {
            if (_realPath == null)
                _realPath = realFile.GetPath();
            return _realPath;
        }

        /// <summary>
        /// Return the virtual relative path of the file belonging to a drive
        /// </summary>
        /// <param name="drive">The virtual drive the file belongs to</param>
        /// <param name="realPath">The path of the real file</param>
        /// <returns></returns>
        private string GetRelativePath(string realPath)
        {
            SalmonFile virtualRoot = drive.GetVirtualRoot();
            string virtualRootPath = virtualRoot.realFile.GetAbsolutePath();
            if (realPath.StartsWith(virtualRootPath))
            {
                return realPath.Replace(virtualRootPath, "");
            }
            return realPath;
        }

        /// <summary>
        /// Returns the basename for the file
        /// </summary>
        /// <returns></returns>
        public string GetBaseName()
        {
            if (_baseName != null)
                return _baseName;
            if (drive != null && GetRealPath().Equals(drive.GetVirtualRoot().GetRealPath()))
                return "";

            string realBaseName = realFile.GetBaseName();
            _baseName = GetDecryptedFilename(realBaseName);
            return _baseName;
        }


        /// <summary>
        /// Returns the virtual parent directory 
        /// </summary>
        /// <returns></returns>
        public SalmonFile GetParent()
        {
            try
            {
                if (drive == null
                        || this.GetPath().Equals("")
                        || this.GetPath().Equals(Path.DirectorySeparatorChar + "")
                )
                    return null;
            }
            catch (Exception exception)
            {
                Console.Error.WriteLine(exception);
                return null;
            }
            IRealFile realDir = realFile.GetParent();
            SalmonFile dir = new SalmonFile(realDir, drive);
            return dir;
        }

        /// <summary>
        /// Delete this file
        /// </summary>
        public void Delete()
        {
            realFile.Delete();
        }

        /// <summary>
        /// Create this directory. Currently Not Supported
        /// </summary>
        public void Mkdir()
        {
            throw new NotSupportedException();
        }

        /// <summary>
        /// Returns the last date modified in milliseconds
        /// </summary>
        /// <returns></returns>
        public long GetLastDateTimeModified()
        {
            if (_lastDateModified == null)
                _lastDateModified = realFile.LastModified();
            return (long)_lastDateModified;
        }

        /// <summary>
        /// Return the virtual size of the file excluding the header and HMAC signatures.
        /// </summary>
        /// <returns></returns>
        public long GetSize()
        {
            if (_size == null)
                _size = realFile.Length() - GetHeaderLength() - GetHMACTotalBytesLength();
            return (long)_size;
        }

        /// <summary>
        /// Returns the HMAC total bytes occupied by signatures
        /// </summary>
        /// <returns></returns>
        private long GetHMACTotalBytesLength()
        {
            // file does not support integrity
            if (GetFileChunkSize() == null || GetFileChunkSize() <= 0)
                return 0;

            // integrity has been requested but hmac is missing
            if (integrity && GetHMACKey() == null)
                throw new Exception("File requires hmacKey, use SetVerifyIntegrity() to provide one");

            return SalmonIntegrity.GetTotalHMACBytesFrom(realFile.Length(), (int)GetFileChunkSize(), SalmonGenerator.HMAC_RESULT_LENGTH);
        }


        /// <summary>
        /// Create a file under this directory
        /// </summary>
        /// <param name="realFilename">The real file name of the file (encrypted)</param>
        /// <returns></returns>
        //TODO: files with real same name can exists we can add checking all files in the dir
        // and throw an Exception though this could be an expensive operation
        public SalmonFile CreateFile(string realFilename, byte[] key = null, byte[] fileNameNonce = null, byte[] fileNonce = null)
        {
            string encryptedFilename = GetEncryptedFilename(realFilename, key, fileNameNonce);
            IRealFile file = realFile.CreateFile(encryptedFilename);
            SalmonFile salmonFile = new SalmonFile(file, drive);
            salmonFile.SetEncryptionKey(key);
            salmonFile.SetRequestedNonce(fileNonce);
            return salmonFile;
        }

        /// <summary>
        ///  Rename the virtual file name
        /// </summary>
        /// <param name="newFilename">The new filename this file will be renamed to</param>
        public void Rename(string newFilename, byte[] nonce = null)
        {
            string newEncryptedFilename = GetEncryptedFilename(newFilename, nonce);
            realFile.RenameTo(newEncryptedFilename);
            _baseName = null;
            _path = null;
            _realPath = null;
        }

        /// <summary>
        /// Returns true if this file exists
        /// </summary>
        /// <returns></returns>
        public bool Exists()
        {
            if (realFile == null)
                return false;
            return realFile.Exists();
        }


        /// <summary>
        /// Return the decrypted filename of a real filename
        /// </summary>
        /// <param name="filename">The filename of a real file</param>
        /// <returns></returns>
        private string GetDecryptedFilename(string filename, byte[] key = null)
        {
            filename = filename.Replace("-", "/");
            if (key == null)
                key = this.encryptionKey;
            if (key == null && drive != null)
                key = drive.GetKey().GetDriveKey();
            string decryptedPath = SalmonTextEncryptor.DecryptString(filename, key, null, true);
            return decryptedPath;
        }

        /// <summary>
        /// Return the encrypted filename of a virtual filename
        /// </summary>
        /// <param name="filename">The virtual filename</param>
        /// <returns></returns>
        private string GetEncryptedFilename(string filename, byte[] key = null, byte[] nonce = null)
        {
            if (nonce == null && drive != null)
                nonce = drive.GetNextNonce();
            if (key == null && drive != null)
                key = drive.GetKey().GetDriveKey();
            string encryptedPath = SalmonTextEncryptor.EncryptString(filename, key, nonce, true);
            encryptedPath = encryptedPath.Replace("/", "-");
            return encryptedPath;
        }


        public SalmonDrive GetDrive()
        {
            return drive;
        }

        public void SetTag(Object tag)
        {
            this.tag = tag;
        }

        public Object GetTag()
        {
            return tag;
        }

        public SalmonFile Move(SalmonFile dir, AbsStream.OnProgressChanged OnProgressListener)
        {
            IRealFile newRealFile = realFile.Move(dir.realFile, OnProgressListener);
            return new SalmonFile(newRealFile, drive);
        }

        public SalmonFile Copy(SalmonFile dir, AbsStream.OnProgressChanged OnProgressListener)
        {
            IRealFile newRealFile = realFile.Copy(dir.realFile, OnProgressListener);
            return new SalmonFile(newRealFile, drive);
        }

    }
}
