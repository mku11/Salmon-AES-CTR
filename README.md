![alt text](https://github.com/mku11/Salmon-AES-CTR/blob/wip/common/common-res/icons/logo.png)

## Salmon

Salmon is an AES-256 encryption library with built-in integrity, parallel operations, and seekable stream support.  
It provides a high level API for encrypting data, streams, and a virtual drive API for encrypting files.  
Powered by a fast native library for Intel x86_64 and ARM64.  
  
[![License: MIT](https://img.shields.io/github/license/mku11/Salmon-AES-CTR.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-2.2.0-blue)](https://github.com/mku11/Salmon-AES-CTR/releases)
[![GitHub Releases](https://img.shields.io/github/downloads/mku11/Salmon-AES-CTR/latest/total?logo=github)](https://github.com/mku11/Salmon-AES-CTR/releases)

**Library Features**  
AES-256 encryption in CTR Mode  
HMAC SHA-256 authentication  
SHA-256 PBKDF2 key derivation    
AES-NI for Intel x86 and ARM64  
AES GPU support with OpenCL  
Data and seekable stream encryption API  
Virtual file system API  
Nonce sequencer in app sandbox (Android Only)  
Nonce sequencer with SHA-256 chksum (Windows Only)  
Nonce sequencer via windows service (Windows Only)  
Experimental Spring Web Service for use with remote drives (Java and C# clients only)

### So why another encryption library?  
**Native implementation:**  
Fast native AES-NI intrinsics subroutines using SIMD for x86_64 and ARM64.  

**Parallel processing:**  
Fast multithreaded encryption and decryption of data and files.  

**Facade API:**  
Read/Write/Seek through encrypted streams and files via a low ceremony API.  
Random access is supported for both the stream and file system APIs.

**Data integrity:**  
Samon is using CTR with interleaved HMAC signatures so you don't have to verify the whole content.  
Seekable streams will simply fail when reading a data chunk that is tampered.  

**No birthday problem:**  
Salmon is using by default sequential nonces with strict ranges so no random nonces.  

**Backups:**  
Nonce sequence ranges reside outside of the virtual drive so you can safely keep fully operational clones and backups of your drive.  

**Portability:**  
You can import new files in virtual drives using multiple authorized devices.  
Move and decrypt files using any supported device.  

**Protected nonce sequences:**  
Sequence ranges are stored in app protected space (Android only).  
Any tampering is detected using an encrypted SHA256 checksum (Win10/11 only).  
Additional protection from non-admin users (Salmon Windows Service Win10/11 only).  

**Platform Support:**  
Salmon is implemented in several programming languages and supports most major platforms and operating systems.  

**Extensible API:**  
The implementation is based on abstract components which you can implement and inject easily in your custom solution.  
This make things easier if you want to support other file systems, or network files and cloud storage.  
You can also create your own secure nonce sequencers by implemented the interfaces provided.

**Comparison:**  
Salmon supports random access unlike GCM.  
Salmon supports authentication unlike XTS.  
Salmon supports hardware acceleration unlike Salsa20 and Chacha20.  

**Benchmarks**  
How fast is Salmon?  
  
jmh benchmark shows that salmon is almost 2x faster than OpenJDK 11 javax.crypto and 3x faster than Bouncy castle:  
```
Data size: 32MB  
Benchmark                                              Mode  Cnt   Score   Error  Units  
SalmonBenchmark.EncryptAndDecryptSalmonNativeAesIntr  thrpt       22.008          ops/s  
SalmonBenchmark.EncryptAndDecryptSysBouncyCastle      thrpt        6.457          ops/s  
SalmonBenchmark.EncryptAndDecryptSysDefault           thrpt       12.371          ops/s  
```
  
C# benchmark shows that salmon is 2x faster than .NET 7 System.Security.Cryptography:  

```
Data size: 32MB  
EncryptAndDecryptPerfSysDefault (System.Security.Cryptography)  
Time ms:  
 enc: 682  
 dec: 548  
 Total: 1230  
  
EncryptAndDecryptPerfSalmonNativeAesIntrinsics  
Time ms:  
 enc time: 253  
 dec time: 253  
 Total: 506  
```
 
Do you need more speed? Use SalmonEncryptor/SalmonDecryptor with multiple threads. 

**Languages:**  
Java 11+  
C# .NET 7+  
C/C++ (data only, no streams, no fs)  
Python 3.11+  
Typescript ESM/ES2020  
Javascript ESM/ES2020  
  
**Platforms:**  
JavaFX 17+  
Android 23+  
.NET Android 23+  
WPF, Xamarin, and MAUI  
Chrome, Firefox, Safari (Remote read-only drives)  
Chrome (Local read-write drives)  
Node.js (Remote read-only and Local read-write drives)  
  
**Operating Systems (Tested):**  
Windows 10 x86_64  
MacOS x86_64 10.11  
Linux Debian 11 x86_64  
Linux Debian 12 aarch64  

**CPU architectures (Tested):**  
Intel x86_64  
ARM64  

**Salmon API Samples**  
  
##### Salmon Core API: #####

```
// Simple encryption and decryption of byte array
byte[] key = SalmonGenerator.getSecureRandomBytes(32); // Generate a secure random key, keep this somewhere safe.
byte[] nonce = SalmonGenerator.getSecureRandomBytes(8); // Generate a nonce, this is not a secret though you need to use only once!
byte[] bytes = ..; // data you want to encrypt

SalmonEncryptor encryptor = new SalmonEncryptor(2); // use 2 threads to encrypt
byte[] encBytes = encryptor.encrypt(bytes, key, nonce, false);
encryptor.close();

SalmonDecryptor decryptor = new SalmonDecryptor(2); // use 2 threads to decrypt
byte[] decBytes = decryptor.decrypt(encBytes, key, nonce, false);
decryptor.close();
```

##### Salmon FS API: #####
```
// Create a sequencer. Make sure this path is secure and excluded from your backups.
String sequencerPath = "c:\\users\\<username>\\AppData\\Local\\<somefolder>\\salmon_sequencer.xml";
SalmonFileSequencer sequencer = new SalmonFileSequencer(new JavaFile(sequencerPath), new SalmonSequenceSerializer());

// create() or open() a virtual drive provided with a location and a text password
// Supported drives: JavaDrive, DotNetDrive, PyDrive, JsDrive, JsHttpDrive (remote), JsNodeDrive (node.js)
SalmonDrive drive = JavaDrive.create(new JavaFile("c:\\path\\to\\your\\virtual\\drive"), password, sequencer);
// you can now import files, create and list directories, for more info see the samples documentation
```

For more Java Samples including the Salmon FS API see: [Samples Documentation](https://github.com/mku11/Salmon-AES-CTR/tree/main/docs/Samples.md)  
For complete sample code for Java, C#, C, C++, Python, and JS: [Samples](https://github.com/mku11/Salmon-AES-CTR/tree/main/samples)  

For a showcase of the Salmon API visit [Salmon Vault App](https://github.com/mku11/Salmon-Vault)
or the [**Live Web Demo**](https://mku11.github.io/Salmon-Vault/demo).

**Salmon API Reference Documentation**  
The API ref documentation is not complete but nontheless helpful:  
[Java](https://mku11.github.io/Salmon-AES-CTR/docs/2.1.0/java/html/)
 | [C#](https://mku11.github.io/Salmon-AES-CTR/docs/2.1.0/csharp/html/namespaces.html)
 | [C](https://mku11.github.io/Salmon-AES-CTR/docs/2.1.0/c/html/files.html)
 | [JavaScript](https://mku11.github.io/Salmon-AES-CTR/docs/2.1.0/javascript/html/Base64.html)
 | [Python](https://mku11.github.io/Salmon-AES-CTR/docs/2.1.0/python/html/namespaces.html)

**Package Management**  
To learn how to integrate Salmon into your project with Maven, Gradle, or VS Studio see [Package Management](https://github.com/mku11/Salmon-AES-CTR/blob/main/docs/Package_Management.md)  

**Specifications**  
Want to know more about Salmon specs and subprojects?  
Click on [Salmon specifications and formats](https://github.com/mku11/Salmon-AES-CTR/tree/main/docs)   
For how to compile and build each subproject see README.md in its respective folder.

**Security Limitations**  
- User sequencer files are not secure from other apps by default.  
For Android: you must create sequencer files in protected app space and not on external storage! Android apps work on sandbox environments though for rooted devices there is no such protection.  
For Windows: you can create sequencer files preferably within %LOCALAPPDATA% folder. Windows will notify you if it detects tampering though it is recommended for additional security you use the Salmon Windows Service. The Salmon windows service is a better solution because it protects the sequencer files under a system administrator (LocalSystem) account.  
For Linux and Mac: make sure you keep the sequencer files in a safe place since salmon has no anti-tampering support for these OSes. What you can do is implement a secure nonce service or your own tampering detection! You can do that by extending SalmonFileSequencer class, for an example see WinFileSequencer.  
- Importing files to a salmon virtual drive using different devices requires authorization by an already authorized device for each  virtual drive. The device that created the drive is by default authorized. The authorization mechanism works by assigning new nonce ranges for each authorized device which prevents nonce reuse.
- Make sure that you never backup and restore the nonce sequencer files! You should always create them under a directory that is exluded from your backups, this will prevent nonce reuse!  
- Data integrity is not supported for filenames only for file contents.   

**Functional Limitations**  
- Salmon streams are seekable only if the backed resource supports random access (disk, memory, network).  
- Salmon API is not Thread Safe! If you want to use parallel processing you need to use SalmonEncryptor/SalmonDecryptor and SalmonFileImporter/SalmonFileExporter.  
- Maximum guaranteed file size: 2^64 bytes or limited by the backed resource (disk, memory, network).  
- Maximum drive file size: 2^64 bytes  
- Maximum number of drive files: 2^62 (64 bit nonces used for the filename and the file contents.  
- Python parallel processing (multiple cpus) in Windows is slow due to python being single-threaded.  
- Javascript service worker handler does not support parallel processing.  
- Javascript implementation is ESM so in order to work in Node.js you need to use --experimental-vm-modules  

**Contributions**  
Unfortunately I cannot accept any code contributions. Though, bug reports and security POCs are more than welcome!  
  
**License**  
Salmon is released under MIT Licence, see [LICENSE](https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE) file.
Make sure you read the LICENSE file and display proper attribution if you decide to use this software.
Dependency libraries from Github, Maven, and NuGet are covered by their own license  
see [NOTICE](https://github.com/mku11/Salmon-AES-CTR/blob/main/NOTICE)  
  