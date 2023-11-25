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
import com.mku.io.RandomAccessStream;
import com.mku.utils.SalmonFileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Interface that represents a real file. This class is used internally by the virtual disk to
 * import, store, and export the encrypted files.
 * Extend this to provide an interface to any file system, platform, or API ie: on disk, memory, network, or cloud.
 * See: {@link JavaFile}
 */
public interface IRealFile {
    /**
     * True if this file exists.
     *
     * @return
     */
    boolean exists();

    /**
     * Delete this file.
     *
     * @return
     */
    boolean delete();

    /**
     * Get a stream for reading the file.
     *
     * @return
     * @throws FileNotFoundException
     */
    RandomAccessStream getInputStream() throws FileNotFoundException;

    /**
     * Get a stream for writing to the file.
     *
     * @return
     * @throws FileNotFoundException
     */
    RandomAccessStream getOutputStream() throws FileNotFoundException;

    /**
     * Rename file.
     *
     * @param newFilename The new filename
     * @return True if success.
     * @throws FileNotFoundException
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
     * @return
     */
    int getChildrenCount();

    /**
     * Get the last modified date of the file.
     *
     * @return
     */
    long lastModified();

    /**
     * Get the absolute path of the file on disk.
     *
     * @return
     */
    String getAbsolutePath();

    /**
     * Get the original filepath of this file. This might symlinks or merged folders. To get the absolute path
     * use {@link #getAbsolutePath()}.
     *
     * @return
     */
    String getPath();

    /**
     * True if this is a file.
     *
     * @return
     */
    boolean isFile();

    /**
     * True if this is a directory.
     *
     * @return
     */
    boolean isDirectory();

    /**
     * Get all files and directories under this directory.
     *
     * @return
     */
    IRealFile[] listFiles();

    /**
     * Get the basename of the file.
     *
     * @return
     */
    String getBaseName();

    /**
     * Create the directory with the name provided under this directory.
     *
     * @param dirName Directory name.
     * @return The newly created directory.
     */
    IRealFile createDirectory(String dirName);

    /**
     * Get the parent directory of this file/directory.
     *
     * @return The parent directory.
     */
    IRealFile getParent();

    /**
     * Create an empty file with the provided name.
     *
     * @param filename The name for the new file.
     * @return The newly create file.
     * @throws IOException
     */
    IRealFile createFile(String filename) throws IOException;

    /**
     * Move this file to another directory.
     *
     * @param newDir The target directory.
     * @return The file after the move. Use this instance for any subsequent file operations.
     */
    IRealFile move(IRealFile newDir) throws IOException;

    /**
     * Move this file to another directory.
     *
     * @param newDir  The target directory.
     * @param newName The new filename.
     * @return The file after the move. Use this instance for any subsequent file operations.
     */
    IRealFile move(IRealFile newDir, String newName) throws IOException;

    /**
     * Move this file to another directory.
     *
     * @param newDir           The target directory.
     * @param newName          The new filename.
     * @param progressListener Observer to notify of the move progress.
     * @return The file after the move. Use this instance for any subsequent file operations.
     */
    IRealFile move(IRealFile newDir, String newName, RandomAccessStream.OnProgressListener progressListener) throws IOException;

    /**
     * Copy this file to another directory.
     *
     * @param newDir The target directory.
     * @return The file after the copy. Use this instance for any subsequent file operations.
     * @throws IOException
     */
    IRealFile copy(IRealFile newDir) throws IOException;

    /**
     * Copy this file to another directory.
     *
     * @param newDir  The target directory.
     * @param newName The new filename.
     * @return The file after the copy. Use this instance for any subsequent file operations.
     * @throws IOException
     */
    IRealFile copy(IRealFile newDir, String newName) throws IOException;

    /**
     * Copy this file to another directory.
     *
     * @param newDir           The target directory.
     * @param newName          The new filename.
     * @param progressListener Observer to notify of the copy progress.
     * @return The file after the copy. Use this instance for any subsequent file operations.
     * @throws IOException
     */
    IRealFile copy(IRealFile newDir, String newName, RandomAccessStream.OnProgressListener progressListener) throws IOException;

    /**
     * Get the file/directory matching the name provided under this directory.
     *
     * @param filename The name of the file or directory to match.
     * @return The file that was matched.
     */
    IRealFile getChild(String filename);

    /**
     * Create a directory with the current filepath.
     *
     * @return
     */
    boolean mkdir();

    /**
     * Copy contents of a file to another file.
     *
     * @param src              The source directory
     * @param dest             The target directory
     * @param delete           True to delete the source files when complete
     * @param progressListener The progress listener
     * @return
     * @throws IOException
     */
    static boolean copyFileContents(IRealFile src, IRealFile dest, boolean delete,
                                    RandomAccessStream.OnProgressListener progressListener)
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
     * @param dest
     * @throws IOException
     */
    default void copyRecursively(IRealFile dest) throws IOException {
        copyRecursively(dest, null, null, true, null);
    }

    /**
     * Copy a directory recursively
     *
     * @param dest
     * @param progressListener
     * @param autoRename
     * @param autoRenameFolders Apply autorename to folders also (default is true)
     * @param onFailed
     * @throws IOException
     */
    default void copyRecursively(IRealFile dest,
                                 TriConsumer<IRealFile, Long, Long> progressListener,
                                 Function<IRealFile, String> autoRename,
                                 boolean autoRenameFolders,
                                 BiConsumer<IRealFile, Exception> onFailed) throws IOException {
        String newFilename = getBaseName();
        IRealFile newFile;
        newFile = dest.getChild(newFilename);
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
            this.copy(dest, newFilename, (position, length) ->
            {
                if (progressListener != null) {
                    progressListener.accept(this, position, length);
                }
            });
        } else if (this.isDirectory()) {
            if (progressListener != null)
                progressListener.accept(this, 0L, 1L);
            if (newFile != null && newFile.exists() && autoRename != null && autoRenameFolders)
                newFile = dest.createDirectory(autoRename.apply(this));
            else if (newFile == null || !newFile.exists())
                newFile = dest.createDirectory(newFilename);
            if (progressListener != null)
                progressListener.accept(this, 1L, 1L);

            for (IRealFile child : this.listFiles()) {
                child.copyRecursively(newFile, progressListener, autoRename, autoRenameFolders, onFailed);
            }
        }
    }

    /**
     * Move a directory recursively
     *
     * @param dest The target directory
     */
    default void moveRecursively(IRealFile dest) throws IOException {
        moveRecursively(dest, null, null, true, null);
    }

    /**
     * Move a directory recursively
     *
     * @param dest              The target directory
     * @param progressListener
     * @param autoRename
     * @param autoRenameFolders Apply autorename to folders also (default is true)
     * @param onFailed
     */
    default void moveRecursively(IRealFile dest,
                                 TriConsumer<IRealFile, Long, Long> progressListener,
                                 Function<IRealFile, String> autoRename,
                                 boolean autoRenameFolders,
                                 BiConsumer<IRealFile, Exception> onFailed) throws IOException {
        // target directory is the same
        if (getParent().getPath().equals(dest.getPath())) {
            if (progressListener != null) {
                progressListener.accept(this, 0L, 1L);
                progressListener.accept(this, 1L, 1L);
            }
            return;
        }

        String newFilename = getBaseName();
        IRealFile newFile;
        newFile = dest.getChild(newFilename);
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
            this.move(dest, newFilename, (position, length) ->
            {
                if (progressListener != null) {
                    progressListener.accept(this, position, length);
                }
            });
        } else if (this.isDirectory()) {
            if (progressListener != null)
                progressListener.accept(this, 0L, 1L);
            if ((newFile != null && newFile.exists() && autoRename != null && autoRenameFolders)
                    || newFile == null || !newFile.exists()) {
                newFile = move(dest, autoRename.apply(this));
                return;
            }
            if (progressListener != null)
                progressListener.accept(this, 1L, 1L);

            for (IRealFile child : this.listFiles()) {
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
     * @param progressListener
     * @param onFailed
     */
    default void deleteRecursively(TriConsumer<IRealFile, Long, Long> progressListener,
                                   BiConsumer<IRealFile, Exception> onFailed) {
        if (isFile()) {
            progressListener.accept(this, 0L, 1L);
            if (!this.delete()) {
                onFailed.accept(this, new Exception("Could not delete file"));
                return;
            }
            progressListener.accept(this, 1L, 1L);
        } else if (this.isDirectory()) {
            for (IRealFile child : this.listFiles()) {
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
    public static Function<IRealFile, String> autoRename = (IRealFile file) -> {
        return autoRename(file.getBaseName());
    };

    /**
     * Get an auto generated copy of a filename
     *
     * @param filename
     * @return
     */
    static String autoRename(String filename) {
        String ext = SalmonFileUtils.getExtensionFromFileName(filename);
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

