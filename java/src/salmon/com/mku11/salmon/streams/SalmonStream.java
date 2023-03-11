package com.mku11.salmon.streams;
/*
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/


import com.mku11.salmon.*;
import com.mku11.salmon.transformers.SalmonAES;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static javax.crypto.Cipher.ENCRYPT_MODE;

/**
 * Stream wrapper provides encryption and decryption
 * Block data integrity is also supported.
 */
public class SalmonStream extends AbsStream {
    public static final int BLOCK_SIZE = 16;
    public static final int MAX_CHUNK_SIZE = 1024 * 1024;
    public static final int DEFAULT_CHUNK_SIZE = 256 * 1024;

    private static boolean enableLogDetails = false;
    private final byte[] headerData;

    private final EncryptionMode encryptionMode;

    private boolean failSilently;
    private boolean allowRangeWrite;
    private Cipher aesTransformer;
    private final AbsStream baseStream;
    private final byte[] key;
    private final byte[] nonce;
    private final byte[] hmacKey;
    private long block = 0;
    private byte[] counter;
    private final boolean integrity;
    private int chunkSize = -1;
    private static ProviderType providerType = ProviderType.Default;

    private int hmacHashLength;

    public enum ProviderType {
        Default, AesIntrinsics
    }

    public SalmonStream(byte[] key, byte[] nonce, EncryptionMode encryptionMode,
                        AbsStream baseStream) throws Exception {
        this(key, nonce, encryptionMode, baseStream, null, false, null, null);
    }

    public SalmonStream(byte[] key, byte[] nonce, EncryptionMode encryptionMode,
                        AbsStream baseStream, byte[] headerData) throws Exception {
        this(key, nonce, encryptionMode, baseStream, headerData, false, null, null);
    }
    /**
     * AES Counter mode Stream
     * <p>
     * If you read from the stream it will decrypt the data from the baseStream.
     * If you write to the stream it will encrypt the data from the baseStream.
     * The transformation is based on AES CTR Mode:
     * https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)
     * Notes:
     * The initial value of the counter is a result of the concatenation of an 12 byte nonce and an additional 4 bytes counter.
     * The counter is then: incremented every block, encrypted by the key, and xored with the plain text.
     *
     * @param key            The AES key that is used to encrypt decrypt
     * @param nonce          The nonce used for the initial counter
     * @param encryptionMode Encryption mode Encrypt or Decrypt this cannot change later
     * @param baseStream     The base Stream that will be used to read the data
     * @param headerData     Header data (unencrypted)
     * @param integrity      enable HMAC integrity
     * @param chunkSize      the chunk size to be used with integrity
     * @param hmacKey        HMAC key to be used with integrity
     */
    public SalmonStream(byte[] key, byte[] nonce, EncryptionMode encryptionMode,
                        AbsStream baseStream, byte[] headerData,
                        boolean integrity, Integer chunkSize, byte[] hmacKey) throws Exception {
        this.key = key;
        this.nonce = nonce;
        this.encryptionMode = encryptionMode;
        this.baseStream = baseStream;

        this.headerData = headerData;
        this.integrity = integrity;
        this.hmacKey = hmacKey;

        if (this.integrity && chunkSize != null && chunkSize < 0)
            throw new Exception("Chunk size should be zero for the default or a positive number");
        if (this.integrity && hmacKey == null)
            throw new Exception("You need an HMAC key to use with integrity");

        if (integrity && (chunkSize == null || chunkSize == 0))
            this.chunkSize = DEFAULT_CHUNK_SIZE;
        else if (chunkSize != null)
            this.chunkSize = chunkSize;
        init();
    }

    public static void setEnableLogDetails(boolean value) {
        enableLogDetails = value;
    }

    public static void setProviderType(ProviderType providerType) {
        SalmonStream.providerType = providerType;
    }

    public static ProviderType getProviderType() {
        return SalmonStream.providerType;
    }

    public long length() {
        int hmacOffset = chunkSize > 0 ? SalmonGenerator.HMAC_RESULT_LENGTH : 0;
        long totalHMACBytes = getTotalHMACBytesFrom(baseStream.length() - 1, hmacOffset);
        return baseStream.length() - getHeaderLength() - totalHMACBytes;
    }

    public long actualLength() {
        long totalHMACBytes = getTotalHMACBytesFrom(baseStream.length() - 1, 0);
        if (canRead())
            return length();
        else if (canWrite())
            return baseStream.length() + getHeaderLength() + totalHMACBytes;
        return 0;
    }

    public long position() throws IOException {
        return getVirtualPosition();
    }

    public void position(long value) throws Exception {
        if (canWrite() && !allowRangeWrite && value != 0)
            throw new SalmonSecurityException("Range Write is not allowed for security (non-reusable IVs), if you still want to take the risk you can   it by setting SetAllowRangeWrite(true)");
        setVirtualPosition(value);
    }

    public boolean canRead() {
        return baseStream.canRead() && encryptionMode == EncryptionMode.Decrypt;
    }

    public boolean canSeek() {
        return baseStream.canSeek();
    }

    public boolean canWrite() {
        return baseStream.canWrite() && encryptionMode == EncryptionMode.Encrypt;
    }

    /**
     * If the stream has integrity enabled
     */
    public boolean hasIntegrity() {
        return getChunkSize() > 0;
    }

    /**
     * Initialize the Transformer and the base stream
     */
    private void init() throws Exception {
        initTransformer();
        initStream();
    }

    private void initStream() throws Exception {
        baseStream.position(getHeaderLength());
    }

    private long getHeaderLength() {
        if (headerData == null)
            return 0;
        else
            return headerData.length;
    }

    /**
     * To create the AES CTR mode we use ECB for AES with No Padding.
     * Initailize the Counter to the initial vector provided.
     * For each data block we increase the Counter and apply the EAS encryption on the Counter.
     * The encrypted Counter then will be xor-ed with the actual data block.
     */
    private void initTransformer() throws SalmonIntegrity.SalmonIntegrityException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        if (providerType == ProviderType.Default) {
            SecretKey encSecretKey = new SecretKeySpec(key, "AES");
            aesTransformer = Cipher.getInstance("AES/ECB/NoPadding");
            aesTransformer.init(ENCRYPT_MODE, encSecretKey);
        }
        hmacHashLength = SalmonGenerator.HMAC_RESULT_LENGTH;
        if (integrity) {
            if (chunkSize < 0 || chunkSize < BLOCK_SIZE || chunkSize % BLOCK_SIZE != 0 || chunkSize > MAX_CHUNK_SIZE)
                throw new SalmonIntegrity.SalmonIntegrityException("Invalid chunk size, specify zero for default value or a positive number multiple of: "
                        + BLOCK_SIZE + " and less than: " + MAX_CHUNK_SIZE + " bytes");
        }
        if (providerType == ProviderType.AesIntrinsics) {
            SalmonAES.init(enableLogDetails, hmacHashLength);
        }
        resetCounter();
    }


    /**
     * Seek to a specific position on the stream. This does not include the header and any HMAC Signatures.
     *
     * @param offset The offset that seek will use
     * @param origin If it is Begin the offset will be the absolute postion from the start of the stream
     *               If it is Current the offset will be added to the current position of the stream
     *               If it is End the offset will be the absolute position starting from the end of the stream.
     */
    @Override
    public long seek(long offset, SeekOrigin origin) throws Exception {
        if (origin == SeekOrigin.Begin)
            position(offset);
        else if (origin == SeekOrigin.Current)
            position(position() + offset);
        else if (origin == SeekOrigin.End)
            position(length() - offset);
        return position();
    }

    @Override
    public void setLength(long value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void flush() {
        if (this.baseStream != null) {
            baseStream.flush();
        }
    }

    @Override
    public void close() throws IOException {
        closeStreams();
    }

    /**
     * Returns the current Counter value
     */
    public byte[] getCounter() {
        return counter.clone();
    }

    /* 
     Sets the Virtual Position of the stream excluding the header and HMAC signatures.
      
     @param value 
     */

    /**
     * Returns the current Block value
     */
    public long getBlock() {
        return block;
    }

    /**
     * Returns the encryption key
     */
    public byte[] getKey() {
        return key;
    }


    /**
     * Returns the HMAC key
     */
    public byte[] getHmacKey() {
        return hmacKey;
    }

    /**
     * Returns the initial vector
     */
    public byte[] getNonce() {
        return nonce;
    }

    /**
     * Returns the Chunk size used to apply HMAC signature
     */
    public int getChunkSize() {
        return chunkSize;
    }


    boolean getIntegrity() {
        return integrity;
    }

    /**
     * Warning! Allow byte range encryption writes on a current stream. Overwriting is not a good idea because it will re-use the same IV.
     * This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
     * You should only use this setting for initial encryption with parallel streams and not for overwriting!
     *
     * @param value True to allow byte range encryption write operations
     */
    public void setAllowRangeWrite(boolean value) {
        this.allowRangeWrite = value;
    }

    /**
     * Set to true if don't want to throw Exceptions if you are using integrity and the are mismatches during read operations.
     * The stream will anyway return zero for the read.
     *
     * @param value
     */
    public void setFailSilently(boolean value) {
        this.failSilently = value;
    }

    private void setVirtualPosition(long value) throws Exception {
        // we skip the header bytes and any hmac values we have if the file has integrity set
        baseStream.position(value);
        baseStream.position(baseStream.position() + getTotalHMACBytesFrom(baseStream.position(), 0));
        baseStream.position(baseStream.position() + getHeaderLength());
        resetCounter();
        syncCounter();
    }

    /**
     * Returns the Virtual Position of the stream excluding the header and HMAC signatures
     */
    private long getVirtualPosition() throws IOException {
        int hmacOffset = chunkSize > 0 ? SalmonGenerator.HMAC_RESULT_LENGTH : 0;
        long totalHMACBytes = getTotalHMACBytesFrom(baseStream.position(), hmacOffset);
        return baseStream.position() - getHeaderLength() - totalHMACBytes;
    }

    /**
     * Close base stream
     */
    private void closeStreams() throws IOException {
        if (baseStream != null) {
            if (canWrite())
                baseStream.flush();
            baseStream.close();
        }
    }

    /**
     * Resets the Counter and the block count.
     *
     * @return True if the stream has integrity enabled
     */
    private void resetCounter() {
        counter = new byte[BLOCK_SIZE];
        System.arraycopy(nonce, 0, counter, 0, nonce.length);
        block = 0;
    }

    /**
     * Syncs the Counter based on what AES block position the stream is at.
     * The block count is alredy excluding the header and the HMAC signatures.
     */
    private void syncCounter() throws Exception {
        long currBlock = position() / BLOCK_SIZE;
        increaseCounter(currBlock - block);
        block = currBlock;
    }

    /**
     * Increase the Counter
     * We use only big endianness for AES regardless of the machine architecture
     *
     * @param value value to increase counter by
     */
    // TODO: throw Exception when 8 lower bytes overflow
    private void increaseCounter(long value) throws Exception {
        if (value < 0)
            throw new IllegalArgumentException("Value should be positive");
        int index = BLOCK_SIZE - 1;
        int carriage = 0;
        while (index >= 0 && value + carriage > 0) {
            if (index <= BLOCK_SIZE - SalmonGenerator.NONCE_LENGTH)
                throw new MaxFileSizeExceededException("Current CTR max blocks exceeded");
            long val = (value + carriage) % 256;
            carriage = (int) (((counter[index] & 0xFF) + val) / 256);
            counter[index--] += (byte) val;
            value /= 256;
        }
    }

    /**
     * Return the number of bytes that all HMAC signatures occupy for each chunk size
     *
     * @param count      Actual length of the real data int the base stream including heder and HMAC signatures.
     * @param hmacOffset The HMAC key length
     *                   <returns>The number of bytes all HMAC signatures occupy</returns>
     */
    private long getTotalHMACBytesFrom(long count, int hmacOffset) {
        if (chunkSize <= 0)
            return 0;
        return SalmonIntegrity.getTotalHMACBytesFrom(count, chunkSize, hmacOffset);
    }

    protected final long getAlignedVirtualPosition() throws IOException {
        // if we want to apply or verify integrity with HMAC then we align our start position
        if (chunkSize > 0)
            return position() / chunkSize * chunkSize;
        // if we don't use integrity we can align to the AES block size
        return position() / BLOCK_SIZE * BLOCK_SIZE;
    }

    /**
     * Decrypts the data from the baseStream and stores them in the buffer provided.
     *
     * @param buffer The buffer that the data will be stored after decryption
     * @param offset The start position on the buffer that data will be stored
     * @param count  The requested count of the data bytes that should be decrypted
     *               <returns>The number of data bytes that were decrypted</returns>
     */
    @Override
    public int read(byte[] buffer, int offset, int count) throws Exception {
        long start = 0;
        long totalVerifyTime = 0;

        if (enableLogDetails) {
            start = SalmonTime.currentTimeMillis();
            // you can always run a profiler to get realistic results
            System.out.println("SalmonStream Read() byte range from: " + position() + ", length: " + count);
        }

        //we return -1 for java
        if (baseStream.position() >= baseStream.length())
            return -1;
        count = Math.min(buffer.length, count);

        long dataStart = position();
        int blockOffset = (int) (position() % BLOCK_SIZE);

        position(getAlignedVirtualPosition());

        // even though we can use BufferedStream for the baseStream for performance
        // we bring the whole data set to memory by setting up a temp buffer
        // including all the integrity chunks involved in the byte range
        int[] bytesAvail = new int[]{0};
        byte[] cacheReadBuffer = getCacheReadBuffer(dataStart, count, bytesAvail);
        int chunkToBlockOffset = (int) (dataStart - position()) - blockOffset;
        // check integrity and get the actual available bytes ignoring the hmac signatures
        if (integrity) {
            long startVerify = 0;
            if (enableLogDetails) {
                startVerify = SalmonTime.currentTimeMillis();
            }
            bytesAvail[0] = SalmonIntegrity.verifyHMAC(cacheReadBuffer, bytesAvail[0], hmacKey, chunkSize,
                    getVirtualPosition() == 0 ? headerData : null);
            if (enableLogDetails) {
                totalVerifyTime += (SalmonTime.currentTimeMillis() - startVerify);
            }
            if (bytesAvail[0] < 0) {
                if (failSilently)
                    return -1;
                throw new SalmonIntegrity.SalmonIntegrityException("Data corrupt or tampered!");
            }
        }

        position(dataStart);

        // to get the bytes that are the actual data we remove the offsets
        bytesAvail[0] -= blockOffset;
        bytesAvail[0] -= chunkToBlockOffset;

        int totalBytesRead;
        if (providerType == ProviderType.AesIntrinsics) {
            totalBytesRead = SalmonAES.decrypt(key, counter, chunkSize,
                    cacheReadBuffer, cacheReadBuffer.length, bytesAvail[0],
                    buffer, offset, count,
                    chunkToBlockOffset, blockOffset);
            if (totalBytesRead < 0)
                throw new Exception("Error during DecryptNative(), see previous messages");
        } else {
            totalBytesRead = decrypt(cacheReadBuffer, bytesAvail[0],
                    buffer, offset, count,
                    chunkToBlockOffset, blockOffset);
        }

        setVirtualPosition(dataStart + totalBytesRead);

        if (enableLogDetails) {
            System.out.println("SalmonStream Read HMAC Verify: " + totalBytesRead + " bytes in: " + totalVerifyTime + " ms");
            long total = SalmonTime.currentTimeMillis() - start;
            System.out.println("SalmonStream Read Total: " + totalBytesRead + " bytes in: " + total + " ms"
                    + ", avg speed: " + totalBytesRead / (float) total + " bytes/sec");
        }

        return totalBytesRead;
    }

    /**
     * Retrieves the data from base stream that will be used to decrypt.
     * If you enabled integrity this will also include the HMAC signatures for each chunk size.
     *
     * @param start      The virtual position on the stream excluding the length of the header and the hmac signatures
     * @param count      The length of the data that will be retrieved excluding the length of the header and the hmac signatures
     * @param bytesAvail The length of the data in the stream that are available excluding the length of the header and the hmac signatures
     */
    private byte[] getCacheReadBuffer(long start, int count, int[] bytesAvail) throws Exception {
        long startTime = 0;
        if (enableLogDetails) {
            startTime = SalmonTime.currentTimeMillis();
        }
        // by default our count should also include the start bytes of the block
        // if any
        long currPosition = position();
        int actualCount = (int) (start - position()) + count;
        if (chunkSize > 0) {
            //if (dataStart % chunkSize != 0)
            //    throw new SalmonIntegrityException("Partial reads with integrity should be aligned to the chunk size: " + chunkSize);

            //if there are any bytes in the tail we read up to the chunk that contain them
            // these extra bytes that are not requested will only be used to match the HMAC
            if (actualCount > chunkSize && actualCount % chunkSize != 0)
                actualCount = actualCount / chunkSize * chunkSize + chunkSize;
            if (actualCount < chunkSize && actualCount % chunkSize != 0)
                actualCount = chunkSize;

            // now we need to get all the hmac header.
            // to do this we calculate the total length of the hmac footers
            // and we add it to the virtual count
            int chunks = actualCount / chunkSize;
            if (actualCount != 0 && actualCount % chunkSize != 0)
                chunks++;
            actualCount += chunks * SalmonGenerator.HMAC_RESULT_LENGTH;
        }

        byte[] cacheBuffer = new byte[actualCount];
        bytesAvail[0] = baseStream.read(cacheBuffer, 0, actualCount);
        position(currPosition);
        if (enableLogDetails) {
            long total = SalmonTime.currentTimeMillis() - startTime;
            System.out.println("SalmonStream Read Cache: " + bytesAvail[0] + " bytes in: " + total + " ms");
        }
        return cacheBuffer;
    }


    /**
     * Encrypts/Decrypts the data from the buffer and writes the result to the baseStream.
     * If you are using integrity you will need to align all writes to the chunk size.
     *
     * @param buffer The buffer that contains the data that will be encrypted
     * @param offset The offset that encryption will start from
     * @param count  The length of the bytes that will be encrypted
     */
    @Override
    public void write(byte[] buffer, int offset, int count) throws Exception {
        long start = 0;
        long totalSignTime = 0;

        if (enableLogDetails) {
            start = SalmonTime.currentTimeMillis();
            // you can always run a profiler to get realistic results
            System.out.println("SalmonStream Write() byte range from: " + position() + ", length: " + count);
        }
        count = Math.min(buffer.length - offset, count);
        byte[] cacheWriteBuffer = getCacheWriteBuffer(count);

        int blockOffset = (int) (position() % BLOCK_SIZE);
        long currPosition = getVirtualPosition();

        // now align to the block for the encryption phase
        setVirtualPosition(getVirtualPosition() / BLOCK_SIZE * BLOCK_SIZE);

        int totalBytesWritten;
        if (providerType == ProviderType.AesIntrinsics) {
            totalBytesWritten = SalmonAES.encrypt(key, counter, chunkSize,
                    buffer, buffer.length, offset, count,
                    cacheWriteBuffer,
                    blockOffset);
            if (totalBytesWritten < 0)
                throw new Exception("Error during EncryptNative(), see previous messages");
        } else {
            totalBytesWritten = encrypt(buffer, offset, count,
                    cacheWriteBuffer,
                    blockOffset);
        }

        // reset the position before we write
        setVirtualPosition(currPosition);

        // write hmac signatures for all the chunk sizes
        if (chunkSize > 0) {
            long startSign = 0;
            if (enableLogDetails) {
                startSign = SalmonTime.currentTimeMillis();
            }
            SalmonIntegrity.applyHMAC(cacheWriteBuffer, cacheWriteBuffer.length, chunkSize, hmacKey,
                    getVirtualPosition() == 0 ? headerData : null);
            if (enableLogDetails) {
                totalSignTime += (SalmonTime.currentTimeMillis() - startSign);
            }
        }

        // finally write to output stream
        baseStream.write(cacheWriteBuffer, 0, cacheWriteBuffer.length);

        if (enableLogDetails) {
            System.out.println("SalmonStream HMAC Sign: " + totalBytesWritten + " bytes in: " + totalSignTime + " ms");
            long total = SalmonTime.currentTimeMillis() - start;
            System.out.println("SalmonStream Write Total: " + totalBytesWritten + " bytes in: " + total + " ms"
                    + ", avg speed: " + totalBytesWritten / (float) total + " bytes/sec");
        }
    }

    private int encrypt(byte[] buffer, int offset, int count,
                        byte[] cacheWriteBuffer,
                        int blockOffset) throws Exception {
        int totalBytesWritten = 0;
        long totalTransformTime = 0;
        int hmacSectionOffset = 0;
        int length;
        byte[] encCounter;

        for (int i = 0; i < count; i += length) {
            if (count - totalBytesWritten < BLOCK_SIZE - blockOffset)
                length = count - totalBytesWritten;
            else
                length = BLOCK_SIZE - blockOffset;
            long startTransform = 0;
            if (enableLogDetails) {
                startTransform = SalmonTime.currentTimeMillis();
            }
            encCounter = aesTransformer.doFinal(counter, 0, counter.length);
            if (enableLogDetails) {
                totalTransformTime += (SalmonTime.currentTimeMillis() - startTransform);
            }

            // adding a placeholder for hmac
            if (chunkSize > 0 && i % chunkSize == 0)
                hmacSectionOffset += hmacHashLength;

            // xor the plain text with the encrypted counter
            for (int k = 0; k < length; k++)
                cacheWriteBuffer[i + k + hmacSectionOffset] = (byte) (buffer[i + k + offset] ^ encCounter[k + blockOffset]);

            totalBytesWritten += length;

            // The counter is positioned automatically by the set Position property
            // but since we haven't written the data to the stream yet we have to
            // increment the counter manually
            if (length + blockOffset >= BLOCK_SIZE)
                increaseCounter(1);

            blockOffset = 0;
        }
        if (enableLogDetails) {
            System.out.println("SalmonStream AES-DEF Encrypt: " + totalBytesWritten + " bytes in: " + totalTransformTime + " ms");
        }
        return totalBytesWritten;
    }


    private int decrypt(byte[] cacheReadBuffer, int bytesAvail,
                        byte[] buffer, int offset, int count,
                        int chunkToBlockOffset, int blockOffset) throws Exception {
        int totalBytesRead = 0;
        int bytesRead;
        long totalTransformTime = 0;
        int length;
        byte[] blockData = new byte[BLOCK_SIZE];
        byte[] encCounter;
        int pos = 0;

        for (int i = 0; i < count && i < bytesAvail; i += bytesRead) {
            // if we have integrity enabled  we skip the hmac header
            // to arrive at the beginning of our chunk
            if (chunkSize > 0 && pos % (chunkSize + hmacHashLength) == 0) {
                pos += hmacHashLength;
            }
            // now we skip the data prior to our block within that chunk
            // this should happen only at the first time so we have to reset
            if (chunkSize > 0) {
                pos += chunkToBlockOffset;
                chunkToBlockOffset = 0;
            }
            // we also skip the data within the block so we are now at the beginning of the
            // data we want to read
            if (blockOffset > 0)
                pos += blockOffset;

            // we calculate the length of the data we need to read
            if (bytesAvail - totalBytesRead < BLOCK_SIZE - blockOffset)
                length = bytesAvail - totalBytesRead;
            else
                length = BLOCK_SIZE - blockOffset;

            if (length > count - totalBytesRead)
                length = count - totalBytesRead;

            bytesRead = Math.min(length, cacheReadBuffer.length - pos);
            System.arraycopy(cacheReadBuffer, pos, blockData, 0, length);
            pos += bytesRead;

            if (bytesRead == 0)
                break;
            long startTransform = 0;
            if (enableLogDetails) {
                startTransform = SalmonTime.currentTimeMillis();
            }
            encCounter = aesTransformer.doFinal(counter, 0, counter.length);
            if (enableLogDetails) {
                totalTransformTime += (SalmonTime.currentTimeMillis() - startTransform);
            }
            // xor the plain text with the encrypted counter
            for (int k = 0; k < length && k < bytesRead && i + k < bytesAvail; k++) {
                buffer[i + k + offset] = (byte) (blockData[k] ^ encCounter[k + blockOffset]);
                totalBytesRead++;
            }

            // XXX: since we have read all the data from the stream already
            // we have to increment the counter
            if (blockOffset + bytesRead >= BLOCK_SIZE)
                increaseCounter(1);

            // reset the blockOffset
            blockOffset = 0;
        }

        if (enableLogDetails) {
            System.out.println("SalmonStream AES-DEF Decrypt: " + totalBytesRead + " bytes in: " + totalTransformTime + " ms");
        }
        return totalBytesRead;
    }

    /**
     * Generates a byte array that will be used to store the encrypted data.
     * If you have enabled integrity it will also factor in all the HMAC signatures for each chunk.
     *
     * @param count length of byte array
     * @return byte array with HMAC placeholders
     */
    private byte[] getCacheWriteBuffer(int count) throws SalmonIntegrity.SalmonIntegrityException, IOException {
        long start = 0;
        if (enableLogDetails) {
            start = SalmonTime.currentTimeMillis();
        }
        // by default our count should also include the start bytes of the block
        // if any
        int actualCount = count;
        if (integrity) {
            if (position() % chunkSize != 0)
                throw new SalmonIntegrity.SalmonIntegrityException("Partial writes with integrity should use a buffer size multiple of the chunk size: " + chunkSize);

            // we need to get all the data plus the hmac header.
            // to do this we calculate the total length of the hmac headers
            // and we add it to the virtual count
            int chunks = count / chunkSize;
            if (count != 0 && count % chunkSize != 0)
                chunks++;
            actualCount = count + chunks * SalmonGenerator.HMAC_RESULT_LENGTH;
        }

        byte[] cacheBuffer = new byte[actualCount];
        if (enableLogDetails) {
            long total = SalmonTime.currentTimeMillis() - start;
            System.out.println("SalmonStream Write Cache: " + actualCount + " bytes in: " + total + " ms");
        }
        return cacheBuffer;
    }

    public enum EncryptionMode {
        Encrypt, Decrypt
    }

}

