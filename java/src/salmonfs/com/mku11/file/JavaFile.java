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

import com.mku11.salmonfs.IRealFile;
import com.mku11.salmon.streams.AbsStream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Salmon RealFile implementation for Java
 */
public class JavaFile implements IRealFile {
    private String filePath;

    public JavaFile(String path) {
        this.filePath = path;
    }

    public IRealFile createDirectory(String dirName) {
        String nDirPath = filePath + File.separator + dirName;
        new File(nDirPath).mkdirs();
        JavaFile dotNetDir = new JavaFile(nDirPath);
        return dotNetDir;
    }

    public IRealFile createFile(String filename) throws IOException {
        String nFilePath = filePath + File.separator + filename;
        new File(nFilePath).createNewFile();
        JavaFile JavaFile = new JavaFile(nFilePath);
        return JavaFile;
    }

    public boolean delete() {
        if (isDirectory()) {
            IRealFile[] files = listFiles();
            for (IRealFile file : files) {
                file.delete();
            }
        }
        return new File(filePath).delete();
    }

    public boolean exists() {
        return new File(filePath).exists() || new File(filePath).exists();
    }

    public String getAbsolutePath() {
        return filePath;
    }

    public String getBaseName() {
        return new File(filePath).getName();
    }

    public AbsStream getInputStream() throws FileNotFoundException {
        return new JavaFileStream(this, "r");
    }

    public AbsStream getOutputStream() throws FileNotFoundException {
        return new JavaFileStream(this, "rw");
    }

    public IRealFile getParent() {
        String dirPath = new File(filePath).getParent();
        JavaFile parent = new JavaFile(dirPath);
        return parent;
    }

    public String getPath() {
        return filePath;
    }

    public boolean isDirectory() {
        return new File(filePath).isDirectory();
    }

    public boolean isFile() {
        return !isDirectory();
    }

    public long lastModified() {
        return new File(filePath).lastModified();
    }

    public long length() {
        return new File(filePath).length();
    }

    public IRealFile[] listFiles() {
        List<JavaFile> children = new ArrayList<>();
        File[] files = new File(filePath).listFiles();
        if (files != null) {
            for (File file : files) {
                children.add(new JavaFile(file.getPath()));
            }
        }
        return children.toArray(new JavaFile[0]);
    }

    public IRealFile move(IRealFile newDir, AbsStream.OnProgressListener progressListener) {
        String nFilePath = newDir.getPath() + File.separator + getBaseName();
        boolean res = new File(filePath).renameTo(new File(nFilePath));
        return new JavaFile(nFilePath);
    }

    @Override
    public IRealFile copy(IRealFile newDir, AbsStream.OnProgressListener progressListener) throws Exception {
        if (this.isDirectory()) {
            IRealFile dir = newDir.createDirectory(this.getBaseName());
            for (IRealFile ifile : listFiles()) {
                ifile.copy(dir, progressListener);
            }
            return dir;
        } else {
            IRealFile newFile = newDir.createFile(getBaseName());
            AbsStream source = getInputStream();
            AbsStream target = newFile.getOutputStream();
            try {
                source.copyTo(target, progressListener);
            } catch (Exception ex) {
                newFile.delete();
                return null;
            } finally {
                source.close();
                target.close();
            }
            return newFile;
        }
    }

    public IRealFile getChild(String filename) {
        if (isFile())
            return null;
        JavaFile child = new JavaFile(filePath + File.separator + filename);
        return child;
    }

    public boolean renameTo(String newFilename) {
        File file = new File(filePath);
        File nfile = new File(file.getParent(), newFilename);
        boolean res = file.renameTo(nfile);
        filePath = nfile.getPath();
        return res;
    }

    public boolean mkdir() {
        new File(filePath).mkdir();
        return true;
    }
}
