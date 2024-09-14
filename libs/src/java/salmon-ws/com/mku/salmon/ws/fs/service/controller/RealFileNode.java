package com.mku.salmon.ws.fs.service.controller;

import com.mku.file.IRealFile;
import com.mku.salmon.ws.fs.service.FileSystem;

import java.io.IOException;

/**
 * File system nodes exposed as resources.
 */
public class RealFileNode {
    /**
     * We wrap over the real file instead of choosing redundancy for better performance.
     */
    private transient IRealFile file;

    public RealFileNode(IRealFile file) {
        this.file = file;
    }

    public String getPath() throws IOException {
        return FileSystem.getRelativePath(file);
    }
    public boolean isPresent() {
        return file.exists();
    }
    public boolean isDirectory() throws IOException {
        return file.isDirectory();
    }

    public boolean isFile() throws IOException {
        return file.isFile();
    }

    public long getLength() {
        return file.length();
    }

    public long getLastModified() {
        return file.lastModified();
    }

    public String getName() {
        return file.getBaseName();
    }
}
