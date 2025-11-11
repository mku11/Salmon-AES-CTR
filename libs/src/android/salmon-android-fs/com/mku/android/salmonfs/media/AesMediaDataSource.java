package com.mku.android.salmonfs.media;
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

import android.media.MediaDataSource;
import android.util.Log;

import com.mku.func.Consumer;
import com.mku.android.salmonfs.drive.AndroidDrive;
import com.mku.salmon.integrity.IntegrityException;
import com.mku.salmon.streams.AesStream;
import com.mku.salmonfs.file.AesFile;
import com.mku.salmonfs.streams.AesFileInputStream;
import com.mku.streams.InputStreamWrapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * Seekable source for encrypted media content.
 */
public class AesMediaDataSource extends MediaDataSource {
    private static final String TAG = AesMediaDataSource.class.getName();

    private final InputStream stream;
    private AesFile aesFile;
	private Consumer<String> onError;
	
    private boolean integrityFailed;
    private long size;

    /**
     * Construct a seekable source for the android media player from an encrypted file.
     *
     * @param aesFile    AesFile that will be used as a source
     */
    public AesMediaDataSource(AesFile aesFile) throws Exception {
        this.aesFile = aesFile;
        this.size = aesFile.getLength();
        this.stream = new InputStreamWrapper(aesFile.getInputStream());
    }
	
	/**
     * Construct a seekable source for the android media player from an encrypted file using cached buffers and parallel processing.
     *
     * @param aesFile    AesFile that will be used as a source
     * @param buffers    The buffers
     * @param bufferSize Buffer size
     * @param threads    Threads for parallel processing
     * @param backOffset The backwards offset to use when reading buffers
     * @throws Exception Thrown if error occured
     */
    public AesMediaDataSource(AesFile aesFile,
                                 int buffers, int bufferSize, int threads, int backOffset) throws Exception {
        this.aesFile = aesFile;
        this.size = aesFile.getLength();
        this.stream = new AesFileInputStream(aesFile, buffers, bufferSize, threads, backOffset);
       
    }
	
	/**
     * Construct a seekable source for the android media player from an encrypted stream.
     *
     * @param aesStream    AesStream that will be used as a source
     * @throws Exception Thrown if error occured
     */
    public AesMediaDataSource(AesStream aesStream) throws Exception {
        this.size = aesStream.getLength();
        this.stream = new InputStreamWrapper(aesStream);
    }
	
	/**
     * Notify when error occurs
     *
     * @param onError The callback
	 */
	public void setOnError(Consumer<String> onError) {
		this.onError = onError;
	}
	
    /**
     * Decrypts and reads the contents of an encrypted file
     *
     * @param position The source file position the read will start from
     * @param buffer   The buffer that will store the decrypted contents
     * @param offset   The position on the buffer that the decrypted data will start
     * @param size     The length of the data requested
     */
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        stream.reset();
        stream.skip(position);
        try {
            int bytesRead = stream.read(buffer, offset, size);
            if (bytesRead != size) {
				Log.d(TAG, "Read underbuffered: " + bytesRead + " != " + size + ", position: " + position);
            }
            return bytesRead;
        } catch (IOException ex) {
            ex.printStackTrace();
            if (ex.getCause() instanceof IntegrityException && !integrityFailed) {
                // showing integrity error only once
                integrityFailed = true;
                if (onError != null)
                    onError.accept("File is corrupt or tampered");
            }
            throw ex;
        }
    }

    /**
     * Get the content size.
     *
     * @return The size
     */
    public long getSize() {
        return size;
    }

    /**
     * Close the source and all associated resources.
     *
     * @throws IOException Thrown if error during IO
     */
    public void close() throws IOException {
        stream.close();
    }
}

