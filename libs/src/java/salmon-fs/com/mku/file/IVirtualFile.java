package com.mku.file;
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

import com.mku.func.BiConsumer;
import com.mku.func.Function;
import com.mku.func.TriConsumer;
import com.mku.streams.RandomAccessStream;

import java.io.IOException;

/*
 * A virtual file. Read-only operations are included. Since write operations can be implementation
 * specific ie for encryption they can be implemented by extending this class.
 */
public interface IVirtualFile {
    RandomAccessStream getInputStream() throws IOException;

    RandomAccessStream getOutputStream() throws IOException;

    IVirtualFile[] listFiles();

    IVirtualFile getChild(String filename) throws IOException;

    boolean isFile();

    boolean isDirectory();

    String getPath() throws IOException;

    String getRealPath();

    IRealFile getRealFile();

    String getBaseName() throws IOException;

    IVirtualFile getParent();

    void delete();

    void mkdir();

    long getLastDateTimeModified();

    long getSize() throws IOException;

    boolean exists();

    IVirtualFile createDirectory(String dirName) throws IOException;

    IVirtualFile createFile(String realFilename) throws IOException;

    void rename(String newFilename) throws IOException;

    IVirtualFile move(IVirtualFile dir, BiConsumer<Long, Long> OnProgressListener) throws IOException;

    IVirtualFile copy(IVirtualFile dir, BiConsumer<Long, Long> OnProgressListener) throws IOException;

    void moveRecursively(IVirtualFile dest,
                         TriConsumer<IVirtualFile, Long, Long> progressListener,
                         Function<IVirtualFile, String> autoRename,
                         boolean autoRenameFolders,
                         BiConsumer<IVirtualFile, Exception> onFailed)
            throws IOException;

    void copyRecursively(IVirtualFile dest,
                         TriConsumer<IVirtualFile, Long, Long> progressListener,
                         Function<IVirtualFile, String> autoRename,
                         boolean autoRenameFolders,
                         BiConsumer<IVirtualFile, Exception> onFailed)
            throws IOException;

    void deleteRecursively(TriConsumer<IVirtualFile, Long, Long> progressListener,
                           BiConsumer<IVirtualFile, Exception> onFailed);
}
