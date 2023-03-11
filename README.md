![alt text](https://github.com/mku11/Salmon/blob/main/common/common-res/icons/logo.png)
# Salmon
Salmon is an encryption AES256 CTR library with HMAC SHA256 integrity, seekable stream support, and parallel file operations (read/write). It provides a stream and virtual filesystem API written in Java and C#, a secure sequential Nonce generator, and fast C subroutines using Intel x86 and ARM64 intrinsics. The Salmon Vault application demonstrates the library capabilities for different platforms: JavaFX, WPF, Android, and Xamarin-Android.

### Library Features
* AES256 standard encryption with CTR Mode.
* Data chunk integrity with standard HMAC SHA256.
* Password based key derivation with Pbkdf2 SHA256.
* Encryption subroutines with AES256 intrinsics for Intel x86 and ARM64 for C/C++.
* Byte array, Text, and Stream encrypt/decrypt methods for C#, Xamarin, Java, and Android.
* Seek support for encrypted streams and files.
* Implementation of Sequential Nonce Generator as protected Windows Service.
* Virtual filesystem API for portable and backable virtual drives with strict Nonce ranges that protect from repeated access based attacks.
* Fast parallel read/write operations on files.
* In-memory media playback with seek support and parallel read buffers for performance.
* Extensible API interfaces for use with other filesystems and network files (UWP, Xamarin iOS, Dropbox, etc...).

### WARNING
Salmon is currently in **BETA** stage which means this library should **NOT** be used in production systems since it may contain several security bugs and design/implementation are very likely to change. 

### Limitations
* Salmon streams and files are seekable only if the backed resource supports random access (disk, memory, network).
* Salmon streams and files are not Thread Safe! If you want to use parallel processing you need to open multiple SalmonStream or SalmonFile that point to the same resource (disk, memory, network) and use Seek(), see examples below.
* Importing files to a salmon drive using a different device needs authorization only by an already authorized device for that specific drive. The device that created the drive is by definition authorized. The authorization mechanism protects against repeated access based attacks!
* Make sure that you never backup and restore the Nonce Sequencer files! They are located in the <%LOCALAPPDATA%\.salmon> directory for the User or the LocalSystem (if you use the Windows Service). Make sure you exclude them from backups and restores.
* If you decide to use the User Nonce Sequencer make sure the file is secure. If you are not sure then use the Windows Service Nonce Sequencer which will be protected under the LocalSystem folder.
* Maximum standalone file size: 2^128 bytes or limited by the backed resource (disk, memory, network).
* Maximum drive file size: 2^64 bytes
* Maximum number of drive files: 2^62 (64 bit nonces used for the filename and the file contents.

### License
Salmon is released under MIT Licence, see [LICENSE](https://github.com/mku11/Salmon/blob/main/LICENSE) file.
Make sure you read the LICENSE file and display proper attribution if you decide to use this software.
Dependency libraries from Github, Maven, and NuGet are covered by their own license.
  
  
### Contributions
Code contributions are not accepted though security audits and POCs are more than welcome!
  
  
# Examples

## Java
```
String text = "This is a plaintext that will be used for testing";
String testFile = "D:/tmp/file.txt";
IRealFile tFile = new JavaFile(testFile);

byte[] bytes = text.getBytes();
byte[] key = SalmonGenerator.getSecureRandomBytes(32); // 256 bit key
byte[] nonce = SalmonGenerator.getSecureRandomBytes(8); // 64 bit nonce
```
  
```
// Example 1: encrypt byte array using 4 threads
byte[] encBytes = SalmonEncryptor.encrypt(bytes, key, nonce, false, 4);

// decrypt byte array
byte[] decBytes = SalmonEncryptor.decrypt(encBytes, key, nonce, false, 4);
Assert.assertArrayEquals(bytes, decBytes);
```
  
```
// Example 2: encrypt string and save the nonce in the header
nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
String encText = SalmonTextEncryptor.encryptString(text, key, nonce, true);

// decrypt string
String decText = SalmonTextEncryptor.decryptString(encText, key, null, true);
Assert.assertEquals(text, decText);
```
  
```
// Example 3: encrypt data to an byte output stream
AbsStream encOutStream = new MemoryStream(); // or use your custom output stream by extending AbsStream
nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
// pass the output stream to the SalmonStream
SalmonStream encrypter = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, encOutStream,
null, false, null, null);
// encrypt and write with a single call, you can also Seek() and Write()
encrypter.write(bytes, 0, bytes.length);
// encrypted data are now written to the encOutStream.
encOutStream.position(0);
byte[] encData = ((MemoryStream) encOutStream).toArray();
encrypter.flush();
encrypter.close();
encOutStream.close();

//decrypt a stream with encoded data
AbsStream encInputStream = new MemoryStream(encData); // or use your custom input stream by extending AbsStream
SalmonStream decrypter = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt, encInputStream,
null, false, null, null);
byte[] decBuffer = new byte[1024];
// decrypt and read data with a single call, you can also Seek() before Read()
int bytesRead = decrypter.read(decBuffer, 0, decBuffer.length);
// encrypted data are now in the decBuffer
String decString = new String(decBuffer, 0, bytesRead);
System.out.println(decString);
decrypter.close();
encInputStream.close();
Assert.assertEquals(text, decString);
```
  
```
// Example 4: encrypt to a file, the SalmonFile has a virtual file system API
// with copy, move, rename, delete operations
SalmonFile encFile = new SalmonFile(new JavaFile(testFile), null);
nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
encFile.setEncryptionKey(key);
encFile.setRequestedNonce(nonce);
AbsStream stream = encFile.getOutputStream();
// encrypt data and write with a single call
stream.write(bytes, 0, bytes.length);
stream.flush();
stream.close();

// decrypt an encrypted file
SalmonFile encFile2 = new SalmonFile(new JavaFile(testFile), null);
encFile2.setEncryptionKey(key);
AbsStream stream2 = encFile2.getInputStream();
byte[] decBuff = new byte[1024];
// decrypt and read data with a single call, you can also Seek() to any position before Read()
int encBytesRead = stream2.read(decBuff, 0, decBuff.length);
String decString2 = new String(decBuff, 0, encBytesRead);
System.out.println(decString2);
stream2.close();
Assert.assertEquals(text, decString2);
```

## C#  
```
string text = "This is a plaintext that will be used for testing";
string testFile = "D://tmp/file.txt";
IRealFile tFile = new DotNetFile(testFile);

byte[] bytes = Encoding.UTF8.GetBytes(text);
byte[] key = SalmonGenerator.GetSecureRandomBytes(32); // 256 bit key
byte[] nonce = SalmonGenerator.GetSecureRandomBytes(8); // 64 bit nonce
```

```
// Example 1: encrypt byte array using 4 threads
byte[] encBytes = SalmonEncryptor.Encrypt(bytes, key, nonce, false, threads:4);

// decrypt byte array
byte[] decBytes = SalmonEncryptor.Decrypt(encBytes, key, nonce, false, threads:4);
CollectionAssert.AreEqual(bytes, decBytes);
```

```
// Example 2: encrypt string and save the nonce in the header
nonce = SalmonGenerator.GetSecureRandomBytes(8); // always get a fresh nonce!
string encText = SalmonTextEncryptor.EncryptString(text, key, nonce, true);

// decrypt string
string decText = SalmonTextEncryptor.DecryptString(encText, key, null, true);
Assert.AreEqual(text, decText);
```
  
```
// Example 3: encrypt data to an output stream
Stream encOutStream = new MemoryStream(); // or any other writeable Stream like to a file
nonce = SalmonGenerator.GetSecureRandomBytes(8); // always get a fresh nonce!
// pass the output stream to the SalmonStream
SalmonStream encrypter = new SalmonStream(key, nonce, EncryptionMode.Encrypt, encOutStream);
// encrypt and write with a single call, you can also Seek() and Write()
encrypter.Write(bytes, 0, bytes.Length);
// encrypted data are now written to the encOutStream.
encOutStream.Position = 0;
byte[] encData = (encOutStream as MemoryStream).ToArray();
encrypter.Flush();
encrypter.Close();
encOutStream.Close();

//decrypt a stream with encoded data
Stream encInputStream = new MemoryStream(encData); // or any other readable Stream like from a file
SalmonStream decrypter = new SalmonStream(key, nonce, EncryptionMode.Decrypt, encInputStream);
byte[] decBuffer = new byte[1024];
// decrypt and read data with a single call, you can also Seek() before Read()
int bytesRead = decrypter.Read(decBuffer, 0, decBuffer.Length);
// encrypted data are now in the decBuffer
string decString = Encoding.UTF8.GetString(decBuffer, 0, bytesRead);
Console.WriteLine(decString);
decrypter.Close();
encInputStream.Close();
Assert.AreEqual(text, decString);
```
  
```
// Example 4: encrypt to a file, the SalmonFile has a virtual file system API
// with copy, move, rename, delete operations
SalmonFile encFile = new SalmonFile(new DotNetFile(testFile));
nonce = SalmonGenerator.GetSecureRandomBytes(8); // always get a fresh nonce!
encFile.SetEncryptionKey(key);
encFile.SetRequestedNonce(nonce);
Stream stream = encFile.GetOutputStream();
// encrypt data and write with a single call
stream.Write(bytes, 0, bytes.Length);
stream.Flush();
stream.Close();

// decrypt an encrypted file
SalmonFile encFile2 = new SalmonFile(new DotNetFile(testFile));
encFile2.SetEncryptionKey(key);
Stream stream2 = encFile2.GetInputStream();
byte[] decBuff = new byte[1024];
// decrypt and read data with a single call, you can also Seek() to any position before Read()
int encBytesRead = stream2.Read(decBuff, 0, decBuff.Length);
string decString2 = Encoding.UTF8.GetString(decBuff, 0, encBytesRead);
Console.WriteLine(decString2);
stream2.Close();
Assert.AreEqual(text, decString2);	
```
  
## C/C++  

```
// Example: encrypt/decrypt byte array

HCRYPTPROV hCryptProv;
BYTE key[32];
BYTE nonce[8];
CryptAcquireContextW(&hCryptProv, 0, 0, PROV_RSA_FULL, CRYPT_VERIFYCONTEXT);
CryptGenRandom(hCryptProv, 32, key); // get a random key
CryptGenRandom(hCryptProv, 8, nonce); // 8 bytes for the random nonce
init(false, 32);

string text = "This is a plaintext that will be used for testing";
char const* bytes = text.c_str();
int length = strlen(bytes);
Logger::WriteMessage(bytes);
Logger::WriteMessage("\n");

BYTE counter[16];
memset(counter, 0, 16);
memcpy(counter, nonce, 8); // set the nonce
uint8_t outBuff[1024];

// encrypt the byte array
int bytesEncrypted = encrypt(key, counter, 0,
	(uint8_t*) bytes, length, 0, length,
	outBuff, 0);
Assert::AreEqual(length, bytesEncrypted);

// reset the counter
memset(counter, 0, 16);
memcpy(counter, nonce, 8); // set the nonce
uint8_t outBuff2[1024];

// decrypt the byte array
int bytesDecrypted = decrypt(key, counter, 0,
	outBuff, bytesEncrypted, bytesEncrypted,
	outBuff2, 0, bytesEncrypted, 
	0, 0);
Assert::AreEqual(length, bytesDecrypted);

// this is the decrypted string
string decText = string((char*)outBuff2, bytesDecrypted);
Logger::WriteMessage(decText.c_str());
Logger::WriteMessage("\n");
Assert::AreEqual(text, decText);

```

## Parallelism
For parallel read/write processing see:  
[Salmon Encryptor Java](https://github.com/mku11/Salmon/tree/main/java/src/salmon/com/mku11/salmon/SalmonEncryptor.java)  
[Salmon Importer Java](https://github.com/mku11/Salmon/tree/main/java/src/salmonfs/com/mku11/salmonfs/SalmonFileImporter.java)  
[Salmon Exporter Java](https://github.com/mku11/Salmon/tree/main/java/src/salmonfs/com/mku11/salmonfs/SalmonFileExporter.java)  

[Salmon Encryptor C#](https://github.com/mku11/Salmon/tree/main/csharp/src/salmon/Salmon/SalmonEncryptor.cs)  
[Salmon Importer C#](https://github.com/mku11/Salmon/tree/main/csharp/src/salmonfs/SalmonFS/SalmonFileImporter.cs)  
[Salmon Exporter C#](https://github.com/mku11/Salmon/tree/main/csharp/src/salmonfs/SalmonFS/SalmonFileExporter.cs)  
  
  
## Encrypted Media Playback
For in-memory media playback for encrypted files (with parallel read buffers):  
[Salmon Media Player JavaFX](https://github.com/mku11/Salmon/tree/main/javafx/src/com/mku11/salmon/vault/controller/MediaPlayerController.java)  
[Salmon Media Player WPF](https://github.com/mku11/Salmon/tree/main/wpf/src/salmon-wpf/ViewModel/MediaPlayerViewModel.cs)  
[Salmon Media Player Android](https://github.com/mku11/Salmon/tree/main/android/src/com/mku11/salmon/main/MediaPlayerActivity.java)  
[Salmon Media Player Xamarin Android](https://github.com/mku11/Salmon/tree/main/xamarin-android/src/Main/MediaPlayerActivity.cs)  
  
  
## More Examples
For more examples see the unit test cases:  
[Java Test](https://github.com/mku11/Salmon/tree/main/java/test)  
[C# Test](https://github.com/mku11/Salmon/tree/main/csharp/test)  
[C/C++ Test](https://github.com/mku11/Salmon/tree/main/c/test)  
  
  
## Applications
For a complete showcase of the Salmon virtual drive API see:  
[Salmon Vault JavaFX](https://github.com/mku11/Salmon/tree/main/javafx)  
[Salmon Vault WPF](https://github.com/mku11/Salmon/tree/main/wpf)  
[Salmon Vault Android](https://github.com/mku11/Salmon/tree/main/android)  
[Salmon Vault Xamarin Android](https://github.com/mku11/Salmon/tree/main/xamarin-android)  
  
  
# Subprojects   
* Salmon  
Library provides AES256 encryption for bytearray, text, and seekable stream support for Java, C#, Android, and Xamarin Android.  
  
  
* SalmonFS  
Library provides a virtual filesystem API with fast parallel read/write operations on encrypted files for Java, C#, Android, and Xamarin Android.   
  
  
* SalmonNative  
Library provides a fast native C library with AES intrinsics for Intel x86 32/64bit, and ARM 64bit for C/C++.  
  
  
* SalmonService  
A secure Sequential Nonce Generator implemented as a Windows Service based on device authorization.
  
  
* SalmonVault  
A file vault app using Salmon. Currently there are versions in JavaFX, C# WPF, and Android, and Xamarin Android.  
It features an explorer-like interface to browse and view, copy, move, delete, rename, import, and export encrypted files.  
There are builtin in-memory apps for viewing files with Image, Video, and Audio content.  
The android versions feature sharing files with external apps for editing and automatically re-import into the vault.  
  
  
![alt text](https://github.com/mku11/Salmon/blob/main/screenshots/Salmon%20Vault%20WPF.png)   


# Specifications

## SalmonAES

```
     UNENCRYPTED_DATA                                      ENCRYPTED_DATA
			   
     <-------------- ((AES256(CTR_X)) XOR (BLOCK_X))------------->
     |                                                           |
	   |                                                           |
|   CTR_1  |   CTR_2  |...|         |...        |   HMAC_1  |  CTR_1   |  CTR_2   |...|  HMAC_2   |         |...
|____|_____|__________|...|         |...        |___________|____|_____|__________|...|___________|         |...
|  BLOCK_1 |  BLOCK_2 |...|         |...  <-->  |           |  BLOCK_1 |  BLOCK_2 |...|           |         |...
| 16 bytes | 16 bytes |...|         |...        | Optional  | 16 bytes | 16 bytes |...| Optional  |         |...
|__________|__________|...|_________|...        |___________|__________|__________|...|___________|_________|...
|       CHUNK_1           | CHUNK_2 |...        | HMAC_1    |       CHUNK_1           |  HMAC_2   | CHUNK_2 |...
|        256 kB           |  256 kB |...        | 32 Bytes <-        256 kB            | 32 Bytes <- 256 kB |...
|_________________________|_________|...        |___________|_________________________|___________|_________|...

```
* In CTR mode each CTR is transformed via AES256 and then XORed with its correspoding 16 byte block to produce a 16 byte block output.
* CTR is an increasing 16byte unsigned integral: CTR_N2 = CTR_N1 + 1
* The CTR mode operation is symmetrical so you can use the same operation to decrypt the encrypted data.
* After encryption an integrity HMAC hash (32 bytes) is calculated and written for every CHUNK (default is 256 kB)
* Before decryption the HMAC hash compared against every correspoding DATA_CHUNK to verify its integrity.
* CTR mode allows for random access at the BLOCK level if integrity is disabled.
* When HMAC integrity is enabled each CHUNK is independent of others thus allowing random access (seek) and parallelism at the CHUNK level.  
   
   
## SalmonStream
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
  
  
  
## SalmonFile

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
  
  
  
## SalmonDrive

SalmonDrive is a virtual drive that hosts encrypted files.
More specifically:
1) Takes advantage of the SalmonFile API.
2) Uses parallel processing to import and export files to the drive.
3) Handles configuration for the encryption keys and nonce generation.
4) Is portable so any device with Salmon can read/decrypt files. 

## SalmonDriveConfig

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
  
  
  
## SalmonAuthConfig

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
  
  
  
## SalmonSequenceConfig

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
  
  
  
## SalmonService
The Salmon Service is implemented in Windows as a LocalSystem service so the nonce sequences are protected from local users.  
For Windows the sequence file is stored in <%LOCALAPPDATA%\.salmon> directory for the LocalSystem account.  
This is usually under: C:\Windows\SysWOW64\config\systemprofile\AppData\Local\\.salmon  
You still need to make sure that the file is not eligible for backup and restore, the same security concerns apply here!  
  