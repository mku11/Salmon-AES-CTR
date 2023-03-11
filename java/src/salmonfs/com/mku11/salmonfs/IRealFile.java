package com.mku11.salmonfs;
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

import com.mku11.salmon.streams.AbsStream;

import java.io.FileNotFoundException;
import java.io.IOException;

public interface IRealFile {
    boolean exists();

    boolean delete();

    AbsStream getInputStream() throws FileNotFoundException;

    AbsStream getOutputStream() throws FileNotFoundException;

    boolean renameTo(String newFilename) throws FileNotFoundException;

    long length();

    long lastModified();

    String getAbsolutePath();

    String getPath();

    boolean isFile();

    boolean isDirectory();

    IRealFile[] listFiles();

    String getBaseName();

    IRealFile createDirectory(String dataDirName);

    IRealFile getParent();

    IRealFile createFile(String filename) throws IOException;

    IRealFile move(IRealFile newDir, AbsStream.OnProgressListener progressListener) throws Exception;

    IRealFile copy(IRealFile newDir, AbsStream.OnProgressListener progressListener) throws Exception;

    IRealFile getChild(String filename);

    boolean mkdir();
}

