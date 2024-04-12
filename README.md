![alt text](https://github.com/mku11/Salmon-AES-CTR/blob/wip/common/common-res/icons/logo.png)

# Salmon
Salmon is an AES-256 CTR encryption library with HMAC SHA-256 integrity, parallel file operations (read/write), and seekable stream support. It provides a high level, low-ceremony, consistent API for encrypting streams and files. Salmon is using a fast native library for Intel x86 and ARM64 that you can include in your C/C++ projects.
  

[![License: MIT](https://img.shields.io/github/license/mku11/Salmon-AES-CTR.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-2.0.0-blue)](https://github.com/mku11/Salmon-AES-CTR/releases)
[![GitHub Releases](https://img.shields.io/github/downloads/mku11/Salmon-AES-CTR/latest/total?logo=github)](https://github.com/mku11/Salmon-AES-CTR/releases)

## Library Features
* AES-256 standard AES-256 in CTR Mode.
* Data integrity with standard HMAC SHA-256.
* Password based key derivation with PBKDF2 SHA-256.
* Fast native AES-NI intrinsics for Intel x86 and ARM64 for C/C++.
* Fast parallel encryption/decryption operations and seek support for streams with a simple API in Java and C#.
* Full filesystem API for creating virtual encrypted drives.
* Extensible API interfaces for other filesystems, network files, and custom streams (UWP, Xamarin iOS, Dropbox, etc).
* Protected file-based nonce sequencer with encrypted SHA-256 checksum.
* System protected nonce sequencer with encrypted SHA-256 checksum via an optional Windows Service.

## Applications

For a complete showcase of the Salmon API visit the [Salmon Vault](https://github.com/mku11/Salmon-Vault) app offered on several different platforms  
[**Live Web Demo**](https://mku11.github.io/Salmon-AES-CTR/demo)

## Support

**Languages**:  
Java 11+  
Android 23+  
C# .NET 7+  
Python 3.11+  
Typescript/Javascript ES2020  
  
**Platforms/Browsers**:  
Xamarin, MAUI, WPF  
JavaFX  
Chrome (Local Drives)  
Chrome, Firefox, Safari (Remote Drives read-only)  
  
**Operating Systems (Tested)**  
Windows 10 x86_64  
MacOS 10.11  
Linux Debian 11  

## Why Salmon?

* Native implementation: Fast AES-NI intrinsics subroutines.
* Parallel processing: Multithreaded encryption/decryption of content in CTR mode.
* Facade API: Read/Write/Seek through encrypted files and streams via a low ceremony API.
* Data Integrity: Interleaved signatures with data chunks removes the need of processing the whole content.
* No birthday problem: Strict nonce ranges for every authorized device eliminating random nonce collisions during encryption when using a Salmon Drive.
* Backups/Portability: Nonce sequencer ranges reside outside of the encryption space so you can backup your Salmon Drive as many times you want and decrypt them using any device.
* Protected nonce sequences: Sequence ranges are stored in app protected space (Android only).
* Tamper-Proof nonce sequences: Tampering is detected using an encrypted SHA256 checksum (Win10/11 only).
* Admin-Protected nonce sequences: Additional protection from non-admin users (Salmon Windows Service Win10/11 only).

## Salmon API Samples
**Java/C#/Python API**:
API usage is consistent across platforms with slight variations on naming conventions.  
  
**Typescript/Javascript API**:  
The ts/js libraries are based on async IO so make sure when to use await.  
  
For detailed samples see: [**Samples**](https://github.com/mku11/Salmon-AES-CTR/tree/main/samples)  
For an extensive use of the API see: [**Salmon Vault**](https://github.com/mku11/Salmon-Vault)  
  
For short code samples see below.

### SalmonFS API:
```
// Create a sequencer. Make sure this path is safe and does NOT get backed up!
String sequencerPath = "c:\\users\\<username>\\AppData\\Local\\<somefolder>\\salmon_sequencer.xml";
SalmonFileSequencer sequencer = new SalmonFileSequencer(new JavaFile(sequencerPath), new SalmonSequenceSerializer());

// create() or open() a virtual drive provided a location and a text password
// Supported drives: JavaDrive, DotNetDrive, PyDrive, JsDrive, JsHttpDrive (remote), JsNodeDrive (node.js)
SalmonDrive drive = JavaDrive.create(new JavaFile("c:\\path\\to\\your\\salmon\\vault"), password, sequencer);

// Create an importer with 2 threads for parallel processing
SalmonFileCommander commander = new SalmonFileCommander(256 * 1024, 256 * 1024, 2);

// and import multiple files
JavaFile[] files = new JavaFile[]{new JavaFile("data/file1.txt"), new JavaFile("data/file2.txt")};
commander.importFiles(files, drive.getRoot(), false, true, null, null, null);
commander.close();

// use the virtual filesystem API to list or directly get the files in the drive
SalmonFile root = drive.getRoot();
SalmonFile[] salmonFiles = root.listFiles();

// or retrieve a file by filename
SalmonFile file = root.getChild("file1.txt");

// get a stream that you can read/decrypt the data from
SalmonStream stream = file.getInputStream();
// stream.read(...);
// stream.seek(...);
stream.close();

// or use a SalmonFileInputStream with parallel processing and caching
SalmonFileInputStream inputStream = new SalmonFileInputStream(file, 2, 4 * 1024 * 1024, 2, 256 * 1024);
// inputStream.read(...);
inputStream.close();
drive.close();
```

### SalmonCore API: Data encryption/decryption
```
// To encrypt byte data or text without using a vault you need to generate your own key and nonce.
// Get a fresh secure random key and keep this somewhere safe.
byte[] key = SalmonGenerator.getSecureRandomBytes(32); // 256-bit key

// Also get a fresh nonce, nonce is not a secret thought it must only be used once per content!
byte[] nonce = SalmonGenerator.getSecureRandomBytes(8); // 64-bit nonce

// Generate some sample data to encrypt
byte[] bytes = SalmonGenerator.getSecureRandomBytes(1024);

// encrypt a byte array using 2 parallel threads
SalmonEncryptor encryptor = new SalmonEncryptor(2);
byte[] encBytes = encryptor.encrypt(bytes, key, nonce, false);
encryptor.close();

// decrypt:
SalmonDecryptor decryptor = new SalmonDecryptor(2);
byte[] decBytes = decryptor.decrypt(encBytes, key, nonce, false);
decryptor.close();

// Or encrypt a text string
nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
String encText = SalmonTextEncryptor.encryptString(text, key, nonce, true);

// Decrypt the encrypted string, no need to specify the nonce again since it's embedded in the data
String decText = SalmonTextDecryptor.decryptString(encText, key, null, true);
```

### Performance: AES-NI intrinsics
```
// To set the Salmon native intrinsics:  
SalmonStream.setAesProviderType(SalmonStream.ProviderType.AesIntrinsics);
```

### Portability: TinyAES library  
```
// If the platform/OS does not provide AES encryption algorithms and the hardware does not have AES-NI features you can use Salmon with the TinyAES library which is written in pure C code:
SalmonStream.setAesProviderType(SalmonStream.ProviderType.TinyAES);
```

### Portable Drives:
Drives are portable and backable and can be read/decrypted by any device that can run Salmon API. If you need to import files to a salmon virtual drive using a different device you need to first requires authorize it. 

The device that created the drive is by default authorized. Any authorized device can authorize other devices. The authorization works by allocating a new nonce range for each authorized device.
```
// Device authorization example:
// open a copy of the drive on the target device and get the authorization id
String authId = SalmonDrive.getAuthId();

// open the drive on the source (authorized) device and export an 
// authorization file with the authorization id of the target device
SalmonAuthConfig.exportAuthFile(drive, authId, new JavaFile("c:\\path\\to\\auth_file\\auth.slma"));

// finally import the authorization file in the target device
SalmonAuthConfig.importAuthFile(drive, new JavaFile("c:\\path\\to\\auth_file\\auth.slma"));
```

### Interoperability: Injectable stream wrappers
The C# implementation has a SalmonStream that inherits from .NET Stream so you can use it with other libraries or 3rd party code. For Java/Javascript/Typescript/Python you have to wrap the SalmonStream to built-in wrappers:   
* Java: InputStreamWrapper for SalmonStream and SalmonFileInputStream for SalmonFile
* Typescript/Javascript: ReadableStreamWrapper for SalmonStream and SalmonFileReadableStream for SalmonFile
* Python: BufferedIOWrapper for SalmonStream and SalmonFileInputStream for SalmonFile

### Native: C/C++  
Although there is no drive and stream support for C/C++ you can use the Salmon native AES-NI subroutines directly.
For a full working C++ sample see: [Samples](https://github.com/mku11/Salmon-AES-CTR/tree/main/samples)

## Package Management
To learn how to integrate the Salmon library packages into your project with Maven, Gradle, or VS Studio see [Package Management](https://github.com/mku11/Salmon-AES-CTR/docs/Package_Management.md)  

## Specifications
Want to know more about Salmon specs and subprojects?  
Click on [Salmon specifications and formats](https://github.com/mku11/Salmon-AES-CTR/tree/main/docs)   
For how to compile and build each subproject see README.md in its respective folder.

## Limitations
* Salmon streams are seekable only if the backed resource supports random access (disk, memory, network).
* Salmon API is not Thread Safe! If you want to use parallel processing you need to use SalmonEncryptor/SalmonDecryptor and SalmonFileImporter/SalmonFileExporter.
* Make sure that you never backup and restore the Nonce Sequencer files in your Windows Device! They are located in each user %LOCALAPPDATA%\\.salmon directory (including the LocalSystem user if you use the Salmon Windows Service). So make sure you exclude them from backups and restores.
* The Windows user sequencer files are not secure from other apps! Also do not share your device account with other users! Salmon will attempt to notify you if it encounters tampering on the sequencer though for additional security you should use the Salmon Windows Service which protects the sequencer under the LocalSystem space.
* Integrity is not supported for filenames only for file contents.
* Maximum guaranteed file size: 2^64 bytes or limited by the backed resource (disk, memory, network).
* Maximum drive file size: 2^64 bytes
* Maximum number of drive files: 2^62 (64 bit nonces used for the filename and the file contents.

## Contributions
Unfortunately I cannot accept any code contributions. Though, bug reports and security POCs are more than welcome!  
  
## License
Salmon is released under MIT Licence, see [LICENSE](https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE) file.
Make sure you read the LICENSE file and display proper attribution if you decide to use this software.
Dependency libraries from Github, Maven, and NuGet are covered by their own license  
see [NOTICE](https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE)  
