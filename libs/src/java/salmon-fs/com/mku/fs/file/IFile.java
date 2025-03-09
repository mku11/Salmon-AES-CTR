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
import com.mku.streams.RandomAccessStream;
import com.mku.fs.drive.utils.FileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * A real file. This class is used internally by the virtual disk to
 * import, store, and export the encrypted files.
 * Extend this to provide an interface to any file system, platform, or API ie: on disk, memory, network, or cloud.
 */
public interface IFile {
    /**
     * Check if this file exists.
     *
     * @return True if file exists
     */
    boolean exists();

    /**
     * Delete this file.
     *
     * @return True if file deleted.
     */
    boolean delete();

    /**
     * Get a stream for reading the file.
     *
     * @return The input stream
     * @throws FileNotFoundException Thrown if file not found
     */
    RandomAccessStream getInputStream() throws FileNotFoundException;

    /**
     * Get a stream for writing to the file.
     *
     * @return The output stream
     * @throws FileNotFoundException Thrown if file not found
     */
    RandomAccessStream getOutputStream() throws FileNotFoundException;

    /**
     * Rename file.
     *
     * @param newFilename The new filename
     * @return True if success.
     * @throws FileNotFoundException Thrown if file not found
     */
    boolean renameTo(String newFilename) throws FileNotFoundException;

    /**
     * Get the length for the file.
     *
     * @return The length.
     */
    long length();

    /**
     * Get the count of files and subdirectories
     *
     * @return The children count
     */
    int getChildrenCount();

    /**
     * Get the last modified date of the file.
     *
     * @return The last date modified in milliseconds
     */
    long lastModified();

    /**
     * Get the display path of the file on disk.
     *
     * @return The display path
     */
    String getDisplayPath();

    /**
     * Get the original filepath of this file. This might symlinks or merged folders. To get the display path
     * use {@link #getDisplayPath()}.
     *
     * @return The file path
     */
    String getPath();

    /**
     * Check if this is a file.
     *
     * @return True if this is a file
     */
    boolean isFile();

    /**
     * Check if this is a directory.
     *
     * @return True if this is a directory.
     */
    boolean isDirectory();

    /**
     * Get all files and directories under this directory.
     *
     * @return The files
     */
    IFile[] listFiles();

    /**
     * Get the file name
     *
     * @return The file name
     */
    String getName();

    /**
     * Create the directory with the name provided under this directory.
     *
     * @param dirName Directory name.
     * @return The newly created directory.
     */
    IFile createDirectory(String dirName);

    /**
     * Get the parent directory of this file/directory.
     *
     * @return The parent directory.
     */
    IFile getParent();

    /**
     * Create an empty file with the provided name.
     *
     * @param filename The name for the new file.
     * @return The newly create file.
     * @throws IOException Thrown if there is an IO error.
     */
    IFile createFile(String filename) throws IOException;

    /**
     * Move this file to another directory.
     *
     * @param newDir The target directory.
     * @return The file after the move. Use this instance for any subsequent file operations.
     * @throws IOException Thrown if there is an IO error.
     */
    IFile move(IFile newDir) throws IOException;

    /**
     * Move this file to another directory.
     *
     * @param newDir  The target directory.
     * @param newName The new filename.
     * @return The file after the move. Use this instance for any subsequent file operations.
     * @throws IOException Thrown if there is an IO error.
     */
    IFile move(IFile newDir, String newName) throws IOException;

    /**
     * Move this file to another directory.
     *
     * @param newDir           The target directory.
     * @param newName          The new filename.
     * @param progressListener Observer to notify of the move progress.
     * @return The file after the move. Use this instance for any subsequent file operations.
     * @throws IOException Thrown if there is an IO error.
     */
    IFile move(IFile newDir, String newName, BiConsumer<Long, Long> progressListener) throws IOException;

    /**
     * Copy this file to another directory.
     *
     * @param newDir The target directory.
     * @return The file after the copy. Use this instance for any subsequent file operations.
     * @throws IOException Thrown if there is an IO error.
     */
    IFile copy(IFile newDir) throws IOException;

    /**
     * Copy this file to another directory.
     *
     * @param newDir  The target directory.
     * @param newName The new filename.
     * @return The file after the copy. Use this instance for any subsequent file operations.
     * @throws IOException Thrown if there is an IO error.
     */
    IFile copy(IFile newDir, String newName) throws IOException;

    /**
     * Copy this file to another directory.
     *
     * @param newDir           The target directory.
     * @param newName          The new filename.
     * @param progressListener Observer to notify of the copy progress.
     * @return The file after the copy. Use this instance for any subsequent file operations.
     * @throws IOException Thrown if there is an IO error.
     */
    IFile copy(IFile newDir, String newName, BiConsumer<Long, Long> progressListener) throws IOException;

    /**
     * Get the file/directory matching the name provided under this directory.
     *
     * @param filename The name of the file or directory to match.
     * @return The file that was matched.
     */
    IFile getChild(String filename);

    /**
     * Create a directory with the current filepath.
     *
     * @return True if directory was created
     */
    boolean mkdir();

    /**
     * Reset cached properties
     */
    void reset();

    /**
     * Copy contents of a file to another file.
     *
     * @param src              The source directory
     * @param dest             The target directory
     * @param delete           True to delete the source files when complete
     * @param progressListener The progress listener
     * @return True if contents were copied
     * @throws IOException Thrown if there is an IO error.
     */
    static boolean copyFileContents(IFile src, IFile dest, boolean delete,
                                    BiConsumer<Long, Long> progressListener)
            throws IOException {
        RandomAccessStream source = src.getInputStream();
        RandomAccessStream target = dest.getOutputStream();
        try {
            source.copyTo(target, progressListener);
        } catch (Exception ex) {
            dest.delete();
            return false;
        } finally {
            source.close();
            target.close();
        }
        if (delete)
            src.delete();
        return true;
    }

    /**
     * Copy a directory recursively
     *
     * @param dest Destination directory
     * @throws IOException Thrown if there is an IO error.
     */
    default void copyRecursively(IFile dest) throws IOException {
        copyRecursively(dest, null, null, true, null);
    }

    /**
     * Copy a directory recursively
     *
     * @param destDir           The destination directory
     * @param progressListener  The progress listener
     * @param autoRename        The autorename function
     * @param autoRenameFolders Apply autorename to folders also (default is true)
     * @param onFailed          Callback if copy failed
     * @throws IOException Thrown if there is an IO error.
     */
    default void copyRecursively(IFile destDir,
                                 TriConsumer<IFile, Long, Long> progressListener,
                                 Function<IFile, String> autoRename,
                                 boolean autoRenameFolders,
                                 BiConsumer<IFile, Exception> onFailed) throws IOException {
        String newFilename = getName();
        IFile newFile;
        newFile = destDir.getChild(newFilename);
        if (isFile()) {
            if (newFile != null && newFile.exists()) {
                if (autoRename != null) {
                    newFilename = autoRename.apply(this);
                } else {
                    if (onFailed != null)
                        onFailed.accept(this, new Exception("Another file exists"));
                    return;
                }
            }
            this.copy(destDir, newFilename, (position, length) ->
            {
                if (progressListener != null) {
                    progressListener.accept(this, position, length);
                }
            });
        } else if (this.isDirectory()) {
            if (progressListener != null)
                progressListener.accept(this, 0L, 1L);
            if (destDir.getDisplayPath().startsWith(this.getDisplayPath())) {
                if (progressListener != null)
                    progressListener.accept(this, 1L, 1L);
                return;
            }
            if (newFile != null && newFile.exists() && autoRename != null && autoRenameFolders)
                newFile = destDir.createDirectory(autoRename.apply(this));
            else if (newFile == null || !newFile.exists())
                newFile = destDir.createDirectory(newFilename);
            if (progressListener != null)
                progressListener.accept(this, 1L, 1L);

            for (IFile child : this.listFiles()) {
                child.copyRecursively(newFile, progressListener, autoRename, autoRenameFolders, onFailed);
            }
        }
    }

    /**
     * Move a directory recursively
     *
     * @param dest The target directory
     * @throws IOException Thrown if there is an IO error.
     */
    default void moveRecursively(IFile dest) throws IOException {
        moveRecursively(dest, null, null, true, null);
    }

    /**
     * Move a directory recursively
     *
     * @param destDir           The target directory
     * @param progressListener  The progress listener
     * @param autoRename        The autorename function
     * @param autoRenameFolders Apply autorename to folders also (default is true)
     * @param onFailed          Callback when move failed
     * @throws IOException Thrown if there is an IO error.
     */
    default void moveRecursively(IFile destDir,
                                 TriConsumer<IFile, Long, Long> progressListener,
                                 Function<IFile, String> autoRename,
                                 boolean autoRenameFolders,
                                 BiConsumer<IFile, Exception> onFailed) throws IOException {
        // target directory is the same
        if (getParent().getPath().equals(destDir.getPath())) {
            if (progressListener != null) {
                progressListener.accept(this, 0L, 1L);
                progressListener.accept(this, 1L, 1L);
            }
            return;
        }

        String newFilename = getName();
        IFile newFile;
        newFile = destDir.getChild(newFilename);
        if (isFile()) {
            if (newFile != null && newFile.exists()) {
                if (newFile.getPath().equals(this.getPath()))
                    return;
                if (autoRename != null) {
                    newFilename = autoRename.apply(this);
                } else {
                    if (onFailed != null)
                        onFailed.accept(this, new Exception("Another file exists"));
                    return;
                }
            }
            this.move(destDir, newFilename, (position, length) ->
            {
                if (progressListener != null) {
                    progressListener.accept(this, position, length);
                }
            });
        } else if (this.isDirectory()) {
            if (progressListener != null)
                progressListener.accept(this, 0L, 1L);
            if (destDir.getDisplayPath().startsWith(this.getDisplayPath())) {
                if (progressListener != null)
                    progressListener.accept(this, 1L, 1L);
                return;
            }
            if ((newFile != null && newFile.exists() && autoRename != null && autoRenameFolders)
                    || newFile == null || !newFile.exists()) {
                newFile = move(destDir, autoRename.apply(this));
                return;
            }
            if (progressListener != null)
                progressListener.accept(this, 1L, 1L);

            for (IFile child : this.listFiles()) {
                child.moveRecursively(newFile, progressListener, autoRename, autoRenameFolders, onFailed);
            }
            if (!this.delete()) {
                onFailed.accept(this, new Exception("Could not delete source directory"));
                return;
            }
        }
    }

    /**
     * Delete a directory recursively
     *
     * @param progressListener The progress listener
     * @param onFailed         Callback when delete failed
     */
    default void deleteRecursively(TriConsumer<IFile, Long, Long> progressListener,
                                   BiConsumer<IFile, Exception> onFailed) {
        if (isFile()) {
            progressListener.accept(this, 0L, 1L);
            if (!this.delete()) {
                onFailed.accept(this, new Exception("Could not delete file"));
                return;
            }
            progressListener.accept(this, 1L, 1L);
        } else if (this.isDirectory()) {
            for (IFile child : this.listFiles()) {
                child.deleteRecursively(progressListener, onFailed);
            }
            if (!this.delete()) {
                onFailed.accept(this, new Exception("Could not delete directory"));
                return;
            }
        }
    }

    /**
     * Get an auto generated copy of the name for a file.
     */
    Function<IFile, String> autoRename = (IFile file) -> {
        return autoRename(file.getName());
    };

    /**
     * Provide an alternative file name. Use this to rename files.
     *
     * @param filename The current file name
     * @return The new file name
     */
    static String autoRename(String filename) {
        String ext = FileUtils.getExtensionFromFileName(filename);
        String filenameNoExt;
        if (ext.length() > 0)
            filenameNoExt = filename.substring(0, filename.length() - ext.length() - 1);
        else
            filenameNoExt = filename;
        String newFilename = filenameNoExt + " (" + new SimpleDateFormat("HHmmssSSS").format(Calendar.getInstance().getTime()) + ")";
        if (ext.length() > 0)
            newFilename += "." + ext;
        return newFilename;
    }
}

