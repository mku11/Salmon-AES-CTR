package com.mku.salmon.ws.fs.service.controller;
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

import com.mku.fs.file.File;
import com.mku.fs.file.IFile;
import com.mku.streams.RandomAccessStream;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility for file system operations
 */
public class FileSystem {
    private static final int BUFF_LENGTH = 32768;

    private String path;

    private static FileSystem instance;

    public static FileSystem getInstance() {
        if(instance == null)
            instance = new FileSystem();
        return instance;
    }


    public void setPath(String path) {
        this.path = path;
    }

    public IFile getRoot() {
        IFile realRoot = new File(path);
        return realRoot;
    }

    public IFile getFile(String path) {
        String[] parts = path.split("/");
        IFile file = getRoot();
        for (String part : parts) {
            if (part.length() == 0)
                continue;
            if (part.equals(".."))
                throw new RuntimeException("Backwards traversing (..) is not supported");
            file = file.getChild(part);
			if(file == null)
				return null;
        }
        return file;
    }

    public String getRelativePath(IFile file) {
        return new File(file.getPath()).getPath().replace(
                new File(path).getPath(), "").replace("\\", "/");
    }

    public IFile write(String path, MultipartFile file, long position) throws IOException {
        IFile rFile = getFile(path);
        if(!rFile.exists()) {
            IFile dir = rFile.getParent();
            rFile = dir.createFile(file.getOriginalFilename());
        }
        InputStream inputStream = null;
        RandomAccessStream outputStream = null;
        try {
            inputStream = file.getInputStream();
            outputStream = rFile.getOutputStream();
            outputStream.setPosition(position);

            byte[] buff = new byte[BUFF_LENGTH];
            int bytesRead;
            while ((bytesRead = inputStream.read(buff, 0, buff.length)) > 0) {
                outputStream.write(buff, 0, bytesRead);
            }
        } catch (Exception ex) {
            throw ex;
        } finally {
            if (inputStream != null)
                inputStream.close();
            if (outputStream != null)
                outputStream.close();
        }
        return rFile;
    }
}
