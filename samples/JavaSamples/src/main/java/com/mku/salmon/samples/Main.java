package com.mku.salmon.samples;

import com.mku.convert.BitConverter;
import com.mku.file.IRealFile;
import com.mku.salmon.drive.JavaDrive;
import com.mku.file.JavaFile;
import com.mku.salmon.streams.EncryptionMode;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonFileInputStream;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;
import com.mku.salmon.*;
import com.mku.salmon.streams.SalmonStream;
import com.mku.salmon.password.SalmonPassword;
import com.mku.salmon.text.SalmonTextDecryptor;
import com.mku.salmon.text.SalmonTextEncryptor;
import com.mku.salmon.sequence.SalmonFileSequencer;
import com.mku.salmon.sequence.SalmonSequenceSerializer;
import com.mku.salmon.utils.SalmonFileCommander;

import java.io.IOException;
import java.util.Random;

public class Main {
    static String text = "This is plaintext that will be encrypted";
    static byte[] data = new byte[2 * 1024 * 1024];
    static String password = "MYS@LMONP@$$WORD";

    static {
        // some random data to encrypt
        Random r = new Random();
        r.nextBytes(data);
    }

    public static void main(String[] args) throws Exception {
        // uncomment to load the AES intrinsics for better performance
        // make sure you add option -Djava.library.path=C:\path\to\salmonlib\
        // SalmonStream.setAesProviderType(ProviderType.AesIntrinsics);

        // use the password to create a drive and import the file
        String vaultPath = "vault_" + BitConverter.toHex(SalmonGenerator.getSecureRandomBytes(6));
        JavaFile vaultDir = new JavaFile(vaultPath);
		vaultDir.mkdir();
        JavaFile[] filesToImport = new JavaFile[]{new JavaFile("data/file.txt")};
        createDriveAndImportFile(vaultDir, filesToImport);

        // or encrypt text into a standalone file without a drive:
        String filePath = "data_" + BitConverter.toHex(SalmonGenerator.getSecureRandomBytes(6));
        JavaFile file = new JavaFile(filePath);
        encryptAndDecryptTextToFile(file);

        // misc stream samples
        streamSamples();
    }

    private static byte[] getKeyFromPassword(String password) {
        // get a key from a text password:
        byte[] salt = SalmonGenerator.getSecureRandomBytes(24);
        // make sure the iterations are a large enough number
        byte[] key = SalmonPassword.getKeyFromPassword(password, salt, 60000, 32);
        return key;
    }

    public static void streamSamples() throws IOException {
        // get a fresh key
        byte[] key = SalmonGenerator.getSecureRandomBytes(32);

        // encrypt and decrypt a text string:
        encryptAndDecryptTextEmbeddingNonce(text, key);

        // encrypt and decrypt data to a byte array stream:
        encryptAndDecryptDataToByteArrayStream(text.getBytes(), key);

        // encrypt and decrypt byte array using multiple threads:
        encryptAndDecryptUsingMultipleThreads(data, key);
    }

    private static void encryptAndDecryptUsingMultipleThreads(byte[] bytes, byte[] key)
            throws IOException {
        System.out.println("Encrypting bytes using multiple threads: " + BitConverter.toHex(bytes).substring(0, 24) + "...");

        // Always request a new random secure nonce.
        byte[] nonce = SalmonGenerator.getSecureRandomBytes(8);

        // encrypt a byte array using 2 threads
        SalmonEncryptor encryptor = new SalmonEncryptor(2);
        byte[] encBytes = encryptor.encrypt(bytes, key, nonce, false);
        System.out.println("Encrypted bytes: " + BitConverter.toHex(encBytes).substring(0, 24) + "...");
        encryptor.close();

        // decrypt byte array using 2 threads
        SalmonDecryptor decryptor = new SalmonDecryptor(2);
        byte[] decBytes = decryptor.decrypt(encBytes, key, nonce, false);
        System.out.println("Decrypted bytes: " + BitConverter.toHex(decBytes).substring(0, 24) + "...");
        System.out.println();
        decryptor.close();
    }

    private static void encryptAndDecryptTextEmbeddingNonce(String text, byte[] key) throws IOException {
        System.out.println("Encrypting text with nonce embedded: " + text);

        // Always request a new random secure nonce.
        byte[] nonce = SalmonGenerator.getSecureRandomBytes(8);

        // encrypt string and save the nonce in the header
        String encText = SalmonTextEncryptor.encryptString(text, key, nonce, true);
        System.out.println("Encrypted text: " + encText);

        // decrypt string without the need to provide the nonce since it's stored in the header
        String decText = SalmonTextDecryptor.decryptString(encText, key, null, true);
        System.out.println("Decrypted text: " + decText);
        System.out.println();
    }

    private static void encryptAndDecryptDataToByteArrayStream(byte[] bytes, byte[] key) throws IOException {
        System.out.println("Encrypting data to byte array stream: " + BitConverter.toHex(bytes));

        // Always request a new random secure nonce!
        byte[] nonce = SalmonGenerator.getSecureRandomBytes(8); // 64 bit nonce

        // encrypt data to an byte output stream
        MemoryStream encOutStream = new MemoryStream(); // or use your custom output stream by extending RandomAccessStream

        // pass the output stream to the SalmonStream
        SalmonStream encStream = new SalmonStream(key, nonce, EncryptionMode.Encrypt,
                encOutStream, null,
                false, null, null);

        // encrypt/write data in a single call, you can also Seek() before Write()
        encStream.write(bytes, 0, bytes.length);

        // encrypted data are now written to the encOutStream.
        encOutStream.setPosition(0);
        byte[] encData = encOutStream.toArray();
        encStream.flush();
        encStream.close();
        encOutStream.close();

        //decrypt a stream with encoded data
        RandomAccessStream encInputStream = new MemoryStream(encData); // or use your custom input stream by extending AbsStream
        SalmonStream decStream = new SalmonStream(key, nonce, EncryptionMode.Decrypt,
                encInputStream, null,
                false, null, null);
        byte[] decBuffer = new byte[(int) decStream.length()];

        // seek to the beginning or any position in the stream
        decStream.seek(0, RandomAccessStream.SeekOrigin.Begin);

        // read/decrypt data in a single call, you can also Seek() before Read()
        int bytesRead = decStream.read(decBuffer, 0, decBuffer.length);
        decStream.close();
        encInputStream.close();

        System.out.println("Decrypted data: " + BitConverter.toHex(decBuffer));
        System.out.println();
    }

    private static void encryptAndDecryptTextToFile(IRealFile file) throws IOException {
        // encrypt to a file, the SalmonFile has a virtual file system API
        System.out.println("Encrypting text to File: " + text);
        byte[] bytes = text.getBytes();

        // derive the key from the password
        byte[] key = getKeyFromPassword(password);

        // Always request a new random secure nonce
        byte[] nonce = SalmonGenerator.getSecureRandomBytes(8); // 64 bit nonce

        SalmonFile encFile = new SalmonFile(file);
        encFile.setEncryptionKey(key);
        encFile.setRequestedNonce(nonce);
        RandomAccessStream stream = encFile.getOutputStream();

        // encrypt/write data with a single call
        stream.write(bytes, 0, bytes.length);
        stream.flush();
        stream.close();

        // Decrypt the file
        SalmonFile encFile2 = new SalmonFile(file);
        encFile2.setEncryptionKey(key);
        RandomAccessStream stream2 = encFile2.getInputStream();

        // read/decrypt data with a single call
        byte[] decBuff = new byte[1024];
        int encBytesRead = stream2.read(decBuff, 0, decBuff.length);
        String decString2 = new String(decBuff, 0, encBytesRead);
        System.out.println("Decrypted text: " + decString2);
        stream2.close();
        System.out.println();
    }

    private static void createDriveAndImportFile(IRealFile vaultDir, IRealFile[] filesToImport) throws Exception {
        // create a file sequencer:
        SalmonFileSequencer sequencer = createSequencer();

        // create a drive
        SalmonDrive drive = JavaDrive.create(vaultDir, password, sequencer);
        SalmonFileCommander commander = new SalmonFileCommander(256 * 1024, 256 * 1024, 2);

        // import multiple files
        SalmonFile[] filesImported = commander.importFiles(filesToImport, drive.getRoot(), false, true,
                (taskProgress) -> {
                    System.out.println("file importing: " + taskProgress.getFile().getBaseName() + ": "
                            + taskProgress.getProcessedBytes() + "/" + taskProgress.getTotalBytes() + " bytes");
                }, IRealFile.autoRename, (file, ex) -> {
                    // file failed to import
                });

        System.out.println("Files imported");

        // query for the file from the drive
        SalmonFile root = drive.getRoot();
        SalmonFile[] files = root.listFiles();

        // read from a native stream wrapper with parallel threads and caching
        // or use file.getInputStream() to get a low level RandomAccessStream
        SalmonFile file = files[0];
        SalmonFileInputStream inputStream = new SalmonFileInputStream(file,
                4, 4 * 1024 * 1024, 2, 256 * 1024);
        // inputStream.read(...);
        inputStream.close();

        // export the file
        IRealFile[] filesExported = commander.exportFiles(files, drive.getExportDir(), false, true,
                (taskProgress) -> {
                    try {
                        System.out.println("file exporting: " + taskProgress.getFile().getBaseName() + ": "
                                + taskProgress.getProcessedBytes() + "/" + taskProgress.getTotalBytes() + " bytes");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, IRealFile.autoRename, (sfile, ex) -> {
                    // file failed to import
                });

        System.out.println("Files exported");

        // close the file commander
        commander.close();

        // close the drive
        drive.close();
    }

    private static SalmonFileSequencer createSequencer() throws IOException {
        // create a file nonce sequencer and place it in a private space
        // make sure you never edit or back up this file.
        String seqFilename = "sequencer.xml";
		// if you use Linux/Macos use 'HOME'
        IRealFile privateDir = new JavaFile(System.getenv("LOCALAPPDATA"));
        IRealFile sequencerDir = privateDir.getChild("SalmonSequencer");
        if (!sequencerDir.exists())
            sequencerDir.mkdir();
        IRealFile sequenceFile = sequencerDir.getChild(seqFilename);
        SalmonFileSequencer fileSequencer = new SalmonFileSequencer(sequenceFile, new SalmonSequenceSerializer());
        return fileSequencer;
    }
}
