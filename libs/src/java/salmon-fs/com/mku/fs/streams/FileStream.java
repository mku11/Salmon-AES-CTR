package com.mku.fs.stream;
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

import com.mku.fs.file.File;
import com.mku.streams.RandomAccessStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * File stream implementation for local files.
 */
public class FileStream extends RandomAccessStream {

    /**
     * The random access file associated with this stream.
     */
    private final RandomAccessFile raf;

    /**
     * The java file associated with this stream.
     */
    private final File file;

    /**
     * The file channel associated with this stream.
     */
    private final FileChannel fileChannel;

    private boolean canWrite;

    /**
     * Construct a file stream from a JavaFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The JavaFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     * @throws FileNotFoundException Thrown if file not found
     */
    public FileStream(File file, String mode) throws FileNotFoundException {
        this.file = file;
        if (mode.equals("rw")) {
            canWrite = true;
        }
        raf = new RandomAccessFile(file.getPath(), mode);
        fileChannel = raf.getChannel();
    }

    /**
     * Check if stream can read from file.
     * @return True if readable
     */
    @Override
    public boolean canRead() {
        return fileChannel.isOpen() && (!canWrite);
    }

    /**
     * Check if stream can write to file.
     * @return True if writable
     */
    @Override
    public boolean canWrite() {
        return fileChannel.isOpen() && (canWrite);
    }

    /**
     * Check if stream can seek.
     * @return True if seekable
     */
    @Override
    public boolean canSeek() {
        return true;
    }

    /**
     * Get the length of the stream. This is the same as the backed file.
     * @return The file stream length
     */
    @Override
    public long getLength() {
        return file.getLength();
    }

    /**
     * Get the current position of the stream.
     * @return The current position
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public long getPosition() throws IOException {
        return fileChannel.position();
    }

    /**
     * Set the current position of the stream.
     * @param value The new position.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void setPosition(long value) throws IOException {
        fileChannel.position(value);
    }

    /**
     * Set the length of the stream. This is applicable for write streams only.
     * @param value The new length.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void setLength(long value) throws IOException {
        fileChannel.position(value);
    }

    /**
     * Read data from the file stream into the buffer provided.
     * @param buffer The buffer to write the data.
     * @param offset The offset of the buffer to start writing the data.
     * @param count The maximum number of bytes to read from.
     * @return The bytes read
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
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
     * Write the data from the buffer provided into the stream.
     * @param buffer The buffer to read the data from.
     * @param offset The offset of the buffer to start reading the data.
     * @param count The maximum number of bytes to read from the buffer.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(count);
        buf.put(buffer, offset, count);
        buf.rewind();
        fileChannel.write(buf);
    }

    /**
     * Seek to the offset provided.
     * @param offset The position to seek to.
     * @param origin The type of origin {@link RandomAccessStream.SeekOrigin}
     * @return The new position after seeking.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public long seek(long offset, SeekOrigin origin) throws IOException {
        long pos = fileChannel.position();

        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = file.getLength() - offset;

        fileChannel.position(pos);
        return fileChannel.position();

    }

    /**
     * Flush the buffers to the associated file.
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
     * Close this stream and associated resources.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void close() throws IOException {
        fileChannel.close();
        fileChannel.close();
        raf.close();
    }
}
