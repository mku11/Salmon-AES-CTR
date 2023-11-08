package com.mku.salmon.samples;

import com.mku.convert.BitConverter;
import com.mku.file.IRealFile;
import com.mku.file.JavaDrive;
import com.mku.file.JavaFile;
import com.mku.io.MemoryStream;
import com.mku.io.RandomAccessStream;
import com.mku.salmon.*;
import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.salmon.io.SalmonStream;
import com.mku.salmon.password.SalmonPassword;
import com.mku.salmon.text.SalmonTextDecryptor;
import com.mku.salmon.text.SalmonTextEncryptor;
import com.mku.salmonfs.*;
import com.mku.sequence.SalmonFileSequencer;
import com.mku.sequence.SalmonSequenceException;
import com.mku.sequence.SalmonSequenceSerializer;
import com.mku.utils.SalmonFileCommander;

import java.io.IOException;
import java.util.Random;

public class Main {
    static {
        // uncomment to load the AES intrinsics for better performance
        // SalmonStream.setAesProviderType(SalmonStream.ProviderType.AesIntrinsics);

        // If you're getting an java.lang.UnsatisfiedLinkError add java param:
        // -Djava.library.path=C:\path\to\salmonlib\
        // Make sure you build from gradle so the native lib can be unpacked under: build/libs/natives
        // > gradlew.bat build
    }

    public static void main(String[] args) throws Exception {
        String password = "MYS@LMONP@$$WORD";

        // some test to encrypt
        String text = "This is a plaintext that will be used for testing";
        byte[] bytes = text.getBytes();

        // some data to encrypt
        byte[] data = new byte[1 * 1024 * 1024];
        Random r = new Random();
        r.nextBytes(data);

        // you can create a key and reuse it:
        //byte[] key = SalmonGenerator.getSecureRandomBytes(32);
        // or get one derived from a text password:
        byte[] salt = SalmonGenerator.getSecureRandomBytes(24);

        // make sure the iterations are a large enough number
        byte[] key = SalmonPassword.getKeyFromPassword(password, salt, 60000, 32);

        // encrypt and decrypt byte array using multiple threads:
        encryptAndDecryptUsingMultipleThreads(data, key);

        // encrypt and decrypt a text string:
        encryptAndDecryptTextEmbeddingNonce(text, key);

        // encrypt and decrypt data to a byte array stream:
        encryptAndDecryptDataToByteArrayStream(bytes, key);

        // encrypt and decrypt text to a file:
        encryptAndDecryptTextToFile(text, key);

        // create a drive import, read the encrypted content, and export
        createDriveAndImportFile(password);
    }

    private static void encryptAndDecryptUsingMultipleThreads(byte[] bytes, byte[] key)
            throws SalmonSecurityException, SalmonIntegrityException, IOException {
        System.out.println("Encrypting bytes using multiple threads: " + BitConverter.toHex(bytes).substring(0, 24) + "...");

        // Always request a new random secure nonce.
        byte[] nonce = SalmonGenerator.getSecureRandomBytes(8);

        // encrypt a byte array using 2 threads
        byte[] encBytes = new SalmonEncryptor(2).encrypt(bytes, key, nonce, false);
        System.out.println("Encrypted bytes: " + BitConverter.toHex(encBytes).substring(0, 24) + "...");

        // decrypt byte array using 2 threads
        byte[] decBytes = new SalmonDecryptor(2).decrypt(encBytes, key, nonce, false);
        System.out.println("Decrypted bytes: " + BitConverter.toHex(decBytes).substring(0, 24) + "...");
        System.out.println();
    }

    private static void encryptAndDecryptTextEmbeddingNonce(String text, byte[] key)
            throws SalmonSecurityException, SalmonIntegrityException, IOException {
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

    private static void encryptAndDecryptDataToByteArrayStream(byte[] bytes, byte[] key)
            throws IOException, SalmonSecurityException, SalmonIntegrityException {
        System.out.println("Encrypting data to byte array stream: " + BitConverter.toHex(bytes));

        // Always request a new random secure nonce!
        byte[] nonce = SalmonGenerator.getSecureRandomBytes(8); // 64 bit nonce

        // encrypt data to an byte output stream
        MemoryStream encOutStream = new MemoryStream(); // or use your custom output stream by extending RandomAccessStream

        // pass the output stream to the SalmonStream
        SalmonStream encrypter = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt,
                encOutStream, null,
                false, null, null);

        // encrypt and write with a single call, you can also Seek() and Write()
        encrypter.write(bytes, 0, bytes.length);

        // encrypted data are now written to the encOutStream.
        encOutStream.position(0);
        byte[] encData = encOutStream.toArray();
        encrypter.flush();
        encrypter.close();
        encOutStream.close();

        //decrypt a stream with encoded data
        RandomAccessStream encInputStream = new MemoryStream(encData); // or use your custom input stream by extending AbsStream
        SalmonStream decrypter = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt,
                encInputStream, null,
                false, null, null);
        byte[] decBuffer = new byte[(int) decrypter.length()];

        // seek to the beginning or any position in the stream
        decrypter.seek(0, RandomAccessStream.SeekOrigin.Begin);

        // decrypt and read data with a single call, you can also Seek() before Read()
        int bytesRead = decrypter.read(decBuffer, 0, decBuffer.length);
        decrypter.close();
        encInputStream.close();

        System.out.println("Decrypted data: " + BitConverter.toHex(decBuffer));
        System.out.println();
    }

    private static void encryptAndDecryptTextToFile(String text, byte[] key)
            throws SalmonSecurityException, SalmonIntegrityException, SalmonSequenceException,
            IOException, SalmonRangeExceededException, SalmonAuthException {
        // encrypt to a file, the SalmonFile has a virtual file system API
        System.out.println("Encrypting text to File: " + text);
        String testFile = "D:/tmp/salmontestfile.txt";

        // the real file:
        IRealFile tFile = new JavaFile(testFile);
        if (tFile.exists())
            tFile.delete();

        byte[] bytes = text.getBytes();

        // Always request a new random secure nonce. Though if you will be re-using
        // the same key you should create a SalmonDrive to keep the nonces unique.
        byte[] nonce = SalmonGenerator.getSecureRandomBytes(8); // 64 bit nonce

        SalmonFile encFile = new SalmonFile(new JavaFile(testFile), null);
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        encFile.setEncryptionKey(key);
        encFile.setRequestedNonce(nonce);
        RandomAccessStream stream = encFile.getOutputStream();

        // encrypt data and write with a single call
        stream.write(bytes, 0, bytes.length);
        stream.flush();
        stream.close();

        // Decrypt the file
        SalmonFile encFile2 = new SalmonFile(new JavaFile(testFile), null);
        encFile2.setEncryptionKey(key);
        RandomAccessStream stream2 = encFile2.getInputStream();
        byte[] decBuff = new byte[1024];

        // read data with a single call
        int encBytesRead = stream2.read(decBuff, 0, decBuff.length);
        String decString2 = new String(decBuff, 0, encBytesRead);
        System.out.println("Decrypted text: " + decString2);
        stream2.close();
        System.out.println();
    }

    private static void createDriveAndImportFile(String password) throws Exception {
        // create a file nonce sequencer
        String seqFilename = "sequencer.xml";
        JavaFile dir = new JavaFile("output");
        if(!dir.exists())
            dir.mkdir();
        IRealFile sequenceFile = dir.getChild(seqFilename);
        SalmonFileSequencer fileSequencer = new SalmonFileSequencer(sequenceFile, new SalmonSequenceSerializer());
        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
        SalmonDriveManager.setSequencer(fileSequencer);

        // create a drive
        SalmonDrive drive = SalmonDriveManager.createDrive(dir.getPath() + "/vault" + new Random().nextInt(), password);
        SalmonFileCommander commander = new SalmonFileCommander(
                SalmonDefaultOptions.getBufferSize(), SalmonDefaultOptions.getBufferSize(), 2);
        JavaFile[] files = new JavaFile[]{new JavaFile("data/file.txt")};

        // import multiple files
        commander.importFiles(files, drive.getVirtualRoot(), false, true,
                (taskProgress) -> {
                    System.out.println("file importing: " + taskProgress.getFile().getBaseName() + ": " 
					+ taskProgress.getProcessedBytes() + "/" + taskProgress.getTotalBytes() + " bytes");
                }, IRealFile.autoRename, (file, ex) -> {
                    // file failed to import
                });

        // query for the file from the drive
        SalmonFile file = drive.getVirtualRoot().getChild("file.txt");

        // read from the stream with parallel threads and caching
        SalmonFileInputStream inputStream = new SalmonFileInputStream(file,
                4, 4 * 1024 * 1024, 2, 256 * 1024);
        // inputStream.read(...);
        inputStream.close();

        // export the file
        commander.exportFiles(new SalmonFile[]{file}, new JavaFile("output"), false, true,
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

        // close the file commander
        commander.close();

        // close the drive
        drive.close();
    }
}
