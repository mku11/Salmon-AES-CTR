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
    long getLength();

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
    long getLastDateModified();

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
     * @param options The options
     * @return The file after the move. Use this instance for any subsequent file operations.
     * @throws IOException Thrown if there is an IO error.
     */
    IFile move(IFile newDir, MoveOptions options) throws IOException;

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
     * @param options The options
     * @return The file after the copy. Use this instance for any subsequent file operations.
     * @throws IOException Thrown if there is an IO error.
     */
    IFile copy(IFile newDir, CopyOptions options) throws IOException;

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
     * @param src  The source directory
     * @param dest The target directory
     * @return True if contents were copied
     * @throws IOException Thrown if there is an IO error.
     */
    static boolean copyFileContents(IFile src, IFile dest) throws IOException {
        return copyFileContents(src, dest, null);
    }

    /**
     * Copy contents of a file to another file.
     *
     * @param src     The source directory
     * @param dest    The target directory
     * @param options The options
     * @return True if contents were copied
     * @throws IOException Thrown if there is an IO error.
     */
    static boolean copyFileContents(IFile src, IFile dest, CopyContentsOptions options)
            throws IOException {

        RandomAccessStream source = src.getInputStream();
        RandomAccessStream target = dest.getOutputStream();
        try {
            source.copyTo(target, options != null ? options.onProgressChanged : null);
        } catch (Exception ex) {
            dest.delete();
            return false;
        } finally {
            source.close();
            target.close();
        }
        return true;
    }

    /**
     * Copy a directory recursively
     *
     * @param destDir The destination directory
     * @throws IOException Thrown if there is an IO error.
     */
    default void copyRecursively(IFile destDir) throws IOException {
        copyRecursively(destDir, null);
    }

    /**
     * Copy a directory recursively
     *
     * @param destDir The destination directory
     * @param options The options
     * @throws IOException Thrown if there is an IO error.
     */
    default void copyRecursively(IFile destDir, RecursiveCopyOptions options) throws IOException {
        if (options == null)
            options = new RecursiveCopyOptions();
        String newFilename = getName();
        IFile newFile;
        newFile = destDir.getChild(newFilename);
        if (isFile()) {
            if (newFile != null && newFile.exists()) {
                if (options.autoRename != null) {
                    newFilename = options.autoRename.apply(this);
                } else {
                    if (options.onFailed != null)
                        options.onFailed.accept(this, new Exception("Another file exists"));
                    return;
                }
            }
            RecursiveCopyOptions finalOptions = options;
            CopyOptions copyOptions = new CopyOptions();
            copyOptions.newFilename = newFilename;
            copyOptions.onProgressChanged = (position, length) ->
            {
                if (finalOptions.onProgressChanged != null) {
                    finalOptions.onProgressChanged.accept(this, position, length);
                }
            };
            this.copy(destDir, copyOptions);
        } else if (this.isDirectory()) {
            if (options.onProgressChanged != null)
                options.onProgressChanged.accept(this, 0L, 1L);
            if (destDir.getDisplayPath().startsWith(this.getDisplayPath())) {
                if (options.onProgressChanged != null)
                    options.onProgressChanged.accept(this, 1L, 1L);
                return;
            }
            if (newFile != null && newFile.exists() && options.autoRename != null && options.autoRenameFolders)
                newFile = destDir.createDirectory(options.autoRename.apply(this));
            else if (newFile == null || !newFile.exists())
                newFile = destDir.createDirectory(newFilename);
            if (options.onProgressChanged != null)
                options.onProgressChanged.accept(this, 1L, 1L);

            for (IFile child : this.listFiles()) {
                child.copyRecursively(newFile, options);
            }
        }
    }

    /**
     * Move a directory recursively
     *
     * @param destDir The target directory
     * @throws IOException Thrown if there is an IO error.
     */
    default void moveRecursively(IFile destDir) throws IOException {
        moveRecursively(destDir, null);
    }

    /**
     * Move a directory recursively
     *
     * @param destDir The target directory
     * @param options The options
     * @throws IOException Thrown if there is an IO error.
     */
    default void moveRecursively(IFile destDir, RecursiveMoveOptions options) throws IOException {
        if (options == null)
            options = new RecursiveMoveOptions();
        // target directory is the same
        if (getParent().getPath().equals(destDir.getPath())) {
            if (options.onProgressChanged != null) {
                options.onProgressChanged.accept(this, 0L, 1L);
                options.onProgressChanged.accept(this, 1L, 1L);
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
                if (options.autoRename != null) {
                    newFilename = options.autoRename.apply(this);
                } else {
                    if (options.onFailed != null)
                        options.onFailed.accept(this, new Exception("Another file exists"));
                    return;
                }
            }
            RecursiveMoveOptions finalOptions = options;
            MoveOptions moveOptions = new MoveOptions();
            moveOptions.newFilename = newFilename;
            moveOptions.onProgressChanged = (position, length) ->
            {
                if (finalOptions.onProgressChanged != null) {
                    finalOptions.onProgressChanged.accept(this, position, length);
                }
            };
            this.move(destDir, moveOptions);
        } else if (this.isDirectory()) {
            if (options.onProgressChanged != null)
                options.onProgressChanged.accept(this, 0L, 1L);
            if (destDir.getDisplayPath().startsWith(this.getDisplayPath())) {
                if (options.onProgressChanged != null)
                    options.onProgressChanged.accept(this, 1L, 1L);
                return;
            }
            if ((newFile != null && newFile.exists() && options.autoRename != null && options.autoRenameFolders)
                    || newFile == null || !newFile.exists()) {
                MoveOptions moveOptions = new MoveOptions();
                moveOptions.newFilename = options.autoRename.apply(this);
                newFile = move(destDir, moveOptions);
                return;
            }
            if (options.onProgressChanged != null)
                options.onProgressChanged.accept(this, 1L, 1L);

            for (IFile child : this.listFiles()) {
                child.moveRecursively(newFile, options);
            }
            if (!this.delete()) {
                options.onFailed.accept(this, new Exception("Could not delete source directory"));
            }
        }
    }


    /**
     * Delete a directory recursively
     */
    default void deleteRecursively() {
        deleteRecursively(null);
    }

    /**
     * Delete a directory recursively
     *
     * @param options The options
     */
    default void deleteRecursively(RecursiveDeleteOptions options) {
        if (options == null)
            options = new RecursiveDeleteOptions();
        if (isFile()) {
			if(options.onProgressChanged != null)
				options.onProgressChanged.accept(this, 0L, 1L);
            if (!this.delete()) {
				if(options.onFailed != null)
					options.onFailed.accept(this, new Exception("Could not delete file"));
            }
			if(options.onProgressChanged != null)
				options.onProgressChanged.accept(this, 1L, 1L);
        } else if (this.isDirectory()) {
            for (IFile child : this.listFiles()) {
                child.deleteRecursively(options);
            }
            if (!this.delete()) {
				if(options.onFailed != null)
					options.onFailed.accept(this, new Exception("Could not delete directory"));
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


    /**
     * File copy options
     */
    class CopyOptions {
        /**
         * Override filename
         */
        public String newFilename;

        /**
         * Callback where progress changed
         */
        public BiConsumer<Long, Long> onProgressChanged;
    }

    /**
     * File move options
     */
    class MoveOptions {
        /**
         * Override filename
         */
        public String newFilename;

        /**
         * Callback where progress changed
         */
        public BiConsumer<Long, Long> onProgressChanged;
    }

    /**
     * Directory copy options (recursively)
     */
    class RecursiveCopyOptions {
        /**
         * Callback when file with same name exists
         */
        public Function<IFile, String> autoRename;

        /**
         * True to autorename folders
         */
        public boolean autoRenameFolders = false;

        /**
         * Callback when file changes
         */
        public BiConsumer<IFile, Exception> onFailed;

        /**
         * Callback where progress changed
         */
        public TriConsumer<IFile, Long, Long> onProgressChanged;
    }

    /**
     * Directory move options (recursively)
     */
    public class RecursiveMoveOptions {
        /**
         * Callback when file with the same name exists
         */
        public Function<IFile, String> autoRename;

        /**
         * True to autorename folders
         */
        public boolean autoRenameFolders = false;

        /**
         * Callback when file failed
         */
        public BiConsumer<IFile, Exception> onFailed;

        /**
         * Callback when progress changes
         */
        public TriConsumer<IFile, Long, Long> onProgressChanged;
    }

    /**
     * Directory move options (recursively)
     */
    public class RecursiveDeleteOptions {
        /**
         * Callback when file failed
         */
        public BiConsumer<IFile, Exception> onFailed;

        /**
         * Callback when progress changed
         */
        public TriConsumer<IFile, Long, Long> onProgressChanged;
    }

    /**
     * Directory move options (recursively)
     */
    public class CopyContentsOptions {
		/**
         * Callback when progress changed
         */
        public BiConsumer<Long, Long> onProgressChanged;
    }
}

