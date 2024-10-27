## Salmon API Samples
API usage is consistent across languages with slight variations on naming conventions. Note that the javascript/typescript libraries are based on async IO so you will need to use 'await' most of the time.
  
For detailed samples see: [**Samples**](https://github.com/mku11/Salmon-AES-CTR/tree/main/samples)  
For an extensive use of the API see: [**Salmon Vault**](https://github.com/mku11/Salmon-Vault)  
  
For short code samples see below.

### SalmonFS API:
```
// Create a sequencer. Make sure this path is secure and excluded from your backups.
String sequencerPath = "c:\\users\\<username>\\AppData\\Local\\<somefolder>\\salmon_sequencer.xml";
SalmonFileSequencer sequencer = new SalmonFileSequencer(new JavaFile(sequencerPath), new SalmonSequenceSerializer());

// create() or open() a virtual drive provided with a location and a text password
// Supported drives: JavaDrive, DotNetDrive, PyDrive, JsDrive, JsHttpDrive (remote), JsNodeDrive (node.js)
SalmonDrive drive = JavaDrive.create(new JavaFile("c:\\path\\to\\your\\virtual\\drive"), password, sequencer);

// Create an importer with 2 threads for parallel processing
SalmonFileCommander commander = new SalmonFileCommander(256 * 1024, 256 * 1024, 2);

// you can create a batch of multiple files for encryption
JavaFile[] files = new JavaFile[]{
	new JavaFile("data/file1.txt"), 
	new JavaFile("data/file2.jpg"), 
	new JavaFile("data/file3.mp4")};

// now import the files under the root directory
commander.importFiles(files, drive.getRoot(), false, true, null, null, null);
commander.close();

// use the virtual drive API to list the files
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
// To encrypt byte data or text without using a drive you need to generate your own key and nonce.
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