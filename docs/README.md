### Subprojects
#### Salmon  
Library provides AES256 encryption for bytearray, text, and seekable stream support for Java, C#, Android, and Xamarin Android.  
  
  
#### SalmonFS 
Library provides a virtual filesystem API with fast parallel read/write operations on encrypted files for Java, C#, Android, and Xamarin Android.   
   
   
#### SalmonNative  
Library provides a fast native C library with AES intrinsics  in C/C++ for Intel x86 64bit only. ARM64 is supported only in SalmonNativeAndroid.
  
  
#### SalmonWin
This package contains a file sequencer implementation for Windows featuring a tamper detection checksum verifier using the windows Registry.  
  
  
#### SalmonFSAndroid
Library provides support for Android including Storage Access Framework API.   


#### SalmonNativeAndroid  
Library provides a fast native C library for Android with AES-NI intrinsics in C/C++ for x86, x64, armv7, ARM64 architectures.  
    
  
#### SalmonService  
The Salmon Service provides a protected nonce sequencer running with elevated privileges. 
  
  
#### SalmonVault  
A file vault app using Salmon. Currently there are versions in JavaFX, C# WPF, and Android, and Xamarin Android.  
It features an explorer-like interface to browse and view, copy, move, delete, rename, import, and export encrypted files.  
There are builtin in-memory apps for viewing files with Image, Video, and Audio content.  
The android versions feature sharing files with external apps for editing and automatically re-import into the vault.  
  
  
### Specifications

#### SalmonAES

```
     UNENCRYPTED_DATA                                      ENCRYPTED_DATA

      -------------- ((AES256(CTR_X)) XOR (BLOCK_X))-------------
     |                                                           |
     |                                                           |
|   CTR_1  |   CTR_2  |...|         |...        |   HMAC_1  |  CTR_1   |  CTR_2   |...|  HMAC_2   |         |...
|____|_____|__________|...|         |...        |___________|____|_____|__________|...|___________|         |...
|  BLOCK_1 |  BLOCK_2 |...|         |...  <-->  |           |  BLOCK_1 |  BLOCK_2 |...|           |         |...
| 16 bytes | 16 bytes |...|         |...        | Optional  | 16 bytes | 16 bytes |...| Optional  |         |...
|__________|__________|...|_________|...        |___________|__________|__________|...|___________|_________|...
|       CHUNK_1           | CHUNK_2 |...        | HMAC_1    |       CHUNK_1           |  HMAC_2   | CHUNK_2 |...
|        256 kB           |  256 kB |...        | 32 Bytes <-        256 kB           | 32 Bytes <- 256 kB |...
|_________________________|_________|...        |___________|_________________________|___________|_________|...

```
* In CTR mode each CTR is transformed via AES256 and then XORed with its correspoding 16 byte block to produce a 16 byte block output.
* CTR is an increasing 16byte unsigned integral: CTR_N2 = CTR_N1 + 1
* The CTR mode operation is symmetrical so you can use the same operation to decrypt the encrypted data.
* After encryption an integrity HMAC hash (32 bytes) is calculated and written for every CHUNK (default is 256 kB)
* Before decryption the HMAC hash compared against every correspoding DATA_CHUNK to verify its integrity.
* CTR mode allows for random access at the BLOCK level if integrity is disabled.
* When HMAC integrity is enabled each CHUNK is independent of others thus allowing random access (seek) and parallelism at the CHUNK level.  
   
   
#### SalmonStream
```
				 ENCRYPTED DATA STREAM

                            |     CTR      |    CTR + 1   |...|...
____________________________|______________|______________|...|...
|              |            |   16 bytes   |   16 bytes   |...|...
|              |            |   BLOCK_1    |   BLOCK_2    |...|...
|______________|____________|______________|______________|...|...
|   optional   |  32 bytes  |        DATA_CHUNK_1             |...
|    HEADER    ->   HMAC1  <-         256 kB                  |...
|______________|____________|_________________________________|...
|              |                                              |...
|              |                RANDOM ACCESS                 |...
|              |                                              |...
|______________|______________________________________________|...

```
* SalmonStream optionally supports a standard Salmon header with unencrypted data (see SalmonFile format below) or you can include any data you want.
* Enabling HMAC integrity in the stream will interleave HMAC SHA256 hash signatures before the chunks as shown above.
* The first HMAC section includes the HMAC for the first chunk and the header data.
* HMAC is applied after the encrypted data in each chunk.
* Same HMAC key is used for all integrity chunks.
* The CHUNK_SIZE must be a multiple of the AES Block (16 bytes).
* Last chunk might have a length lesser than the CHUNK_SIZE, no padding applied.
* SalmonStream is NOT thread safe, if you need parallel processing see SalmonImporter and SalmonExporter.
* Always align your write buffers to the AES Block size.
* If you use integrity align your read/write buffers to the CHUNK_SIZE for better performance.
* If you don't use integrity align your read/write buffers to the AES Block size for better performance.
  
  
  
#### SalmonFile

You can use a SalmonFile to encrypt contents of a file.
SalmonFile follows the same format as the stream but with the addition of a standard Salmon header, see below.
```

NONCE is a incremental number from a sequence hosted in a sequence file in the user filesystem or served via the Salmon Windows Service.
This nonce will be the 8 high bytes of the CTR. 
CTR is the counter that is used for the AES algorithm to encrypt every 16 bytes of data.

CTR (16 bytes) = NONCE(8 bytes) || ZERO(8 bytes)
 
                                       ENCRYPTED FILE
                                         <file.ext>
 _____________________________________________ __________ ___________ __________ ___________
|                                             |          |           |          |           |
|               SALMON HEADER DATA            |   HMAC1 <-  CHUNK1   |  HMAC2  <-   CHUNK2  |
|_____________________________________________|__________|___________|__________|___________|    
| 3 bytes |  1 byte  |  4 bytes   |  8 bytes  | 32 bytes |           | 32 bytes |           |
|  MAGIC  | VERSION# | CHUNK_SIZE |   NONCE   |   HMAC   | FILE_DATA |   HMAC   | FILE_DATA |
|_________|__________|____________|___________|__________|___________|__________|___________|

```
* SalmonFile comes with a complete API for creating, deleting, copying, and moving files.
* Filenames may be the same for 2 or more files so when you export make sure you don't overwrite 
other files in the export directory
* If you use parallel processing you need to align to the CHUNK_SIZE. See SalmonImporter
  
  
  
#### SalmonDrive

SalmonDrive is a virtual drive that hosts encrypted files.
More specifically:
1) Takes advantage of the SalmonFile API.
2) Uses parallel processing to import and export files to the drive.
3) Handles configuration for the encryption keys and nonce generation.
4) Is portable so any device with Salmon can read/decrypt files. 

#### SalmonDriveConfig

```
                                                  DRIVE CONFIG FILE
                                                    <vault.slmn>
 _________________________________________________________ _______________________________________ ____________ 
|                                                         |                                       |            |
|                    HEADER                               |                ENC_DATA               |   HMAC     |
|_________ __________ ____________ ____________ __________|_____________ ____________ ____________|____________|
| 3 bytes |  1 byte  |  24 bytes  |  4 bytes   | 16 bytes |  32 bytes   |  32 bytes  |  16 bytes  |  32 bytes  |
|  MAGIC  | VERSION# |    SALT    | ITERATIONS |    IV    |  DRIVE KEY  |  HMAC KEY  |  DRIVE_ID  ->   HMAC    |
|_________|__________|____________|____________|__________|_____________|____________|____________|____________|

```
* The Master key is derived from the user text password using Pbkdf2 SHA256 providing the SALT and ITERATIONS.
* The DRIVE KEY, HMAC KEY, and NONCE SEQ are encrypted with the Master key.
* The DRIVE KEY is then used for file encryption and decryption.
* The HMAC key will be used to sign file contents and verify their data integrity.
* The NONCE contain at all times the next value that will serve as a nonce (part of the IV) for the next encrypted file.
* The CTR used for each file is a combination of 8 bytes NONCE and 8 zero bytes.
* The INTEGRITY section contains an HMAC signature of the encrypted nonce sequence for tampering prevention.
* The HMAC signature serves a dual purpose: a) verify that nonce has not been altered and b) a way to verify the user password.
* DRIVE_ID is generated during the creation of a new drive.
* APP_DEVICE_ID is generated during the first time the application runs one a device and is unique per application per device.
  
#### SalmonAuthConfig

```
                                                  AUTH CONFIG FILE
                                                    <salmon.slma>
 _________________________________________________________ ____________________________________________________ ____________ 
|                                                         |                                                    |            |
|                    HEADER                               |                ENC_DATA                            |   HMAC     |
|_________ __________ ____________ ____________ __________|_____________ ____________ ____________ ____________|____________|
| 3 bytes |  1 byte  |  24 bytes  |  4 bytes   | 16 bytes |  16 bytes   |  16 bytes  |   8 bytes  |  8 bytes   |  32 bytes  |
|  MAGIC  | VERSION# |    SALT    | ITERATIONS |    IV    |  DRIVE ID   |  AUTH ID   | NEXT_NONCE | MAX_NONCE  ->   HMAC    |
|_________|__________|____________|____________|__________|_____________|____________|____________|____________|____________|

```
* By default only authorized devices can import files to a drive as a scheme of ensuring non-reusable Nonces.
* Nonces are sequential and based on a scheme that allows ranges to be split and provided to another device.
* The Auth Config file is a file that is exported by an authorized device contain the ranges for the device to be authorized.
* The auth file can only be used once per drive per device.
* The AUTH ID of the new device needs to be provided during the Export Auth phase.
* The Auth config file is encrypted and can only be imported to a device that has opened successfully the drive.  
* An unauthorized device cannot import files only decrypt/view files.
  
  
  
#### SalmonSequenceConfig

```
                                           SEQUENCES CONFIG FILE 
                                               <config.xml>
 <?xml version="1.0" encoding="UTF-8"?>
 <!--WARNING! Do not edit this file, security may be compromised if you do so-->
 <drives>
    <drive id="..." authID="..." nextNonce="..." maxNonce="..."/>
 </drives>

```
* The sequences config file is used by a Sequencer to keep track of the next available Nonce for each of the drives that the device is authorized.
* The sequences config file should be kept in a secure place protected from unauthorized users!
* This file should not be backed up or restored to a previous state!
* For Android this is kept in the private non-backable data folder.
* For Windows use the SalmonService to ensure that it is out of reach of users of the system, see below.
* You can use a user space to save the sequence files though you need to make sure the files are secured and not eligible for backup.
* A backup and restore of the seqeunce file will restore the nonce sequencer to its previous state and reuse previous nonces! So do not backup this file.
  
#### SalmonWin
This provides an additional security feature since windows ACL does not provide per-app security. For Android a similar solution is obviously not needed since apps are running in isolation and have private storage.  
  
#### SalmonService
This is implemented as a LocalSystem service with the file sequencer under protected file storage. For Windows the sequence file is stored under <%LOCALAPPDATA%\.salmon> directory. For the LocalSystem account this is usually under: C:\Windows\SysWOW64\config\systemprofile\AppData\Local\\.salmon. You still need to make sure that the file is not eligible for backup and restore, the same security concerns apply here!  
  
