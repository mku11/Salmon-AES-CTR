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
import com.mku.salmon.sequence.SequenceException;
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
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @throws SequenceException  Thrown if there is an error with the nonce sequence
     */
    RandomAccessStream getOutputStream() throws IOException;

    /**
     * Lists files and directories under this directory
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
    long getLastDateTimeModified();

    /**
     * Return the virtual size of the file.
     *
     * @return The size in bytes.
     */
    long getSize() throws IOException;

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
     */
    IVirtualFile createDirectory(String dirName) throws IOException;

    /**
     * Create a file under this directory
     *
     * @param filename The file name.
     */
    IVirtualFile createFile(String filename) throws IOException;

    /**
     * Rename the virtual file name
     *
     * @param newFilename The new filename this file will be renamed to
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
     * @param OnProgressListener Observer to notify when move progress changes.
     * @return The file
     * @throws IOException Thrown if there is an IO error.
     */
    IVirtualFile move(IVirtualFile dir, BiConsumer<Long, Long> OnProgressListener) throws IOException;

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
     * @param OnProgressListener Observer to notify when copy progress changes.
     * @return The file
     * @throws IOException Thrown if there is an IO error.
     */
    IVirtualFile copy(IVirtualFile dir, BiConsumer<Long, Long> OnProgressListener) throws IOException;

    /**
     * Copy a directory recursively
     *
     * @param dest              The destination directory
     * @param autoRename        The autorename function
     * @param autoRenameFolders True to also auto rename folders
     */
    public void copyRecursively(IVirtualFile dest,
                                Function<IVirtualFile, String> autoRename,
                                boolean autoRenameFolders) throws IOException;

    /**
     * Copy a directory recursively
     *
     * @param dest              The destination directory
     * @param autoRename        The autorename function
     * @param autoRenameFolders True to also auto rename folders
     * @param onFailed          The callback when file copying has failed
     */
    public void copyRecursively(IVirtualFile dest,
                                Function<IVirtualFile, String> autoRename,
                                boolean autoRenameFolders,
                                BiConsumer<IVirtualFile, Exception> onFailed) throws IOException;

    /**
     * Copy a directory recursively
     *
     * @param dest              The destination directory
     * @param autoRename        The autorename function
     * @param autoRenameFolders True to also auto rename folders
     * @param onFailed          The callback when file copying has failed
     * @param progressListener  The progress listener
     */
    public void copyRecursively(IVirtualFile dest,
                                Function<IVirtualFile, String> autoRename,
                                boolean autoRenameFolders,
                                BiConsumer<IVirtualFile, Exception> onFailed,
                                TriConsumer<IVirtualFile, Long, Long> progressListener) throws IOException;

    /**
     * Move a directory recursively
     *
     * @param dest              The destination directory
     * @param autoRename        The autorename function
     * @param autoRenameFolders True to also auto rename folder
     */
    public void moveRecursively(IVirtualFile dest,
                                Function<IVirtualFile, String> autoRename,
                                boolean autoRenameFolders) throws IOException;

    /**
     * Move a directory recursively
     *
     * @param dest              The destination directory
     * @param autoRename        The autorename function
     * @param autoRenameFolders True to also auto rename folder
     * @param onFailed          Callback when move fails
     */
    public void moveRecursively(IVirtualFile dest,
                                Function<IVirtualFile, String> autoRename,
                                boolean autoRenameFolders,
                                BiConsumer<IVirtualFile, Exception> onFailed) throws IOException;

    /**
     * Move a directory recursively
     *
     * @param dest              The destination directory
     * @param autoRename        The autorename function
     * @param autoRenameFolders True to also auto rename folders.
     * @param onFailed          Callback when move fails
     * @param progressListener  The progress listener
     */
    public void moveRecursively(IVirtualFile dest,
                                Function<IVirtualFile, String> autoRename,
                                boolean autoRenameFolders,
                                BiConsumer<IVirtualFile, Exception> onFailed,
                                TriConsumer<IVirtualFile, Long, Long> progressListener) throws IOException;

    /**
     * Delete all subdirectories and files.
     */
    void deleteRecursively();

    /**
     * Delete all subdirectories and files.
     *
     * @param onFailed Called when file fails during deletion.
     */
    void deleteRecursively(BiConsumer<IVirtualFile, Exception> onFailed);

    /**
     * Delete all subdirectories and files.
     *
     * @param onFailed         Called when file fails during deletion.
     * @param progressListener Called when progress is changed.
     */
    void deleteRecursively(BiConsumer<IVirtualFile, Exception> onFailed,
                           TriConsumer<IVirtualFile, Long, Long> progressListener);

}
