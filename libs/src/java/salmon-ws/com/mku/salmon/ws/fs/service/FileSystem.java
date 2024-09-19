package com.mku.salmon.ws.fs.service;
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

import com.mku.file.IRealFile;
import com.mku.file.JavaFile;
import com.mku.streams.RandomAccessStream;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility for file system operations
 */
public class FileSystem {
    private static final int BUFF_LENGTH = 32768;

    static void setPath(String path) {
        FileSystem.path = path;
    }

    private static String path;

    public static IRealFile getRoot() {
        IRealFile realRoot = new JavaFile(path);
        return realRoot;
    }

    public static IRealFile getFile(String path) {
        String[] parts = path.split("/");
        IRealFile file = getRoot();
        for (String part : parts) {
            if (part.length() == 0)
                continue;
            if (part.equals(".."))
                throw new RuntimeException("Backwards traversing (..) is not supported");
            file = file.getChild(part);
        }
        return file;
    }

    public static String getRelativePath(IRealFile file) {
        return new JavaFile(file.getPath()).getAbsolutePath().replace(
                new JavaFile(path).getAbsolutePath(), "").replace("\\", "/");
    }

    public static IRealFile write(String path, MultipartFile file, long position) throws IOException {
        IRealFile rFile = getFile(path);
        if(!rFile.exists()) {
            IRealFile dir = rFile.getParent();
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
