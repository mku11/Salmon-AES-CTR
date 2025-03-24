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

import com.mku.fs.stream.FileStream;
import com.mku.streams.RandomAccessStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * IFile implementation for a local file.
 */
public class File implements IFile {
	/**
	 * Directory Separator
	 */
	public static final String separator = java.io.File.separator;
    private String filePath;

    /**
     * Instantiate a real file represented by the filepath provided.
     *
     * @param path The filepath.
     */
    public File(String path) {
        this.filePath = path;
    }

    /**
     * Create a directory under this directory.
     *
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    public IFile createDirectory(String dirName) {
        String nDirPath = filePath + File.separator + dirName;
        java.io.File file = new java.io.File(nDirPath);
        file.mkdirs();
        File dotNetDir = new File(nDirPath);
        return dotNetDir;
    }

    /**
     * Create a file under this directory.
     *
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    public IFile createFile(String filename) throws IOException {
        String nFilePath = filePath + File.separator + filename;
        new java.io.File(nFilePath).createNewFile();
        File File = new File(nFilePath);
        return File;
    }

    /**
     * Delete this file or directory.
     *
     * @return True if deletion is successful.
     */
    public boolean delete() {
        if (isDirectory()) {
            IFile[] files = listFiles();
            for (IFile file : files) {
                file.delete();
            }
        }
        return new java.io.File(filePath).delete();
    }

    /**
     * Check if file or directory exists.
     *
     * @return True if exists.
     */
    public boolean exists() {
        return new java.io.File(filePath).exists();
    }

    /**
     * Get the display path on the physical disk.
     *
     * @return The display path.
     */
    public String getDisplayPath() {
        return new java.io.File(filePath).getAbsolutePath();
    }

    /**
     * Get the name of this file or directory.
     *
     * @return The name of this file or directory.
     */
    public String getName() {
        return new java.io.File(filePath).getName();
    }

    /**
     * Get a stream for reading the file.
     *
     * @return The stream to read from.
     * @throws FileNotFoundException Thrown if file not found
     */
    public RandomAccessStream getInputStream() throws FileNotFoundException {
        return new FileStream(this, "r");
    }

    /**
     * Get a stream for writing to this file.
     *
     * @return The stream to write to.
     * @throws FileNotFoundException Thrown if file not found
     */
    public RandomAccessStream getOutputStream() throws FileNotFoundException {
        return new FileStream(this, "rw");
    }

    /**
     * Get the parent directory of this file or directory.
     *
     * @return The parent directory.
     */
    public IFile getParent() {
        String dirPath = new java.io.File(filePath).getParent();
        File parent = new File(dirPath);
        return parent;
    }

    /**
     * Get the path of this file.
     *
     * @return The path
     */
    public String getPath() {
        return filePath;
    }

    /**
     * Check if this is a directory.
     *
     * @return True if it's a directory.
     */
    public boolean isDirectory() {
        return new java.io.File(filePath).isDirectory();
    }

    /**
     * Check if this is a file.
     *
     * @return True if it's a file
     */
    public boolean isFile() {
        return new java.io.File(filePath).isFile();
    }

    /**
     * Get the last modified date on disk.
     *
     * @return The last modified date in milliseconds
     */
    public long getLastDateModified() {
        return new java.io.File(filePath).lastModified();
    }

    /**
     * Get the size of the file on disk.
     *
     * @return The length
     */
    public long getLength() {
        return new java.io.File(filePath).length();
    }

    /**
     * Get the count of files and subdirectories
     *
     * @return The children count
     */
    public int getChildrenCount() {
        return isDirectory() ? new java.io.File(filePath).listFiles().length : 0;
    }

    /**
     * List all files under this directory.
     *
     * @return The list of files.
     */
    public IFile[] listFiles() {
        java.io.File[] files = new java.io.File(filePath).listFiles();
        if (files == null)
            return new File[0];

        List<File> realFiles = new ArrayList<>();
        List<File> realDirs = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            File file = new File(files[i].getPath());
            if (file.isDirectory())
                realDirs.add(file);
            else
                realFiles.add(file);
        }
        realDirs.addAll(realFiles);
        return realDirs.toArray(new File[0]);
    }

    /**
     * Move this file or directory under a new directory.
     *
     * @param newDir The target directory.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public IFile move(IFile newDir) {
        return move(newDir, null);
    }

    /**
     * Move this file or directory under a new directory.
     *
     * @param newDir  The target directory.
     * @param options The options
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public IFile move(IFile newDir, MoveOptions options) {
        if (options == null)
            options = new MoveOptions();
        String newName = options.newFilename != null ? options.newFilename : getName();
        if (newDir == null || !newDir.exists())
            throw new RuntimeException("Target directory does not exist");
        IFile newFile = newDir.getChild(newName);
        if (newFile != null && newFile.exists())
            throw new RuntimeException("Another file/directory already exists");
        java.io.File nFile = new java.io.File(newFile.getDisplayPath());
        if (options.onProgressChanged != null)
            options.onProgressChanged.accept(0L, this.getLength());
        boolean res = new java.io.File(filePath).renameTo(nFile);
        if (!res)
            throw new RuntimeException("Could not move file/directory");
		newFile = new File(nFile.getPath());
		if (options.onProgressChanged != null)
            options.onProgressChanged.accept(newFile.getLength(), newFile.getLength());
		return newFile;
    }


    /**
     * Move this file or directory under a new directory.
     *
     * @param newDir  The target directory.
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public IFile copy(IFile newDir) throws IOException {
        return copy(newDir, null);
    }

    /**
     * Move this file or directory under a new directory.
     *
     * @param newDir  The target directory.
     * @param options The options
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public IFile copy(IFile newDir, CopyOptions options) throws IOException {
        if (options == null)
            options = new CopyOptions();
        String newName = options.newFilename != null ? options.newFilename : getName();
        if (newDir == null || !newDir.exists())
            throw new IOException("Target directory does not exists");
        IFile newFile = newDir.getChild(newName);
        if (newFile != null && newFile.exists())
            throw new IOException("Another file/directory already exists");
        if (isDirectory()) {
            throw new IOException("Could not copy directory use IFile copyRecursively() instead");
        } else {
            newFile = newDir.createFile(newName);
            CopyContentsOptions copyContentOptions = new CopyContentsOptions();
            copyContentOptions.onProgressChanged = options.onProgressChanged;
            boolean res = IFile.copyFileContents(this, newFile, copyContentOptions);
            return res ? newFile : null;
        }
    }

    /**
     * Get the file or directory under this directory with the provided name.
     *
     * @param filename The name of the file or directory.
     * @return The child
     */
    public IFile getChild(String filename) {
        if (isFile())
            return null;
        File child = new File(filePath + File.separator + filename);
        return child;
    }

    /**
     * Rename the current file or directory.
     *
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    public boolean renameTo(String newFilename) {
        java.io.File file = new java.io.File(filePath);
        java.io.File nfile = new java.io.File(file.getParent(), newFilename);
        boolean res = file.renameTo(nfile);
        filePath = nfile.getPath();
        return res;
    }

    /**
     * Create this directory under the current filepath.
     *
     * @return True if created.
     */
    public boolean mkdir() {
        java.io.File file = new java.io.File(filePath);
        return file.mkdir();
    }

    /**
     * Reset cached properties
     */
    public void reset() {

    }

    /**
     * Returns a string representation of this object
     */
    @Override
    public String toString() {
        return filePath;
    }
}
