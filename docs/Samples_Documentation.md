## Salmon API Samples
You can find complete and working sample code for all the languages and platforms supported. To compile, build, and run the samples you need to download the salmon libraries from github releases or run the scripts under samples/ directory to download them for you.  
For windows:  
```
get_salmon_libs.bat
```
  
For Linux/MacOS:  
```
./get_salmon_libs.sh
```

API usage is consistent across languages with slight variations on naming conventions. Note that the javascript/typescript libraries are based on async IO so you will need to use 'await' most of the time.
  
For detailed samples see: [**Samples**](https://github.com/mku11/Salmon-AES-CTR/tree/main/samples)  
For an extensive use of the API see: [**Salmon Vault**](https://github.com/mku11/Salmon-Vault)  
Salmon API reference docs: [Salmon API Docs](https://mku11.github.io/Salmon-AES-CTR/docs/)
  
For short code samples see below.

### SalmonCore API: Byte Array and Text encryption/decryption
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

### SalmonFS API: file encryption via a virtual file system API
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
