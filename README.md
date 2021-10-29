#### Salmon
**Salmon** is an AES 256bit CTR **encryption library for C#** with support for **seekable** streams. 
Salmon is currently **BETA software** which means this library **should NOT be used in production systems**
since it **may contain security bugs**. To learn more on what Salmon can do build and install the Salmon Vault 
for Android written in Xamarin C#. For more information scroll down.

Salmon is released under MIT Licence, see LICENSE file.

#### Library Features
* AES256 standard encryption in CTR Mode.
* Data Integrity with standard HMAC SHA256.
* Password based key derivation with Pbkdf2 SHA256.
* Parallel file processing.
* Support for .NET seekable streams.
* File Encryption for .NET files.
* File Vault implementation with Import/Export utilities.
* IRealFile interface provided for support with other filesystems: UWP, Android, IOS, etc.
* A file vault demo implementation for Android written in Xamarin C#.

#### SalmonStream
```
// Example: Decrypt and write data to a .Net FileStream
FileStream fileStream = ...;
SalmonStream stream = new SalmonStreamWriter(key, nonce, EncryptionMode.Encrypt, fileStream);
stream.Write(plainText,...);

// Example: Decrypt data from a .Net FileStream
FileStream fileStream = ...;
SalmonStream stream = new SalmonStream(key, nonce, EncryptionMode.Decrypt, fileStream);
stream.Read(...);
```

#### SalmonStream format
```
        12bytes  +   4bytes
  CTR =  NONCE  || 0x00000000           CTR       CTR + 1                          CTR + 2      CTR + 3
_____________________________________________________________________________________________________________
|             |                   |  16 bytes  |  16 bytes  |                   |  16 bytes  |  16 bytes  |
|             |                   | AES BLOCK1 | AES BLOCK2 |                   | AES BLOCK3 | AES BLOCK4 |...
|_____________|___________________|____________|____________|___________________|____________|____________|__
|  optional   | optional 32 bytes |          DATA           | optional 32 Bytes |          DATA           |
| HEADER DATA-> HMAC1 signature   <-   CHUNK1 (256 kbytes)  |  HMAC2 signature <-   CHUNK2 (256 kbytes)   |...
|_____________|___________________|_________________________|___________________|_________________________|__
|             |                                                                                           |
|             |                                         READ/WRITE                                        |...
|             |                         BUFFER multiple of 16 bytes or 256 kbytes                         |
|_____________|___________________________________________________________________________________________|__

```
Notes:
* If you enable HMAC integrity the stream will interleave HMAC SHA256 hash signatures before the chunks.
* The HMAC signature is always in front of the data chunk!
* First HMAC hash includes the header data during computation!
* HMAC is applied over the encrypted data in each chunk
* Same HMAC key is used for all integrity chunks
* Last chunk might have a length lesser than the chunk size.
* SalmonStream is NOT thread safe!
* There is support for buffering the streams so large buffers are prefered.
* If you use integrity align your read/write buffers to the Chunk size for better performance
* If you don't use integrity align your read/write buffers to the AES Block size for better performance


#### SalmonFile

```
// create the encrypted file
IRealFile realDir = new DotNetFile(outputDir);
SalmonFile dir = new SalmonFile(realDir);
string filename = "test.txt";
SalmonFile newFile = dir.CreateFile(filename, key, filenameNonce, fileNonce);

// optionally enable applying integrity while writing
newFile.SetApplyIntegrity(true, hmacKey, requestChunkSize: chunkSize);

// write file contents
Stream stream = newFile.GetOutputStream();
byte[] testBytes = System.Text.UTF8Encoding.Default.GetBytes(text);
stream.Write(testBytes);
stream.Flush();
stream.Close();
            
// open the encrypted file
string realFilePath = newFile.GetRealFile().GetAbsolutePath();
IRealFile realFile = new DotNetFile(realFilePath);
SalmonFile readFile = new SalmonFile(realFile);
readFile.SetEncryptionKey(key);
readFile.SetRequestedNonce(fileNonce);

// optionally enable verifying integrity while reading
readFile.SetVerifyIntegrity(true, hmacKey);

// read the contents
SalmonStream inStream = readFile.GetInputStream();
byte[] textBytes = new byte[testBytes.Length];
inStream.Read(textBytes, 0, textBytes.Length);
string textString = System.Text.UTF8Encoding.Default.GetString(textBytes);

```



#### SalmonFile format

The encrypted contents follow the same format as the stream, see above with the addition of a file header.
```
_____________________________________________________________________________________________________________________
|                                             |                             CONTENTS                              |
|               FILE HEADER DATA              | HMAC1 | CHUNK1 | HMAC2 | CHUNK2 | HMAC3 | CHUNK3 | HMAC4 | CHUNK4 |...
|_____________________________________________|_________________________________|_________________________________|__               
| 3 bytes |  1 byte  |  4 bytes   | 12 bytes  |                                 |                                 |
|  MAGIC  | VERSION# | CHUNK SIZE |   nonce   |              PART1              |              PART2              |...
|_________|__________|____________|___________|_________________________________|_________________________________|__

```

Notes:
* SalmonStream optionally supports a header if you want to stream additional data int the header like the 
initial counter and the HMAC or any other custom data. 
* Filenames may be the same for 2 or more files so when you export make sure you don't overwrite 
other files in the export directory
* Maximum file size supported is ~68GB, 68,719,476,736 bytes

#### SalmonFile parallel processing

SalmonFiles can be broken up to parts and processed in parallel.
Make sure you also align the part start and buffer sizes to either the Chunk size if you use integrity otherwise the AES block size.

```
//Example:
//Create multiple threads then seek to different positions in the stream and start reading.
SalmonStream stream1 = file.GetOutputStream();
stream1.Position = 256 * 1024;
stream1.Read(...);
```
See the unit test cases or the SalmonFileImporter/Exporter for a complete example.

#### Virtual Filesystem C# API
The API is simple and supports the most common File operations: list a directory, create files, open for read/write, etc.
To learn more about how to use the API view the Unit Test case under the Test folder.

#### Virtual Drive
SalmonDrive is provided as a virtual drive that:
1) Takes advantage of the Virtual filesystem API, see above.
2) Uses parallel processing to import and export files to the drive.
3) Handles configuration for the encryption keys and nonce generation.

```
// Example creating a SalmonDrive and a file under the virtual root
// To create a virtual file drive
SalmonVault.SetDriveClass(typeof(DotNetDrive));
SalmonVault.SetDriveLocation("d:\\virtualdrive\\..");

// set a new password
SalmonVault.GetDrive().SetPassword(pass);

// or authenticate if it already exists
// SalmonVault.GetDrive().Authenticate(pass);

// enable integrity 
SalmonVault.GetDrive().SetEnableIntegrityCheck(integrity);

// get the root directory and create a file then write to it
RootDir = SalmonVault.GetDrive().GetVirtualRoot();
SalmonFile file = RootDir.CreateFile("test.txt");
SalmonStream stream = file.GetInputStream();
stream.Write(...);
stream.Flush();
stream.Close();
```

To learn more about how to use view the Unit Test case under the Test folder.

#### Virtual Drive Config Format

Each virtual drive contains a configuration file with all the encryption properties to encrypt / decrypt your files.

```
________________________________________________________________________________________________________________
|                                                                                                               |
|                                                   CONFIG FILE                                                 |
|_______________________________________________________________________________________________________________|
|                                                         |                                         |           |
|                    HEADER                               |            ENCRYPTED DATA               | INTEGRITY |
|_________________________________________________________|_________________________________________|___________|
| 3 bytes |  1 byte  |  24 bytes  |  4 bytes   | 16 bytes |  32 bytes   |  32 bytes  |  12 bytes    |  32 bytes |
|  MAGIC  | VERSION# |    SALT    | ITERATIONS |    IV    |  DRIVE KEY  |  HMAC KEY  |  NONCE SEQ  -> HMAC SIGN |
|_________|__________|____________|____________|__________|_____________|____________|______________|___________|

```

Notes:
* The master key is derived from the user text password using Pbkdf2 SHA256 providing the salt and iterations.
* The DRIVE KEY, HMAC KEY, and NONCE SEQ have been encrypted with the master key.
* The DRIVE KEY will be used for file encryption / decryption.
* The HMAC key will be used to sign file contents and verify their data integrity.
* The NONCE SEQ will contain at all times the next value that will serve as the nonce for the next file that will created inside the vault.
* The NONCE SEQ higher 8 bytes are random and the lowest 4 are an incremental value starting from zero to accomodate a large enough range.
* The integrity section contains an HMAC signature of the encrypted nonce sequence.
* The HMAC signature will verify that nonce has not been altered and a way to validate the user password.

#### Support for other filesystems
To use with other filesystems (ie UWP, Xamarin, IOS, Android, Silverlight, etc) or network files
you need to implement the RealFile interface as well as extend the SalmonDrive.
For a working implentation you can check the built in .Net implementation and the Android implementation 
inside the Android app demo.

#### Salmon Vault for Android
A sample Salmon Android app written in C# Xamarin is also included. The Salmon app demonstrates a  
portable file vault with support for external SD cards. The file vault also features In memory Video/Audio Player and Image/Text 
Viewer for encryption files. File sharing and editing capabilities with external apps is also provided with a limited size.

Editing Files with External Apps:   
External Android Apps should use Intent filters with Action EDIT (not VIEW) inside their Manifest.   
The files are temporarily decrypted and stored within the Android app private cache directory thus being inaccessible by other apps only to the app the user has selected.    
The DocumentFile is then retrievable 
by the standard Android Storage Access Framework.

Example:
```
// A receiving app can open the file as a content Uri and read/write like so:
DocumentFile documentFile = DocumentFile.fromSingleUri(this, (Uri) b.get("android.intent.extra.STREAM"));
ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(documentFile.getUri(), "w");
FileOutputStream outs = new FileOutputStream(pfd.getFileDescriptor());
outs.Write(...);
outs.Flush();
outs.Close();
```

The Salmon app will detect any changes made in the file contents and reimport the file into the vault.
