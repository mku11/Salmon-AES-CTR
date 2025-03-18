package com.mku.fs.file;
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
import com.mku.salmon.SecurityException;
import com.mku.salmon.integrity.IntegrityException;
import com.mku.salmonfs.auth.AuthException;
import com.mku.streams.RandomAccessStream;

import java.io.IOException;

/**
 * A virtual file. Read-only operations are included. Since write operations can be implementation
 * specific ie for encryption they can be implemented by extending this class.
 */
public interface IVirtualFile {
    /**
     * Retrieves a stream that will be used for reading the file contents.
     *
     * @return The input stream
     * @throws IOException        Thrown if there is an IO error.
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    RandomAccessStream getInputStream() throws IOException;

    /**
     * Retrieves a stream that will be used for writing the file contents.
     *
     * @return The output stream
     * @throws IOException Thrown if there is a problem with the stream.
     */
    RandomAccessStream getOutputStream() throws IOException;

    /**
     * Lists files and directories under this directory
     *
     * @return An array of files and subdirectories.
     */
    IVirtualFile[] listFiles();

    /**
     * Get a child with this filename.
     *
     * @param filename The filename to search for
     * @return The child file
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @throws IOException        Thrown if there is an IO error.
     * @throws AuthException      Thrown if there is an Authorization error
     */
    IVirtualFile getChild(String filename) throws IOException;

    /**
     * Check if this is a file.
     *
     * @return True if it is a file.
     */
    boolean isFile();

    /**
     * Check if this is a directory.
     *
     * @return True if it is a directory.
     */
    boolean isDirectory();

    /**
     * Return the virtual path for this file.
     *
     * @return The virtual path.
     * @throws IOException Thrown if there is an IO error.
     */
    String getPath() throws IOException;

    /**
     * Return the path of the real file.
     *
     * @return The real path.
     */
    String getRealPath();

    /**
     * Return the real file
     *
     * @return The real file
     */
    IFile getRealFile();

    /**
     * Returns the file name
     *
     * @return The file name
     * @throws IOException if there was a problem with the stream
     */
    String getName() throws IOException;

    /**
     * Returns the virtual parent directory.
     *
     * @return The parent directory.
     */
    IVirtualFile getParent();

    /**
     * Delete this file.
     */
    void delete();

    /**
     * Create this directory.
     */
    void mkdir();

    /**
     * Returns the last date modified in milliseconds.
     *
     * @return The last date modified in milliseconds.
     */
    long getLastDateModified();

    /**
     * Return the virtual size of the file.
     *
     * @return The size in bytes.
     * @throws IOException if there was a problem with the stream
     */
    long getLength() throws IOException;

    /**
     * Returns true if this file exists
     *
     * @return True if exists.
     */
    boolean exists();

    /**
     * Creates a directory under this directory
     *
     * @param dirName The name of the directory to be created
     * @return The new directory.
     * @throws IOException if there was a problem with the stream
     */
    IVirtualFile createDirectory(String dirName) throws IOException;

    /**
     * Create a file under this directory
     *
     * @param filename The file name.
     * @return The new file.
     * @throws IOException if there was a problem with the stream
     */
    IVirtualFile createFile(String filename) throws IOException;

    /**
     * Rename the virtual file name
     *
     * @param newFilename The new filename this file will be renamed to
     * @throws IOException if there was a problem with the stream
     */
    void rename(String newFilename) throws IOException;

    /**
     * Move file to another directory.
     *
     * @param dir Target directory.
     * @return The file
     * @throws IOException Thrown if there is an IO error.
     */
    IVirtualFile move(IVirtualFile dir) throws IOException;

    /**
     * Move file to another directory.
     *
     * @param dir                Target directory.
     * @param options The options
     * @return The file
     * @throws IOException Thrown if there is an IO error.
     */
    IVirtualFile move(IVirtualFile dir, IFile.MoveOptions options) throws IOException;

    /**
     * Copy file to another directory.
     *
     * @param dir Target directory.
     * @return The file
     * @throws IOException Thrown if there is an IO error.
     */
    IVirtualFile copy(IVirtualFile dir) throws IOException;

    /**
     * Copy file to another directory.
     *
     * @param dir                Target directory.
     * @param options The options
     * @return The file
     * @throws IOException Thrown if there is an IO error.
     */
    IVirtualFile copy(IVirtualFile dir, IFile.CopyOptions options) throws IOException;

    /**
     * Copy a directory recursively
     *
     * @param dest              The destination directory
     * @throws IOException Thrown if there is an IO error.
     */
    public void copyRecursively(IVirtualFile dest) throws IOException;

    /**
     * Copy a directory recursively
     *
     * @param dest              The destination directory
     * @param options The options
     * @throws IOException Thrown if there is an IO error.
     */
    public void copyRecursively(IVirtualFile dest, VirtualRecursiveCopyOptions options) throws IOException;

    /**
     * Move a directory recursively
     *
     * @param dest              The destination directory
     * @throws IOException Thrown if there is an IO error.
     */
    public void moveRecursively(IVirtualFile dest) throws IOException;

    /**
     * Move a directory recursively
     *
     * @param dest              The destination directory
     * @param options The options
     * @throws IOException Thrown if there is an IO error.
     */
    public void moveRecursively(IVirtualFile dest, VirtualRecursiveMoveOptions options) throws IOException;

    /**
     * Delete all subdirectories and files.
     */
    void deleteRecursively();

    /**
     * Delete all subdirectories and files.
     *
     * @param options The options
     */
    void deleteRecursively(VirtualRecursiveDeleteOptions options);

    /**
     * Directory copy options (recursively)
     */
    public class VirtualRecursiveCopyOptions {
        /**
         * Callback when file with same name exists
         */
        public Function<IVirtualFile, String> autoRename;

        /**
         * True to autorename folders
         */
        public boolean autoRenameFolders = false;

        /**
         * Callback when file changes
         */
        public BiConsumer<IVirtualFile, Exception> onFailed;

        /**
         * Callback where progress changed
         */
        public TriConsumer<IVirtualFile, Long, Long> onProgressChanged;
    }

    /**
     * Directory move options (recursively)
     */
    public class VirtualRecursiveMoveOptions {
        /**
         * Callback when file with the same name exists
         */
        public Function<IVirtualFile, String> autoRename;

        /**
         * True to autorename folders
         */
        public boolean autoRenameFolders = false;

        /**
         * Callback when file failed
         */
        public BiConsumer<IVirtualFile, Exception> onFailed;

        /**
         * Callback when progress changes
         */
        public TriConsumer<IVirtualFile, Long, Long> onProgressChanged;
    }

    /**
     * Directory move options (recursively)
     */
    public class VirtualRecursiveDeleteOptions {
        /**
         * Callback when file failed
         */
        public BiConsumer<IVirtualFile, Exception> onFailed;

        /**
         * Callback when progress changed
         */
        public TriConsumer<IVirtualFile, Long, Long> onProgressChanged;
    }
}
