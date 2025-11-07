## Salmon

Salmon is an AES-256 CTR encryption library with built-in integrity, parallel operations, and seekable stream support. It provides a high level API for encrypting data, byte streams, and a virtual drive API for encrypted local and remote files. Optimized for Intel x86_64, ARM64, and GPU cards.
  
[![License: MIT](https://img.shields.io/github/license/mku11/Salmon-AES-CTR.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-3.0.2-blue)](https://github.com/mku11/Salmon-AES-CTR/releases)
[![GitHub Releases](https://img.shields.io/github/downloads/mku11/Salmon-AES-CTR/latest/total?logo=github)](https://github.com/mku11/Salmon-AES-CTR/releases)

## Library Features  
- AES-256 encryption in CTR Mode  
- HMAC SHA-256 authentication  
- SHA-256 PBKDF2 key derivation  
- CPU AES-NI acceleration for Intel x86 and ARM64  
- GPU AES acceleration with OpenCL  
- Data and seekable stream encryption API  
- File system API for encrypted virtual drives  
- Protected nonce sequencers (limited)  
- Support for network drives HTTP (readonly) and Web Service (experimental)  
 
## Live Web Demo
![alt text](https://github.com/mku11/Salmon-Vault/blob/main/screenshots/Screenshot.png)  
Access to live web demo: [**Live Web Demo**](https://mku11.github.io/Salmon-Vault/demo.html)  
Demo Vault files are licensed under [Content License](https://mku11.github.io/Salmon-Vault/vault/content_license.txt)  

\* Access to the demo vault is remote and read-only so you won't be able to import new files.  
\* Local drives are only available using Chrome.  

## Salmon API Support  
**Languages:**  
- Java 11+  
- C# .NET 8+  
- Python 3.11+  
- Typescript ESM/ES2020  
- Javascript ESM/ES2020  
- C/C++ (data encryption API only, no streams)  
  
**Platforms:**  
- JavaFX 17+  
- .NET WPF  
- .NET Xamarin and MAUI  
- Android 23+ (No GPU-accel)  
- .NET Android 23+ (No GPU-accel)  
- Chrome (default JS crypto only)  
- Firefox, Safari (No Local drives / default JS crypto only)  
- Node.js (ESM modules / default JS crypto only)  
  
**Operating Systems:**  
- Windows 10+ x86_64  
- MacOS 10.11+ x86_64  
- Linux/Debian/Ubuntu x86_64, aarch64  
- Android 23+ ARM  

**Acceleration:**
- Intel AES-NI SIMD x86_64  
- ARM64 SIMD aarch64
- GPU OpenCL 1.2+

#### Salmon Core API: ####

```
// Simple encryption and decryption of byte array
byte[] key = Generator.getSecureRandomBytes(32); // Generate a secure key, keep this somewhere safe!
byte[] nonce = Generator.getSecureRandomBytes(8); // Generate a nonce, you must NOT reuse this again for encryption!
byte[] bytes = ..; // data you want to encrypt

// use 2 threads for encryption
Encryptor encryptor = new Encryptor(2);
byte[] encBytes = encryptor.encrypt(bytes, key, nonce);
encryptor.close();

// use 2 threads for decryption
// the nonce is already embedded
Decryptor decryptor = new Decryptor(2); 
byte[] decBytes = decryptor.decrypt(encBytes, key);
decryptor.close();

// Or encrypt a text string
nonce = Generator.getSecureRandomBytes(8); // always get a fresh nonce!
String encText = TextEncryptor.encryptString(text, key, nonce);

// Decrypt the encrypted string, no need to specify the nonce again since it's embedded in the data
String decText = TextDecryptor.decryptString(encText, key);
```

#### Salmon FS API: ####
```
// Create a file-based nonce sequencer. 
// Make sure this path is secure and excluded from your backups.
String sequencerPath = "c:\\users\\<username>\\AppData\\Local\\<somefolder>\\salmon_sequencer.xml";
FileSequencer sequencer = new FileSequencer(new File(sequencerPath), new SequenceSerializer());

// create/open a virtual drive provided with a location and a text password
// Supported drives: Drive, HttpDrive, WSDrive, NodeDrive (node.js)
AesDrive drive = Drive.create(new File("c:\\path\\to\\your\\virtual\\drive"), password, sequencer);

// get root directory and list files
AesFile root = drive.getRoot();
AesFile[] files = root.listFiles();

// import files:
AesFileCommander commander = new AesFileCommander();
File[] newFiles = new File[]{new File("myfile.txt")};
commander.importFiles(newFiles, root);

// read a file:
AesFile file = root.getChild("myfile.txt");
RandomAccessStream stream = root.getInputStream();
stream.close();
```

For complete samples for Java, C#, C, C++, Python, and JS:  
[Samples](https://github.com/mku11/Salmon-AES-CTR/tree/main/samples)  
  
Documentation:  
[Samples Documentation](https://github.com/mku11/Salmon-AES-CTR/tree/main/docs/Samples.md)  
  
For a showcase of the Salmon API for multiple platforms (JavaFx, WPF, Android, Web) visit:  
[**Salmon Vault App**](https://github.com/mku11/Salmon-Vault)  

#### Salmon API Reference Documentation ####
The API ref documentation is now almost complete:  
[Java/Android](https://mku11.github.io/Salmon-AES-CTR/docs/3.0.2/java/html/)
 | [C#/.NET/Android](https://mku11.github.io/Salmon-AES-CTR/docs/3.0.2/csharp/html/namespaces.html)
 | [C](https://mku11.github.io/Salmon-AES-CTR/docs/3.0.2/c/html/files.html)
 | [JavaScript](https://mku11.github.io/Salmon-AES-CTR/docs/3.0.2/javascript/html)
 | [TypeScript](https://mku11.github.io/Salmon-AES-CTR/docs/3.0.2/typescript/html)
 | [Python](https://mku11.github.io/Salmon-AES-CTR/docs/3.0.2/python/html/namespaces.html)


### Why another encryption library?  
- Blazing fast CPU and GPU acceleration.  
- Parallel baked-in encryption and decryption of data arrays, streams, and files.  
- Low ceremony API for read/write/seek operations.  
- Data integrity with interleaved HMAC signatures with a per-chunk authentication scheme.
- Use of sequential nonces with strict ranges so no birthday problem.
- Nonce sequences reside outside of encrypted drives so you can have multiple fully operational clones and backups.  
- Import new files in virtual drives using multiple authorized devices.  
- Sequence ranges are stored in app protected space (Android only).  
- Any tampering is detected using an encrypted SHA256 checksum (Win10/11 only).  
- Additional protection from non-admin users (Salmon Windows Service Win10/11 only).  
- API for several popular programming languages, platforms, and operating systems.  
- Implementation is based on abstract components which can easily be extended.  

### Comparison: ###
- Salmon uses AES-CTR which supports random access unlike GCM.  
- Salmon uses HMAC-SHA256 which supports authentication unlike XTS.  
- Salmon uses AES which supports hardware acceleration unlike Salsa20 and Chacha20.  
  
**Benchmarks**  
How fast is Salmon?  
  
jmh benchmark shows that salmon is almost 2x faster than OpenJDK 11 javax.crypto and 3x faster than Bouncy castle:  
```
CPU: Intel i7 @2.00GHz
Data size: 32MB  
Threads: 1

Benchmark                                              Mode  Cnt   Score   Error  Units  
SalmonBenchmark.EncryptAndDecryptSalmonNativeAesIntr  thrpt       22.008          ops/s  
SalmonBenchmark.EncryptAndDecryptSysBouncyCastle      thrpt        6.457          ops/s  
SalmonBenchmark.EncryptAndDecryptSysDefault           thrpt       12.371          ops/s  
```
  
C# benchmark shows that salmon is 2x faster than .NET 7 System.Security.Cryptography:  

```
CPU: Intel i7 @2.00GHz
Data size: 32MB  
Threads: 1

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

In case you need more speed Salmon has baked-in multithreaded read/write operations, see API samples below. 

### Sequence Files ###
User sequencer files keep information about the sequence ranges so they need to be kept in a protected space. Someone who has **write access to a sequencer file** and **repeated read access to your encrypted files** can leak encrypted information about your files. Also make sure that you never backup and restore the nonce sequencer files! You should always create them under a directory that is exluded from your backups, this will prevent nonce reuse!  

More specifically:  
- **Android**: You must create sequencer files in protected app space and not on external storage! Android apps work on sandbox environments so the sequence files are protected from other apps. Though for rooted devices there is no such guarantee since any app can run as a superuser.  
- **Windows**: You should create a sequencer file under %LOCALAPPDATA% folder. Salmon for Windows will detect if a file is tampered with though it is recommended for additional security that you use the Salmon Windows Service. The Salmon Service is a better solution because it protects the sequencer files under a system administrator (LocalSystem) account.  
- **Linux/Mac**: You can create a sequencer file under your $HOME folder. Keep in mind that Salmon has no anti-tampering support for these operating systems. Therefore, if you want to prevent other apps having access to the sequencer file you can do is implement a protected service like the equivalent of Salmon Windows Service or create your own tampering detection. You can do the latter by extending SalmonFileSequencer class, see WinFileSequencer implementation as an example.  

### Device Authorization ###
Importing files to a salmon virtual drive using different devices requires authorization by an already authorized device for each  virtual drive. The device that created the drive is by default authorized. The authorization mechanism works by assigning new nonce ranges for each authorized device which prevents nonce reuse.

### Data integrity ###
Salmon can inform if an encrypted file is tampered with. The verification works with HMAC-SHA256 only for file content ranges that are currently accessed so it allows fast random access without reading the whole file.

### Functional Limitations ###
- Salmon streams are seekable only if the backed resource supports random access (disk, memory, network).  
- Salmon API is not Thread Safe! If you want to use parallel processing you need to use SalmonEncryptor/SalmonDecryptor and SalmonFileImporter/SalmonFileExporter.  
- Maximum guaranteed file size: 2^64 bytes or limited by the backed resource (disk, memory, network).  
- Maximum drive file size: 2^64 bytes  
- Maximum number of drive files: 2^62 (64 bit nonces used for the filename and the file contents.  
- Python parallel processing (multiple cpus) in Windows is slow due to python being single-threaded.  
- Javascript implementation uses only the default platform crypto and not the salmon native implementation.
- Javascript service worker handler does not support parallel processing.  
- Javascript implementation is ESM so in order to work in Node.js you need to use --experimental-vm-modules  
- Data integrity works with file contents but not filenames.  

### Specifications ###
Salmon specs and subprojects:
[Salmon specifications and formats](https://github.com/mku11/Salmon-AES-CTR/tree/main/docs).  
   
There are also plenty of README files under project folder on how to test build, and package.  
To make things even easier there are scripts under the scripts folders to test, deploy, and generate docs:  
[Scripts](https://github.com/mku11/Salmon-AES-CTR/tree/main/scripts).  


### Package Management ###
To integrate Salmon into your project with Maven, Gradle, VS Studio, or VS Code:  
[Package Management](https://github.com/mku11/Salmon-AES-CTR/blob/main/docs/Package_Management.md)  

### Contributions ###
Unfortunately I cannot accept any code contributions.  
Though, bugs and security reports are more than welcome!  
If you have a request for a new features open an issue.  

### License ###
Salmon is released under MIT Licence, see [LICENSE](https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE) file.  
Make sure you read the LICENSE file and display proper attribution if you decide to use this software.  
Dependencies are covered by their own license see [NOTICE](https://github.com/mku11/Salmon-AES-CTR/blob/main/NOTICE)  
  
