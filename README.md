![alt text](https://github.com/mku11/Salmon/blob/main/common/common-res/icons/logo.png)

# Salmon
Salmon is an AES-256 CTR encryption library with HMAC SHA-256 integrity, parallel file operations (read/write), and seekable stream support. It provides a high level, low-ceremony, consistent, and transparent API for encrypted streams and files in Java and C#. Salmon is using low-level fast subroutines using Intel x86 and ARM64 intrinsics that you can alternatively include in your C/C++ projects.  

[![License: MIT](https://img.shields.io/github/license/mku11/salmon-AES-CTR)](https://github.com/mku11/salmon-AES-CTR/blob/main/LICENSE)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/mku11/salmon-AES-CTR)](https://github.com/mku11/salmon-AES-CTR/commits/master)
[![GitHub Releases](https://img.shields.io/github/downloads/mku11/salmon-AES-CTR/latest/total?logo=github)](https://github.com/mku11/salmon-AES-CTR/releases)
<!-- [![CodeFactor](https://www.codefactor.io/repository/github/mku11/salmon-AES-CTR/badge)](https://www.codefactor.io/repository/github/mku11/salmon-AES-CTR) -->
---

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

## Applications
For a complete showcase of the Salmon API check out the Salmon Vault app offered on several platforms:  
JavaFX, WPF, Android, .Net Android, MAUI.  

[**Download Salmon Vault**](https://github.com/mku11/Salmon-AES-CTR/releases)  
  ![alt text](https://github.com/mku11/Salmon/blob/main/screenshots/Salmon%20Vault%20WPF.png)  

---

### Gradle  
You can add the java libraries to your project using gradle:  
```
repositories {
    maven {
        allowInsecureProtocol true
        url 'http://localhost/repository/maven/releases'
    }
    ...
}
dependencies {
    implementation 'com.mku.salmon:salmon-core:1.0.5'
    implementation 'com.mku.salmon:salmon-fs:1.0.5'
	
    // for android
    implementation 'com.mku.salmon:salmon-fs-android:1.0.5'
    
    // uncomment to use optional fast AES intrinsics and Tiny AES
    // use SalmonStream.setProviderType() within your code
    // implementation 'com.mku.salmon:salmon-native-android:1.0.5'
}
```
### Gradle for windows native library:
To add the native library for windows add the gradle task below to gradle.build:
```
// unzipping the native library
configurations {
    nativeImplementation
}
task unzipNative(type: Sync) {
    from {
        configurations.nativeImplementation.collect { zipTree(it) }
    }
    into file(project.nativeLibsDir)
}
build.dependsOn unzipNative
dependencies {
    implementation 'com.mku.salmon:salmon-core:1.0.5'
    implementation 'com.mku.salmon:salmon-fs:1.0.5'
	
    // use SalmonStream.setProviderType() within your code
    // and make sure you add the dll libary to the java.library.path  
    unzipNative 'com.mku.salmon:salmon-native:1.0.5'
}
```

### Maven
```
<repositories>
    <repository>
        <id>mku-salmon-repo</id>
        <url>https://localhost/repository/maven/releases/</url>
    </repository>     
    ...
</repositories>
<dependencies>
    <dependency>
        <groupId>com.mku.salmon</groupId>
        <artifactId>salmon-core</artifactId>
        <version>1.0.5</version>
    </dependency>
	<dependency>
        <groupId>com.mku.salmon</groupId>
        <artifactId>salmon-fs</artifactId>
        <version>1.0.5</version>
    </dependency>
    ...
</dependencies>
```
### Nuget
Download the Nuget packages from [Here](https://github.com/mku11/Repo/nuget/releases)
```
Within Visual Studio go to Tools / Optons / Nuget Package Manager / Package Sources  
Create a new source with a name like "Salmon Repo" and add the local dir that has the downloaded packages.  
Then bring up the Nuget Package Manager and change the Package Source to "Salmon Repo".  
Install the salmon packages like you usually do.  
```
---

### Examples
  
#### Java / C#
The API is consistent across platforms with slight variations on naming conventions between Java and C#.  
  
For a sample using the SalmonDrive and the sequential nonce sequencer see: [Samples](https://github.com/mku11/Salmon/tree/main/samples)  
For a full fledge demo app see: [Salmon Vault](https://github.com/mku11/Salmon/tree/main/apps)  
For a simple usage sample see below.

```
// Get a fresh secure random key you need to keep this somewhere safe.
// For text passwords derived keys see the Samples folder.
byte[] key = SalmonGenerator.getSecureRandomBytes(32); // 256-bit key
// Get a fresh nonce, nonce is not a secret thought it must only be used once!
byte[] nonce = SalmonGenerator.getSecureRandomBytes(8); // 64-bit nonce

// Encrypt/decrypt a byte array using 3 parallel threads:
byte[] encBytes = new SalmonEncryptor(3).encrypt(bytes, key, nonce, false);
byte[] decBytes = new SalmonDecryptor(3).decrypt(encBytes, key, nonce, false);

// Or encrypt a string and save the nonce in the header
nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
String encText = SalmonTextEncryptor.encryptString(text, key, nonce, true);
// Now decrypt the encrypted string
String decText = SalmonTextEncryptor.decryptString(encText, key, null, true);

// Alternatively use a stream to encrypt a byte stream with write()
SalmonStream encStream = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, byteStream);
encStream.write(...);

// Or decrypt an existing encrypted byte stream with seek() and read()
SalmonStream decStream = new SalmonStream(key, nonce, EncryptionMode.Decrypt, byteStream);
encStream.read(...);

// SalmonStream is not a standard java Stream but so you have 
// to wrap it with SalmonInputStreamWrapper if you want to inject it to 3rd party code:
SalmonStream decStream = new SalmonStream(key, nonce, EncryptionMode.Decrypt,byteStream);
SalmonInputStreamWrapper stream = new SalmonInputStream(decStream);
stream.read(...);

// Or save directly to an encrypted file:
// Instantiate a new file with the path on disk:
JavaFile file = new JavaFile(filePath);
SalmonFile salmonFile = new SalmonFile(file);
nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
salmonFile.setEncryptionKey(key);
salmonFile.setRequestedNonce(nonce);
// get an output stream
SalmonStream outputStream = salmonFile.getOutputStream();
// writing to the stream will automatically encrypt the data.
outputStream.write(...);
// to decrypt get an output stream and read directly:
SalmonFile fileToDecrypt = new SalmonFile(file);
fileToDecrypt.setEncryptionKey(key);
SalmonStream inputStream = fileToDecrypt.getInputStream();
inputStream.read(...);
// for more detailed examples see the Samples folder.

```

#### C/C++  
There is no stream support for C/C++ but you can use the native AES-NI subroutines directly as in the example below.
For a full working C++ sample see: [Samples](https://github.com/mku11/Salmon/tree/main/samples)

```
// encrypt/decrypt byte array using low level subroutines
#include "salmon.h"
#include "wincrypt.h"

HCRYPTPROV   hCryptProv;
BYTE         key[32];
BYTE         nonce[8];
	
CryptAcquireContextW(&hCryptProv, 0, 0, PROV_RSA_FULL, CRYPT_VERIFYCONTEXT);
CryptGenRandom(hCryptProv, 32, key); // generate a random key
CryptGenRandom(hCryptProv, 8, nonce); // always use a fresh nonce

// init the Salmon AES-NI intrinsics
salmon_init(AES_IMPL_AES_INTR);

// expand your AES 256-bit key
uint8_t expandedKey[240];
salmon_expandKey(key, expandedKey);

// set the counter
BYTE	counter[16];
memset(counter, 0, 16);
memcpy(counter, nonce, 8);

// reset the counter
memset(counter, 0, 16);
memcpy(counter, nonce, 8);
	
// encrypt using your initial counter
uint8_t encText[1024];
int encBytes = salmon_transform(
	expandedKey, counter, AES_MODE_ENCRYPTION,
	plainText, 0,
	encText, 0, length);

// reset your counter and decrypt
int decBytes = salmon_transform(
	expandedKey, counter, AES_MODE_DECRYPTION,
	encText, 0,
	plainText, 0, length);	
```

---

### Specifications
Want to know more about Salmon specs and subprojects?  
Click on [Salmon specifications and formats](https://github.com/mku11/Salmon/tree/main/docs)   
For how to compile and build each subproject see README.md in its respective folder.

### Limitations
* SalmonStream is seekable only if the backed resource supports random access (disk, memory, network).
* SalmonStream is not Thread Safe! If you want to use parallel processing you need to use [SalmonEncryptor]/[SalmonDecryptor].
* SalmonFile is seekable only if the backed resource supports random access (disk, memory, network).
* SalmonFile is not Thread Safe! If you want to use parallel processing you need to use [SalmonFileImporter]/[SalmonFileExporter].
* Importing files to a salmon virtual drive using different devices requires authorization by an already authorized device for each  virtual drive. The device that created the drive is by default authorized. The authorization mechanism protects against repeated access based attacks!
* Make sure that you never backup and restore the Nonce Sequencer files in your Windows Device! They are located in each user %LOCALAPPDATA%\\.salmon directory (including the LocalSystem user if you use the Salmon Windows Service). So make sure you exclude them from backups and restores.
* The Windows user sequencer files are not secure from other apps! Also do not share your device account with other users! Salmon will attempt to notify you if it encounters tampering on the sequencer though for additional security you should use the Salmon Windows Service which protects the sequencer under the LocalSystem space.
* Integrity is not supported for filenames only for file contents.
* Maximum guaranteed file size: 2^64 bytes or limited by the backed resource (disk, memory, network).
* Maximum drive file size: 2^64 bytes
* Maximum number of drive files: 2^62 (64 bit nonces used for the filename and the file contents.

### Build Status
Salmon is currently in beta stage which means you should use your own discretion before using it in production systems.

### Contributions
Code contributions are not accepted.  
Bug reports and security POCs are more than welcome!  
  
### License
Salmon is released under MIT Licence, see [LICENSE](https://github.com/mku11/Salmon/blob/main/LICENSE) file.
Make sure you read the LICENSE file and display proper attribution if you decide to use this software.
Dependency libraries from Github, Maven, and NuGet are covered by their own license  
see [NOTICE](https://github.com/mku11/Salmon/blob/main/LICENSE)  
