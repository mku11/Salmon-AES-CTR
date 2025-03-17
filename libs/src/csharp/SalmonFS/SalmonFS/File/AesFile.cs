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


using Mku.FS.File;
using Mku.Salmon;
using Mku.Salmon.Integrity;
using Mku.Salmon.Sequence;
using Mku.Salmon.Streams;
using Mku.Salmon.Text;
using Mku.SalmonFS.Drive;
using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.CompilerServices;
using System.Text;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.SalmonFS.File;

/// <summary>
///  A virtual file backed by an encrypted <see cref="IFile"/> on the real filesystem.
///  Supports operations for retrieving <see cref="AesStream"/> for reading/decrypting
///  and writing/encrypting contents.
/// </summary>
public class AesFile : IVirtualFile
{
    private static readonly string separator = "/";

    /// <summary>
    /// The drive this file belongs too.
    /// </summary>
    public AesDrive Drive { get; private set; }

    private readonly EncryptionFormat format;

    /// <summary>
    /// The real encrypted file on the physical disk.
    /// </summary>
    override
    public IFile RealFile { get; protected set; }

    // cached values
    private string _baseName;
    private Header _header;

    /// <summary>
    ///  The path of the real file stored
    /// </summary>
    override
    public string Path
    {
        get
        {
            string realPath = RealFile.AbsolutePath;
            return GetPath(realPath);
        }
    }


    /// <summary>
    ///  The path of the real file
    /// </summary>
    override
    public string RealPath => RealFile.Path;

    /// <summary>
    ///  The last date modified in milliseconds
    /// </summary>
    override
    public long LastDateModified => RealFile.LastDateModified;

    /// <summary>
    ///  The virtual size of the file excluding the header and hash signatures.
    /// </summary>
    override
    public long Length
    {
        get
        {
            long rSize = RealFile.Length;
            if (rSize == 0)
                return rSize;
            return rSize - GetHeaderLength() - GetHashTotalBytesLength();
        }
    }

    /// <summary>
    ///  True if this is a file
    /// </summary>
    override
    public bool IsFile => RealFile.IsFile;

    /// <summary>
    ///  True if this is a directory
    /// </summary>
    override
    public bool IsDirectory => RealFile.IsDirectory;

    /// <summary>
    ///  Warning! Allow overwriting on a current stream. Overwriting is not a good idea because it will re-use the same IV.
    ///  This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
    ///  You should only use this setting for initial encryption with parallel streams and not for overwriting!
	/// </summary>
	///  <param name="value">True to allow overwriting operations</param>
    public bool AllowOverwrite { get; set; }

    /// <summary>
    ///  Is integrity enabled;
    /// </summary>
    public bool IsIntegrityEnabled { get; private set; }

    private int reqChunkSize;
    private byte[] encryptionKey;
    private byte[] requestedNonce;

    /// <summary>
    /// The hash used to verify integrity for this file.
    /// </summary>
    private byte[] HashKey { get; set; }

    /// <summary>
    /// File Tag. You can use to tag this file.
    /// </summary>
    public Object Tag { get; set; }

    /// <summary>
    ///  Provides a file handle that can be used to create encrypted files.
    ///  Requires a virtual drive that supports the underlying filesystem, see File implementation.
    /// </summary>
    ///  <param name="drive">   The file virtual system that will be used with file operations</param>
    /// <param name="format">The encryption format</param>
    ///  <param name="realFile">The real file</param>
    public AesFile(IFile realFile, AesDrive drive = null, EncryptionFormat format = EncryptionFormat.Salmon)
    {
        this.RealFile = realFile;
        this.Drive = drive;
        this.format = format;
        if (IsIntegrityEnabled)
            reqChunkSize = drive.DefaultFileChunkSize;
        if (drive != null && drive.Key != null)
            HashKey = drive.Key.HashKey;
    }

    /// <summary>
    ///  Return the current chunk size requested that will be used for integrity
    /// </summary>
    public int RequestedChunkSize
    {
        get
        {
            lock (this)
            {
                return reqChunkSize;
            }
        }
    }

    /// <summary>
    ///  Get the file chunk size from the header.
	/// </summary>
	///  <returns>The chunk size.</returns>
    ///  <exception cref="IOException">Thrown if the format is corrupt.</exception>
    public int FileChunkSize
    {
        get
        {
            Header header = Header;
            if (header == null)
                return 0;
            return Header.ChunkSize;
        }
    }

    /// <summary>
    ///  The custom <see cref="Salmon.Header"></see> from this file.
    /// </summary>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public Header Header
    {
        get
        {
            if (!Exists)
                return null;
            if (_header != null)
                return _header;
            Header header = new Header(new byte[0]);
            Stream stream = null;
            try
            {
                stream = RealFile.GetInputStream();
                header = Header.ReadHeaderData(stream);
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
                throw new IOException("Could not get file header", ex);
            }
            finally
            {
                if (stream != null)
                {
                    stream.Close();
                }
            }
            _header = header;
            return header;
        }
    }

    /// <summary>
    ///  Opens a AesStream that will be used for reading/decrypting the file contents.
	/// </summary>
	///  <returns>The input stream</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    override
    public AesStream GetInputStream()
    {
        if (!Exists)
            throw new IOException("File does not exist");

        Stream realStream = RealFile.GetInputStream();
        realStream.Seek(Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH,
                SeekOrigin.Begin);

        byte[] fileChunkSizeBytes = new byte[GetChunkSizeLength()];
        int bytesRead = realStream.Read(fileChunkSizeBytes, 0, fileChunkSizeBytes.Length);
        if (bytesRead == 0)
            throw new IOException("Could not parse chunks size from file header");
        int chunkSize = (int)BitConverter.ToLong(fileChunkSizeBytes, 0, 4);
        if (IsIntegrityEnabled && chunkSize == 0)
            throw new SecurityException("Cannot check integrity if file doesn't support it");

        byte[] nonceBytes = new byte[Generator.NONCE_LENGTH];
        int ivBytesRead = realStream.Read(nonceBytes, 0, nonceBytes.Length);
        if (ivBytesRead == 0)
            throw new IOException("Could not parse nonce from file header");

        realStream.Position = 0;
        byte[] headerData = new byte[GetHeaderLength()];
        realStream.Read(headerData, 0, headerData.Length);

        AesStream stream = new AesStream(EncryptionKey,
                nonceBytes, EncryptionMode.Decrypt, realStream, format,
                IsIntegrityEnabled, HashKey, FileChunkSize);
        return stream;
    }

    /// <summary>
    ///  Opens a AesStream for encrypting/writing contents.
	/// </summary>
	///  <returns>The output stream</returns>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    [MethodImpl(MethodImplOptions.Synchronized)]
    override
    public AesStream GetOutputStream()
    {
        return GetOutputStream(null);
    }

    /// <summary>
    ///  Get a <see cref="AesStream"/> for encrypting/writing contents to this file.
    ///  <param name="nonce">Nonce to be used for encryption. Note that each file should have</param>
    ///               a unique nonce see <see cref="AesDrive.GetNextNonce()"/>.
    /// </summary>
    ///  <returns>The output stream.</returns>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    [MethodImpl(MethodImplOptions.Synchronized)]
    internal AesStream GetOutputStream(byte[] nonce)
    {

        // check if we have an existing iv in the header
        Header header = Header;
        byte[] nonceBytes = null;
        if (header != null)
            nonceBytes = header.Nonce;

        if (nonceBytes != null && !AllowOverwrite)
            throw new SecurityException("You should not overwrite existing files for security instead delete the existing file and create a new file. If this is a new file and you want to use parallel streams call SetAllowOverwrite(true)");

        if (nonceBytes == null)
        {
            // set it to zero (disabled integrity) or get the default chunk
            // size defined by the drive
            if (IsIntegrityEnabled && reqChunkSize == 0 && Drive != null)
                reqChunkSize = Drive.DefaultFileChunkSize;
            else if (!IsIntegrityEnabled)
                reqChunkSize = 0;

            if (nonce != null)
                requestedNonce = nonce;
            else if (requestedNonce == null && Drive != null)
                requestedNonce = Drive.GetNextNonce();

            if (requestedNonce == null)
                throw new SecurityException("File requires a nonce");

            nonceBytes = requestedNonce;
        }

        // create a stream with the file chunk size specified which will be used to host the integrity hash
        // we also specify if stream ranges can be overwritten which is generally dangerous if the file is existing
        // but practical if the file is brand new and multithreaded writes for performance need to be used.
        Stream realStream = RealFile.GetOutputStream();

        byte[] key = this.EncryptionKey;
        if (key == null)
            throw new IOException("Set an encryption key to the file first");
        if (nonceBytes == null)
            throw new IOException("No nonce provided and no nonce found in file");

        AesStream stream = new AesStream(EncryptionKey, nonceBytes,
                EncryptionMode.Encrypt, realStream, format,
                IsIntegrityEnabled, HashKey, RequestedChunkSize);
        stream.AllowRangeWrite = AllowOverwrite;
        return stream;
    }

    /// <summary>
    ///  Returns the current encryption key
    /// </summary>
    public byte[] EncryptionKey
    {
        get
        {
            if (this.encryptionKey != null)
                return encryptionKey;
            if (Drive != null && Drive.Key != null)
                return Drive.Key.DriveEncKey;
            return null;
        }
        set
        {
            this.encryptionKey = value;
        }
    }

    /// <summary>
    ///  Return the current header data that are stored in the file
	/// </summary>
	///  <param name="realFile">The real file containing the data</param>
    private byte[] GetRealFileHeaderData(IFile realFile)
    {
        Stream realStream = realFile.GetInputStream();
        byte[] headerData = new byte[GetHeaderLength()];
        realStream.Read(headerData, 0, headerData.Length);
        realStream.Close();
        return headerData;
    }


    /// <summary>
    ///  Enabled verification of file integrity during read() and write()
	/// </summary>
	///  <param name="integrity">True if enable integrity verification</param>
    ///  <param name="hashKey">  The hash key to be used for verification</param>
    public void SetVerifyIntegrity(bool integrity, byte[] hashKey = null)
    {
        Header header = Header;
        if (header == null && integrity)
            throw new IntegrityException("File does not support integrity");

        if (integrity && hashKey == null && Drive != null)
            hashKey = Drive.Key.HashKey;
        this.IsIntegrityEnabled = integrity;
        this.HashKey = hashKey;
        this.reqChunkSize = header.ChunkSize;
    }

    /// <summary>
    ///  Enable integrity when writing contents
    /// </summary>
    ///  <param name="hashKey">The hash key</param>
    ///  <param name="integrity">The integrity</param>
    ///  <param name="requestChunkSize">0 use default file chunk.</param>
    ///                          A positive number to specify integrity chunks.
    public void SetApplyIntegrity(bool integrity, byte[] hashKey = null, int requestChunkSize = 0)
    {
        Header header = Header;
        if (header != null && header.ChunkSize > 0 && !AllowOverwrite)
            throw new IntegrityException("Cannot redefine chunk size");
        if (requestChunkSize < 0)
            throw new IntegrityException("Chunk size needs to be zero for default chunk size or a positive value");

        if (integrity && hashKey == null && Drive != null)
            hashKey = Drive.Key.HashKey;

        if (integrity && hashKey == null)
            throw new SecurityException("Integrity needs a hashKey");

        this.IsIntegrityEnabled = integrity;
        this.reqChunkSize = requestChunkSize;
        if (integrity && this.reqChunkSize == 0 && Drive != null)
            this.reqChunkSize = Drive.DefaultFileChunkSize;
        this.HashKey = hashKey;
    }

    /// <summary>
    ///  Returns the file chunk size
    /// </summary>
    private int GetChunkSizeLength()
    {
        return Generator.CHUNK_SIZE_LENGTH;
    }

    /// <summary>
    ///  Returns the length of the header in bytes
    /// </summary>
    private int GetHeaderLength()
    {
        return Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH +
                GetChunkSizeLength() + Generator.NONCE_LENGTH;
    }

    /// <summary>
    ///  Returns the initial vector that is used for encryption / decryption
    /// </summary>
    public byte[] FileNonce
    {
        get
        {
            Header header = Header;
            if (header == null)
                return null;
            return Header.Nonce;
        }
    }

    /// <summary>
    ///  Get the nonce that is used for encryption/decryption of this file.
	/// </summary>
	///  <returns>The requested nonce</returns>
    public byte[] RequestedNonce
    {
        get
        {
            return requestedNonce;
        }
        set
        {
            if (Drive != null)
                throw new SecurityException("Nonce is already set by the drive");
            this.requestedNonce = value;

        }
    }

    /// <summary>
    ///  Return the AES block size for encryption / decryption
    /// </summary>
    public int BlockSize => Generator.BLOCK_SIZE;

    /// <summary>
    ///  Get the count of files and subdirectories
	/// </summary>
	///  <returns>The children count</returns>
    public int ChildrenCount => RealFile.ChildrenCount;

    /// <summary>
    ///  Lists files and directories under this directory
    /// </summary>
    /// <returns>An array of files and subdirectories</returns>
    override
    public AesFile[] ListFiles()
    {
        IFile[] files = RealFile.ListFiles();
        List<AesFile> salmonFiles = new List<AesFile>();
        foreach (IFile iRealFile in files)
        {
            AesFile file = new AesFile(iRealFile, Drive);
            salmonFiles.Add(file);
        }
        return salmonFiles.ToArray();
    }

    /// <summary>
    /// Get a file/subdirectory with this filename.
    /// </summary>
    /// <param name="filename">The filename to match</param>
    /// <returns>The file/subdirectory</returns>
    override
    public AesFile GetChild(string filename)
    {
        AesFile[] files = ListFiles();
        foreach (AesFile file in files)
        {
            if (file.Name.Equals(filename))
                return file;
        }
        return null;
    }

    /// <summary>
    ///  Creates a directory under this directory
	/// </summary>
	///  <param name="dirName">The name of the directory to be created</param>
    override
    public AesFile CreateDirectory(string dirName)
    {
        if (Drive == null)
            throw new SecurityException("Need to pass the key and dirNameNonce nonce if not using a drive");
        return CreateDirectory(dirName, null, null);
    }

    /// <summary>
    ///  Creates a directory under this directory
	/// </summary>
	///  <param name="dirName">     The name of the directory to be created</param>
    ///  <param name="key">         The key that will be used to encrypt the directory name</param>
    ///  <param name="dirNameNonce">The nonce to be used for encrypting the directory name</param>
    public AesFile CreateDirectory(string dirName, byte[] key, byte[] dirNameNonce)

    {
        string encryptedDirName = GetEncryptedFilename(dirName, key, dirNameNonce);
        IFile realDir = RealFile.CreateDirectory(encryptedDirName);
        return new AesFile(realDir, Drive);
    }

    /// <summary>
    ///  Returns the virtual path for the drive and the file provided
	/// </summary>
	///  <param name="realPath">The path of the real file</param>
    private string GetPath(string realPath)
    {
        string relativePath = GetRelativePath(realPath);
        StringBuilder path = new StringBuilder();
        string[] parts = relativePath.Split(new char[] { '\\','/' });
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
    ///  Return the virtual relative path of the file belonging to a drive
	/// </summary>
	///  <param name="realPath">The path of the real file</param>
    private string GetRelativePath(string realPath)
    {
        if (Drive == null)
        {
            return this.RealFile.Name;
        }
        AesFile virtualRoot = Drive.Root;
        string virtualRootPath = virtualRoot.RealFile.AbsolutePath;
        if (realPath.StartsWith(virtualRootPath))
        {
            return realPath.Replace(virtualRootPath, "");
        }
        return realPath;
    }

    /// <summary>
    ///  The base name for the file
    /// </summary>
    override
    public string Name
    {
        get
        {
            if (_baseName != null)
                return _baseName;
            if (Drive != null && RealPath.Equals(Drive.Root.RealPath))
                return "";

            string realBaseName = RealFile.Name;
            _baseName = GetDecryptedFilename(realBaseName);
            return _baseName;
        }
    }

    /// <summary>
    ///  Returns the virtual parent directory
    /// </summary>
    override
    public AesFile Parent
    {
        get
        {
            try
            {
                if (Drive == null || Drive.Root.RealFile.Path.Equals(RealFile.Path))
                    return null;
            }
            catch (Exception exception)
            {
                Console.Error.WriteLine(exception);
                return null;
            }
            IFile realDir = RealFile.Parent;
            AesFile dir = new AesFile(realDir, Drive);
            return dir;
        }
    }

    /// <summary>
    ///  Delete this file.
    /// </summary>
    override
    public void Delete()
    {
        RealFile.Delete();
    }

    /// <summary>
    ///  Create this directory. Currently Not Supported
    /// </summary>
    override
    public void Mkdir()
    {
        throw new NotSupportedException();
    }

    /// <summary>
    ///  Returns the hash total bytes occupied by signatures
    /// </summary>
    private long GetHashTotalBytesLength()
    {
        // file does not support integrity
        if (FileChunkSize <= 0)
            return 0;

        // integrity has been requested but hash is missing
        if (IsIntegrityEnabled && HashKey == null)
            throw new IntegrityException("File requires hashKey, use SetVerifyIntegrity() to provide one");
        long realLength = this.RealFile.Length;
        int headerLength = this.GetHeaderLength();
        return Integrity.GetTotalHashDataLength(EncryptionMode.Decrypt, realLength - headerLength, FileChunkSize,
                Generator.HASH_RESULT_LENGTH, Generator.HASH_KEY_LENGTH);
    }

    /// <summary>
    ///  Create a file under this directory
	/// </summary>
	///  <param name="realFilename">The real file name of the file (encrypted)</param>
    //TODO: files with real same name can exists we can add checking all files in the dir
    // and throw an Exception though this could be an expensive operation
    override
    public AesFile CreateFile(string realFilename)
    {
        if (Drive == null)
            throw new SecurityException("Need to pass the key, filename nonce, and file nonce if not using a drive");
        return CreateFile(realFilename, null, null, null);
    }

    /// <summary>
    ///  Create a file under this directory
	/// </summary>
	///  <param name="realFilename"> The real file name of the file (encrypted)</param>
    ///  <param name="key">          The key that will be used for encryption</param>
    ///  <param name="fileNameNonce">The nonce for the encrypting the filename</param>
    ///  <param name="fileNonce">    The nonce for the encrypting the file contents</param>
    //TODO: files with real same name can exists we can add checking all files in the dir
    // and throw an Exception though this could be an expensive operation
    public AesFile CreateFile(string realFilename, byte[] key, byte[] fileNameNonce, byte[] fileNonce)
    {
        string encryptedFilename = GetEncryptedFilename(realFilename, key, fileNameNonce);
        IFile file = RealFile.CreateFile(encryptedFilename);
        AesFile salmonFile = new AesFile(file, Drive);
        salmonFile.EncryptionKey = key;
        salmonFile.IsIntegrityEnabled = IsIntegrityEnabled;
        if (Drive != null && (fileNonce != null || fileNameNonce != null))
            throw new SecurityException("Nonce is already set by the drive");
        if (Drive != null && key != null)
            throw new SecurityException("Key is already set by the drive");
        salmonFile.requestedNonce = fileNonce;
        return salmonFile;
    }

    /// <summary>
    ///  Rename the virtual file name
	/// </summary>
	///  <param name="newFilename">The new filename this file will be renamed to</param>
    override
    public void Rename(string newFilename)
    {
        if (Drive == null && (encryptionKey == null || requestedNonce == null))
            throw new SecurityException("Need to pass a nonce if not using a drive");
        Rename(newFilename, null);
    }

    /// <summary>
    ///  Rename the virtual file name
	/// </summary>
	///  <param name="newFilename">The new filename this file will be renamed to</param>
    ///  <param name="nonce">The nonce to be used</param>
    public void Rename(string newFilename, byte[] nonce)
    {
        string newEncryptedFilename = GetEncryptedFilename(newFilename, null, nonce);
        RealFile.RenameTo(newEncryptedFilename);
        _baseName = null;
    }

    /// <summary>
    ///  True if this file/directory exists
    /// </summary>
    override
    public bool Exists
    {
        get
        {
            if (RealFile == null)
                return false;
            return RealFile.Exists;
        }
    }

    /// <summary>
    ///  Return the decrypted filename of a real filename
	/// </summary>
	///  <param name="filename">The filename of a real file</param>
    private string GetDecryptedFilename(string filename)
    {
        if (Drive == null && (encryptionKey == null || requestedNonce == null))
            throw new SecurityException("Need to use a drive or pass key and nonce");
        return GetDecryptedFilename(filename, null, null);
    }

    /// <summary>
    ///  Return the decrypted filename of a real filename
	/// </summary>
	///  <param name="filename">The filename of a real file</param>
    ///  <param name="key">     The encryption key if the file doesn't belong to a drive</param>
    ///  <param name="nonce">   The nonce if the file doesn't belong to a drive</param>
    protected string GetDecryptedFilename(string filename, byte[] key, byte[] nonce)
    {
        string rfilename = filename.Replace("-", "/");
        if (Drive != null && nonce != null)
            throw new SecurityException("Filename nonce is already set by the drive");
        if (Drive != null && key != null)
            throw new SecurityException("Key is already set by the drive");

        if (key == null)
            key = this.encryptionKey;
        if (key == null && Drive != null)
            key = Drive.Key.DriveEncKey;
        string decfilename = TextDecryptor.DecryptString(rfilename, key, nonce);
        return decfilename;
    }

    /// <summary>
    ///  Return the encrypted filename of a virtual filename
	/// </summary>
	///  <param name="filename">The virtual filename</param>
    ///  <param name="key">     The encryption key if the file doesn't belong to a drive</param>
    ///  <param name="nonce">   The nonce if the file doesn't belong to a drive</param>
    protected string GetEncryptedFilename(string filename, byte[] key, byte[] nonce)
    {
        if (Drive != null && nonce != null)
            throw new SecurityException("Filename nonce is already set by the drive");
        if (Drive != null)
            nonce = Drive.GetNextNonce();
        if (Drive != null && key != null)
            throw new SecurityException("Key is already set by the drive");
        if (Drive != null)
            key = Drive.Key.DriveEncKey;
        string encryptedPath = TextEncryptor.EncryptString(filename, key, nonce);
        encryptedPath = encryptedPath.Replace("/", "-");
        return encryptedPath;
    }

    /// <summary>
    ///  Move file to another directory.
	/// </summary>
	///  <param name="dir">Target directory.</param>
    ///  <param name="OnProgressListener">Observer to notify when move progress changes.</param>
    ///  <returns>The moved file</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    override
    public AesFile Move(IVirtualFile dir, Action<long,long> OnProgressListener)
    {
        IFile newRealFile = RealFile.Move(dir.RealFile, null, OnProgressListener);
        return new AesFile(newRealFile, Drive);
    }

    /// <summary>
    ///  Copy a file to another directory.
	/// </summary>
	///  <param name="dir">Target directory.</param>
    ///  <param name="OnProgressListener">Observer to notify when copy progress changes.</param>
    ///  <returns>The new file</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    override
    public AesFile Copy(IVirtualFile dir, Action<long,long> OnProgressListener)
    {
        IFile newRealFile = RealFile.Copy(dir.RealFile, null, OnProgressListener);
        return new AesFile(newRealFile, Drive);
    }

    /// <summary>
    /// Copy a directory recursively
    /// </summary>
    /// <param name="dest">The destination directory</param>
    /// <param name="progressListener">The progress listener</param>
    /// <param name="AutoRename">The autorename function to use when renaming files if they exist</param>
    /// <param name="autoRenameFolders">Apply autorename to folders also (default is true)</param>
    /// <param name="OnFailed">Callback when copy fails</param>
    override
    public void CopyRecursively(IVirtualFile dest,
        Action<IVirtualFile, long, long> progressListener, Func<IVirtualFile, string> AutoRename,
        bool autoRenameFolders,
        Action<IVirtualFile, Exception> OnFailed)
    {
        Action<IFile, Exception> OnFailedRealFile = null;
        if (OnFailed != null)
        {
            OnFailedRealFile = (file, ex) =>
            {
                OnFailed(new AesFile(file, Drive), ex);
            };
        }
        Func<IFile, string> RenameRealFile = null;
        // use auto rename only when we are using a drive
        if (AutoRename != null && Drive != null)
            RenameRealFile = (file) => AutoRename(new AesFile(file, Drive));
        this.RealFile.CopyRecursively(dest.RealFile, (file, position, length) =>
        {
            if (progressListener != null)
                progressListener(new AesFile(file, Drive), position, length);
        },
            RenameRealFile, autoRenameFolders, OnFailedRealFile);
    }


    /// <summary>
    /// Move a directory recursively
    /// </summary>
    /// <param name="dest">The destination directory</param>
    /// <param name="progressListener">The progress listener</param>
    /// <param name="AutoRename">The autorename function to use when renaming files if they exist</param>
    /// <param name="autoRenameFolders">Apply autorename to folders also (default is true)</param>
    /// <param name="OnFailed">Callback when move fails</param>
    override
    public void MoveRecursively(IVirtualFile dest,
        Action<IVirtualFile, long, long> progressListener, Func<IVirtualFile, string> AutoRename,
        bool autoRenameFolders,
        Action<IVirtualFile, Exception> OnFailed)
    {
        Action<IFile, Exception> OnFailedRealFile = null;
        if (OnFailed != null)
        {
            OnFailedRealFile = (file, ex) =>
            {
                if (OnFailed != null)
                    OnFailed(new AesFile(file, Drive), ex);
            };
        }
        Func<IFile, string> RenameRealFile = null;
        // use auto rename only when we are using a drive
        if (AutoRename != null && Drive != null)
            RenameRealFile = (file) => AutoRename(new AesFile(file, Drive));

        this.RealFile.MoveRecursively(dest.RealFile, (file, position, length) =>
        {
            if (progressListener != null)
                progressListener(new AesFile(file, Drive), position, length);
        },RenameRealFile, autoRenameFolders, OnFailedRealFile);
    }

    /// <summary>
    /// Delete a directory recursively
    /// </summary>
    /// <param name="progressListener">The progress listener</param>
    /// <param name="OnFailed">Callback when delete fails</param>
    override
    public void DeleteRecursively(Action<IVirtualFile, long, long> progressListener, Action<IVirtualFile, Exception> OnFailed)
    {
        Action<IFile, Exception> OnFailedRealFile = null;
        if (OnFailed != null)
        {
            OnFailedRealFile = (file, ex) =>
            {
                if (OnFailed != null)
                    OnFailed(new AesFile(file, Drive), ex);
            };
        }
        this.RealFile.DeleteRecursively((file, position, length) =>
        {
            if (progressListener != null)
                progressListener(new AesFile(file, Drive), position, length);
        }, OnFailedRealFile);
    }


    /// <summary>
    ///  Returns the minimum part size that can be encrypted / decrypted in parallel
    ///  aligning to the integrity chunk size if available.
    /// </summary>
    public int GetMinimumPartSize()
    {
        int currChunkSize = this.FileChunkSize;
        if (currChunkSize != 0)
            return (int)currChunkSize;
        if (this.RequestedChunkSize > 0)
            return (int)this.RequestedChunkSize;
        return this.BlockSize;
    }


    /// <summary>
    /// Get an auto generated copy of the name for the file.
    /// </summary>
    /// <param name="file">The file</param>
    /// <returns>The new file name</returns>
    public static string AutoRename(IVirtualFile file)
    {
        string filename = IFile.AutoRename(file.Name);
        byte[] nonce = ((AesFile) file).Drive.GetNextNonce();
        byte[] key = ((AesFile)file).Drive.Key.DriveEncKey;
        string encryptedPath = TextEncryptor.EncryptString(filename, key, nonce);
        encryptedPath = encryptedPath.Replace("/", "-");
        return encryptedPath;
    }

    /// <summary>
    ///  File task progress class.
    /// </summary>
    public class FileTaskProgress
    {
        /// <summary>
        /// The file associated
        /// </summary>
        public IFile File { get; }

        /// <summary>
        /// Processed files
        /// </summary>
        public readonly long ProcessedBytes;

        /// <summary>
        /// Processed files
        /// </summary>
        public readonly long TotalBytes;

        internal FileTaskProgress(IFile file, long processedBytes, long totalBytes)
        {
            this.File = file;
            this.ProcessedBytes = processedBytes;
            this.TotalBytes = totalBytes;
        }
    }
}
