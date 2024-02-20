![alt text](https://github.com/mku11/Salmon-AES-CTR/blob/wip/common/common-res/icons/logo.png)

# Salmon
Salmon is an AES-256 CTR encryption library with HMAC SHA-256 integrity, parallel file operations (read/write), and seekable stream support. It provides a high level, low-ceremony, consistent API for encrypting streams and files in Java and C#. Salmon is using a fast native library for Intel x86 and ARM64 that you can include in your C/C++ projects.
  

[![License: MIT](https://img.shields.io/github/license/mku11/Salmon-AES-CTR.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.5-blue)](https://github.com/mku11/Salmon-AES-CTR/releases)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/mku11/Salmon-AES-CTR)](https://github.com/mku11/Salmon-AES-CTR/commits/master)
[![CodeFactor](https://www.codefactor.io/repository/github/mku11/salmon-AES-CTR/badge)](https://www.codefactor.io/repository/github/mku11/salmon-AES-CTR)
<!-- [![GitHub Releases](https://img.shields.io/github/downloads/mku11/Salmon-AES-CTR/latest/total?logo=github)](https://github.com/mku11/Salmon-AES-CTR/releases) -->

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
For a complete showcase of the Salmon API check out the Salmon Vault app offered on several different platforms:  
[**Salmon Vault**](https://github.com/mku11/Salmon-Vault)
---

### Gradle  

To integrate the Salmon libraries to your gradle project add the salmon repository:  

```
repositories {
    maven {
        url 'https://github.com/mku11/Repo/raw/main/maven/releases'
    }
    ...
}
```

Add the java libraries to your project:
```
dependencies {
    implementation 'com.mku.salmon:salmon-core:1.0.5'
    implementation 'com.mku.salmon:salmon-fs:1.0.5'
	
    // for android
    implementation 'com.mku.salmon:salmon-fs-android:1.0.5'
    
    // optional fast AES intrinsics and Tiny AES
    // make sure you use SalmonStream.setProviderType() to set the provider in java
    implementation 'com.mku.salmon:salmon-native-android:1.0.5'
}
```

### Windows native library in Gradle
To add the native library for windows to your gradle project add the task below:

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

To integrate the Salmon libraries to your maven project add the salmon repository:
```
<repositories>
    <repository>
        <id>mku-salmon-repo</id>
        <url>https://github.com/mku11/Repo/raw/main/maven/releases</url>
    </repository>     
    ...
</repositories>
```

Now add the dependencies:
```
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

To integrate the Salmon libraries to your Visual Studio project:
Download the Nuget packages from [Salmon nuget repo](https://github.com/mku11/Repo/tree/main/nuget/releases)  
Within Visual Studio go to Tools / Optons / Nuget Package Manager / Package Sources  
Create a new source with a name like "Salmon Repo" and add the local dir that has the downloaded packages.  
Then bring up the Nuget Package Manager and change the Package Source to "Salmon Repo".  
Install the salmon packages like you usually do.  

---

### Examples
API usage is pretty much the same across Java/C#/Javascript/Python and various platforms with slight variations on naming conventions.  
Worth to note that the Typescript/Javascript library api is based on async IO so make sure you await.  
  
**Recommended**: Use the SalmonDrive and the virtual drive API to create a drive with a text password and then import your files. This will take care of all the details so you don't want have to worry about them. 
  
For samples using the SalmonDrive and the sequential nonce sequencer see: [Samples](https://github.com/mku11/Salmon-AES-CTR/tree/main/samples)  
For a full fledge demo app see: [Salmon Vault](https://github.com/mku11/Salmon-Vault)  
For a simple usage sample see below.


##### Recommended: Use SalmonDrive and the virtual file system API:
```
// If you don't care much about encryption and just want to encrypt data you can just use the SalmonDrive
// which will create a virtual drive and all the important details for you.
SalmonDrive drive = SalmonDriveManager.createDrive("c:\path\to\your\drive", password);

// setup our importer with 2 threads for parallel processing and import our files:
SalmonFileCommander commander = new SalmonFileCommander(SalmonDefaultOptions.getBufferSize(), SalmonDefaultOptions.getBufferSize(), 2);
JavaFile[] files = new JavaFile[]{new JavaFile("data/file.txt")};
JavaFile[] files = new JavaFile[]{new JavaFile("data/file.txt")};
commander.importFiles(files, drive.getVirtualRoot(), false, true);

// use the virtual filesystem API to list or directly get the file:
SalmonFile root = drive.getVirtualRoot();
SalmonFile[] files = root.listFiles();
SalmonFile file = root.getChild("file.txt");

// now read from the stream using parallel threads and caching:
SalmonFileInputStream inputStream = new SalmonFileInputStream(file, 2, 4 * 1024 * 1024, 2, 256 * 1024);
inputStream.read(...);
inputStream.seek(...);
```

##### Adhoc: data encryption/decryption
```
// To encrypt byte data or text without using a vault:
// Get a fresh secure random key and keep this somewhere safe.
// For text passwords see the Samples folder.
// For the more secure sequential nonces see the SalmonDrive sample.
byte[] key = SalmonGenerator.getSecureRandomBytes(32); // 256-bit key

// Also get a fresh nonce, nonce is not a secret thought it must only be used once per content!
byte[] nonce = SalmonGenerator.getSecureRandomBytes(8); // 64-bit nonce

// Now you encrypt/decrypt a byte array using 2 parallel threads:
byte[] encBytes = new SalmonEncryptor(2).encrypt(bytes, key, nonce, false);
byte[] decBytes = new SalmonDecryptor(2).decrypt(encBytes, key, nonce, false);

// Or encrypt a text string and save the nonce in the header:
nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
String encText = SalmonTextEncryptor.encryptString(text, key, nonce, true);
// Decrypt the encrypted string:
String decText = SalmonTextEncryptor.decryptString(encText, key, null, true);
```

##### Performance: Enable the AES-NI intrinsics
```
// To set the fast native AesIntrinsics:
SalmonStream.setAesProviderType(SalmonStream.ProviderType.AesIntrinsics);
```

##### Usability: Inject the SalmonStream into 3rd party code
```
// If you work with Java and want to inject a SalmonStream to 3rd party code to read the contents
// Wrap it with InputStreamWrapper which is a standard Java InputStream:
SalmonStream decStream = new SalmonStream(key, nonce, EncryptionMode.Decrypt, byteStream);
InputStreamWrapper stream = new InputStreamWrapper(decStream);
// somewhere inside the 3rd party code the data will be decrypted and read transparently
// stream.read(...); 
```


For more detailed examples see the Samples folder.

#### C/C++  
There is no stream support for C/C++ but you can use the Salmon native AES-NI subroutines directly as in the example below.
For a full working C++ sample see: [Samples](https://github.com/mku11/Salmon-AES-CTR/tree/main/samples)

### Specifications
Want to know more about Salmon specs and subprojects?  
Click on [Salmon specifications and formats](https://github.com/mku11/Salmon-AES-CTR/tree/main/docs)   
For how to compile and build each subproject see README.md in its respective folder.

### Limitations
* SalmonStream is seekable only if the backed resource supports random access (disk, memory, network).
* SalmonStream is not Thread Safe! If you want to use parallel processing you need to use SalmonEncryptor/SalmonDecryptor.
* SalmonFile is seekable only if the backed resource supports random access (disk, memory, network).
* SalmonFile is not Thread Safe! If you want to use parallel processing you need to use SalmonFileImporter/SalmonFileExporter.
* Importing files to a salmon virtual drive using different devices requires authorization by an already authorized device for each  virtual drive. The device that created the drive is by default authorized. The authorization mechanism protects against repeated access based attacks!
* Make sure that you never backup and restore the Nonce Sequencer files in your Windows Device! They are located in each user %LOCALAPPDATA%\\.salmon directory (including the LocalSystem user if you use the Salmon Windows Service). So make sure you exclude them from backups and restores.
* The Windows user sequencer files are not secure from other apps! Also do not share your device account with other users! Salmon will attempt to notify you if it encounters tampering on the sequencer though for additional security you should use the Salmon Windows Service which protects the sequencer under the LocalSystem space.
* Integrity is not supported for filenames only for file contents.
* Maximum guaranteed file size: 2^64 bytes or limited by the backed resource (disk, memory, network).
* Maximum drive file size: 2^64 bytes
* Maximum number of drive files: 2^62 (64 bit nonces used for the filename and the file contents.

### Contributions
Code contributions are not accepted.  
Bug reports and security POCs are more than welcome!  
  
### License
Salmon is released under MIT Licence, see [LICENSE](https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE) file.
Make sure you read the LICENSE file and display proper attribution if you decide to use this software.
Dependency libraries from Github, Maven, and NuGet are covered by their own license  
see [NOTICE](https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE)  
