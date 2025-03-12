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

import android.app.Activity;
import android.media.MediaDataSource;
import android.util.Log;
import android.widget.Toast;

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
public class SalmonMediaDataSource extends MediaDataSource {
    private static final String TAG = SalmonMediaDataSource.class.getName();

    private final Activity activity;
    private final InputStream stream;
    private final AesFile aesFile;
    private final boolean enableMultiThreaded = true;

    private boolean integrityFailed;
    private long size;

    /**
     * Construct a seekable source for the media player from an encrypted file source
     *
     * @param activity   Activity this data source will be used with. This is usually the activity the MediaPlayer is attached to
     * @param aesFile    AesFile that will be used as a source
     * @param buffers    The buffers
     * @param bufferSize Buffer size
     * @param threads    Threads for parallel processing
     * @param backOffset The backwards offset to use when reading buffers
     * @throws Exception Thrown if error occured
     */
    public SalmonMediaDataSource(Activity activity, AesFile aesFile,
                                 int buffers, int bufferSize, int threads, int backOffset) throws Exception {
        this.activity = activity;
        this.aesFile = aesFile;
        this.size = aesFile.getLength();

        if (enableMultiThreaded)
            this.stream = new AesFileInputStream(aesFile, buffers, bufferSize, threads, backOffset);
        else {
            AesStream fStream = aesFile.getInputStream();
            this.stream = new InputStreamWrapper(fStream);
        }
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
//        Log.d(TAG, "readAt: position=" + position + ",offset=" + offset + ",size=" + size);
        stream.reset();
        stream.skip(position);
        try {
            int bytesRead = stream.read(buffer, offset, size);
//            Log.d(TAG, "bytesRead: " + bytesRead);
            if (bytesRead != size) {
                Log.e(TAG, "read not same as size: " + bytesRead + " != " + size
                        + ", position: " + position);
            }
            return bytesRead;
        } catch (IOException ex) {
            ex.printStackTrace();
            if (ex.getCause() instanceof IntegrityException && !integrityFailed) {
                // showing integrity error only once
                integrityFailed = true;
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(AndroidDrive.getContext(), "File is corrupt or tampered",
                                Toast.LENGTH_LONG).show();
                    });
                }
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
        // Log.d(TAG, "closed");
    }
}

