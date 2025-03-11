package com.mku.android.streams;
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

import android.os.ParcelFileDescriptor;

import com.mku.android.fs.file.AndroidFile;
import com.mku.streams.RandomAccessStream;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Class is a stream implementation wrapper for java streams that are retrieved from AndroidFile
 * which support external SD cards.
 */
public class AndroidFileStream extends RandomAccessStream {
    private final AndroidFile file;
    private final ParcelFileDescriptor pfd;
    private boolean canWrite;
    private final FileChannel fileChannel;

    /**
     * True if the stream is readable.
     * @return True if readable
     */
    public boolean canRead() {
        return fileChannel.isOpen() && !canWrite;
    }

    /**
     * True if the stream is writeable.
     * @return True if writeable
     */
    public boolean canWrite() {
        return fileChannel.isOpen() && canWrite;
    }

    /**
     * True if the stream is seekable (random access).
     * @return True if seekable
     */
    public boolean canSeek() {
        return true;
    }

    /**
     * Get the length of the stream.
     * @return The length
     */
    public long length() {
        return file.length();
    }

    /**
     * Get the current position of the stream.
     * @return The current position
     * @throws IOException Thrown if error during IO
     */
    public long getPosition() throws IOException {
        return fileChannel.position();
    }

    /**
     * Set the current position of the stream.
     * @param value The new position.
     * @throws IOException Thrown if error during IO
     */
    public void setPosition(long value) throws IOException {
        fileChannel.position(value);
    }

    /**
     * Construct a file stream from an AndroidFile.
     * This will create a wrapper stream that will route read() and write() to the Android FileChannel
     *
     * @param file The AndroidFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     * @throws FileNotFoundException Thrown if file is not found
     */
    public AndroidFileStream(AndroidFile file, String mode) throws FileNotFoundException {
        this.file = file;
        if (mode.equals("rw")) {
            canWrite = true;
        }
        pfd = file.getFileDescriptor(mode);
        if (canWrite) {
            FileOutputStream outs = new FileOutputStream(pfd.getFileDescriptor());
            fileChannel = outs.getChannel();
        } else {
            FileInputStream ins = new FileInputStream(pfd.getFileDescriptor());
            fileChannel = ins.getChannel();
        }
    }

    /**
     * Set the length of the stream.
     * @param value The length.
     * @throws IOException Thrown if error during IO
     */
    public void setLength(long value) throws IOException {
        fileChannel.position(value);
    }

    /**
     * Read data from the stream into the buffer.
     * @param buffer The buffer to read into
     * @param offset The offset to start reading into
     * @param count The number of bytes to read
     * @return The number of bytes read.
     * @throws IOException Thrown if error during IO
     */
    public int read(byte[] buffer, int offset, int count) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(count);
        int bytesRead = fileChannel.read(buf);
        if (bytesRead <= 0)
            return -1;
        buf.rewind();
        buf.get(buffer, offset, bytesRead);
        return bytesRead;
    }

    /**
     * Write the data buffer to the stream.
     * @param buffer The buffer to read the contents from.
     * @param offset The position the reading will start from.
     * @param count The count of bytes to be read from the buffer.
     * @throws IOException Thrown if error during IO
     */
    public void write(byte[] buffer, int offset, int count) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(count);
        buf.put(buffer, offset, count);
        buf.rewind();
        fileChannel.write(buf);
    }

    /**
     * Seek to the requested position.
     * @param offset The new position.
     * @param origin The origin type.
     * @return The current position after seeking
     * @throws IOException Thrown if error during IO
     */
    public long seek(long offset, SeekOrigin origin) throws IOException {
        long pos = fileChannel.position();

        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = file.length() - offset;

        fileChannel.position(pos);
        return fileChannel.position();

    }

    /**
     * Flush the buffers to the stream.
     */
    public void flush() {
        try {
            if (fileChannel.isOpen()) {
                fileChannel.force(true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Close the stream.
     * @throws IOException Thrown if error during IO
     */
    public void close() throws IOException {
        fileChannel.close();
        pfd.close();
    }
}
