# Salmon
Salmon BETA is an encryption AES256 CTR library with HMAC SHA256 integrity capabilities. Currently it written in Java and C# but it also offers an native C low level implemenation for Intel x86 and ARM64 intrinsics. There is also a BETA app in JavaFX, C# WPF, Android, and Xamarin-Android that demonstrate what Salmon can do.

### Library Features
* AES256 standard encryption in CTR Mode.
* Data chunk integrity with standard HMAC SHA256.
* Password based key derivation with Pbkdf2 SHA256.
* AES256 intrinsics written in C for Intel x86 and ARM64.
* Seekable stream implementations for Java, C#, and Android.
* File Encryption with parallel read/write operations.
* Interfaces provided for support with other filesystems (ie UWP, Xamarin iOS, etc).

### Notice
Salmon is currently in **BETA** which means this library should **NOT** be used in production systems since it may contain several security bugs. 

### Limitations
* Maximum numbers of files in a SalmonDrive: 4,294,967,295 bytes
* Maximum size of file: 16*256^7 bytes
* **WARNING** about the SalmonDrive implementation only. Do NOT IMPORT files into MULTIPLE CLONES of the same Salmon drive. Someone that has access to both CLONES can decrypt the contents. Keep only ONE INSTANCE which you can MOVE (DO NOT COPY) to other devices. If you wish to keep a backup of the drive in another device you can do that but do NOT IMPORT any files into the backup drive. If you need to restore the backup drive just export all files, create a new drive, and then import everything into the new drive.

### License
Salmon is released under MIT Licence, see LICENSE file.
Make sure you read the LICENSE file and display proper attribution if you decide to use this software.
Dependency libraries from Github, Maven, and NuGet are covered by their own license.

# Subprojects  
### SalmonFS
SalmonFS provides an easy file encryption and virtual filesystem API with multithreaded read/write file support.

### SalmonNative
SalmonNative provides a performant native C library with AES intrinsics for Intel x86 32/64bit, and ARM 64bit.

### SalmonVault
SalmonValut is an app that implements a file vault using Salmon. Currently there are versions in JavaFX, C# WPF, and Android Java and Xamarin. It features an explorer like interface for navigating, importing, and exporting files. There are also builtin apps for viewing encrypted files with Image, Video, and Audio contents as well as a Text Editor for editing encrypted files. The android version also features editing capabilities with external apps.

### Specifications
#### SalmonStream
```

                         |   CTR      |   CTR + 1  |...|                   |   CTR + n  | CTR + n+1  |...|
_________________________|____________|___________ |...|___________________|____________|____________|...|
|             |          |  16 bytes  |  16 bytes  |...|                   |  16 bytes  |  16 bytes  |...|
|             |          | AES BLOCK1 | AES BLOCK2 |...|                   | AES BLOCK3 | AES BLOCK4 |...|
|_____________|__________|____________|____________|...|___________________|____________|____________|...|
|  optional   | 32 bytes |       DATA CHUNK1       |...|     32 Bytes      |       DATA CHUNK2       |...|
| HEADER DATA ->  HMAC1 <-        256 kbytes       |...|  HMAC2 signature <-        256 kbytes       |...|
|_____________|__________|_________________________|___|___________________|_________________________|___|
|             |                                                                                          |
|             |                                         READ/WRITE                                       |
|             |                         BUFFER multiple of 16 bytes, default: 256 kbytes                 |
|_____________|__________________________________________________________________________________________|

```
Notes:
* SalmonStream optionally supports a header if you want to include the IV or any other data. 
* Enabling HMAC integrity in the stream will interleave HMAC SHA256 hash signatures before the chunks as shown above.
* The first HMAC section includes the HMAC for the first chunk and the header data.
* HMAC is applied after the encrypted data in each chunk.
* Same HMAC key is used for all integrity chunks.
* Last chunk might have a length lesser than the chunk size, no padding applied.
* SalmonStream is NOT thread safe, if you need parallel processing see SalmonImporter and SalmonExporter.
* If you use integrity align your read/write buffers to the chunk size for better performance.
* If you don't use integrity align your read/write buffers to the AES Block size for better performance.

#### SalmonFile

You can use a SalmonFile to encrypt contents of a file.
SalmonFile follows the same format as the stream but with the addition of data into the header, see below.
```

FS_NONCE is a sequence hosted in the virtual drive and it is incremented for each file that is encrypted
This nonce will be the 8 high bytes of the CTR. 
CTR is the counter that is used for the AES algorithm to encrypt every 16 bytes of data.

       8 bytes + 8 bytes
CTR = FS_NONCE + 0x0000000000000000

 _____________________________________________ _________________________________
|                                             |                                 |
|               FILE HEADER DATA              | HMAC1 | CHUNK1 | HMAC2 | CHUNK2 |
|_____________________________________________|_________________________________|    
| 3 bytes |  1 byte  |  4 bytes   |  8 bytes  |                                 |
|  MAGIC  | VERSION# | CHUNK SIZE | FS_NONCE  |         FILE CONTENTS           |
|_________|__________|____________|___________|_________________________________|

```

Notes:
* SalmonFile comes with a complete API for creating, deleting, copying, and moving files.
* Filenames may be the same for 2 or more files so when you export make sure you don't overwrite 
other files in the export directory
* If you use parallel processing you need to align to the chunk size. See SalmonImporter

#### SalmonDrive
SalmonDrive is a virtual drive that takes care of the maintenance of hosting encrypted files.
More specifically:
1) Takes advantage of the SalmonFile API.
2) Uses parallel processing to import and export files to the drive.
3) Handles configuration for the encryption keys and nonce generation.

#### SalmonDriveConfig

```
 _______________________________________________________________________________________________________________
|                                                                                                               |
|                                                   CONFIG FILE                                                 |
|_________________________________________________________ _________________________________________ ___________|
|                                                         |                                         |           |
|                    HEADER                               |            ENCRYPTED DATA               | INTEGRITY |
|_________ __________ ____________ ____________ __________|_____________ ____________ ______________|___________|
| 3 bytes |  1 byte  |  24 bytes  |  4 bytes   | 16 bytes |  32 bytes   |  32 bytes  |    8 bytes   |  32 bytes |
|  MAGIC  | VERSION# |    SALT    | ITERATIONS |    IV    |  DRIVE KEY  |  HMAC KEY  |   FS_NONCE   ->   HMAC   |
|_________|__________|____________|____________|__________|_____________|____________|______________|___________|

```

Notes:
* The Master key is derived from the user text password using Pbkdf2 SHA256 providing the SALT and ITERATIONS.
* The DRIVE KEY, HMAC KEY, and NONCE SEQ are encrypted with the Master key.
* The DRIVE KEY is then used for file encryption and decryption.
* The HMAC key will be used to sign file contents and verify their data integrity.
* The FS_NONCE contain at all times the next value that will serve as a nonce (part of the IV) for the next encrypted file.
* The IV used for each file is a combination of 8 bytes FS_NONCE and 8 bytes for the CTR.
* The INTEGRITY section contains an HMAC signature of the encrypted nonce sequence for tampering prevention.
* The HMAC signature serves a dual purpose: a) verify that nonce has not been altered and b) a way to validate the user password.

#### SalmonNative
SalmonNative contains low level routines utilizing AES hardware acceleration for Intel and ARM processors.
Notes:
* AES Support only for Intel x86 32 and 64 bit and ARM 64 bit (NEON enabled).
* TinyAES implemetation of key expansion is used.
* There is no stream implementation for C++ only basic buffer encryption/decryption in C that you can link to.

#### Examples
See unit test cases under folders java/test and csharp/test or the SalmonVault app for usage examples.
