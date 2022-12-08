package com.mku11.file;
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

import com.mku11.salmon.streams.AbsStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Stream implementation wrapper for java streams that are retrieved from AndroidFile
 * which support external SD cards.
 */
public class JavaFileStream extends AbsStream {

    private final RandomAccessFile raf;
    private final JavaFile file;
    private final FileChannel fileChannel;
    private boolean canWrite;

    /**
     * Construct a file stream from an AndroidFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The AndroidFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     */
    public JavaFileStream(JavaFile file, String mode) throws FileNotFoundException {
        this.file = file;
        if (mode.equals("rw")) {
            canWrite = true;
        }
        raf = new RandomAccessFile(file.getPath(), mode);
        fileChannel = raf.getChannel();
    }

    @Override
    public boolean canRead() {
        return fileChannel.isOpen() && (!canWrite);
    }

    @Override
    public boolean canWrite() {
        return fileChannel.isOpen() && (canWrite);
    }

    @Override
    public boolean canSeek() {
        return true;
    }

    @Override
    public long length() {
        return file.length();
    }

    @Override
    public long position() throws IOException {
        return fileChannel.position();
    }

    @Override
    public void position(long value) throws IOException {
        fileChannel.position(value);
    }

    @Override
    public void setLength(long value) throws IOException {
        fileChannel.position(value);
    }

    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(count);
        int bytesRead = fileChannel.read(buf);
        if (bytesRead <= 0)
            return 0;
        buf.rewind();
        buf.get(buffer, offset, bytesRead);
        return bytesRead;
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(count);
        buf.put(buffer, offset, count);
        buf.rewind();
        fileChannel.write(buf);
    }

    @Override
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

    public void flush() {
        try {
            if (fileChannel.isOpen()) {
                fileChannel.force(true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        fileChannel.close();
        fileChannel.close();
        raf.close();
    }

}
