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
import com.mku11.salmon.streams.SalmonStream;

import java.nio.charset.Charset;
import java.util.HashMap;

public class SalmonDriveCache {
    protected static final String CACHE_FILE = "cc.dat";
    private final SalmonDrive drive;
    private final HashMap<String, String> filenames = new HashMap<>();

    public SalmonDriveCache(SalmonDrive drive) {
        this.drive = drive;
    }

    public void loadCache(IRealFile dir) throws Exception {
        IRealFile cache = getCache(dir);
        if(!cache.exists()) {
            saveCache(dir);
        }
        byte[] bytes = drive.getBytesFromRealFile(cache.getPath(), 0);
        String contents = new String(bytes, Charset.defaultCharset());
        String[] lines = contents.split("\n");
        filenames.clear();
        for (String line : lines) {
            int index = line.indexOf(" ");
            if(index == -1)
                continue;
            String key = line.substring(0, index);
            String val = line.substring(index + 1);
            filenames.put(key, val);
        }
    }

    public IRealFile getCache(IRealFile dir) {
        if (dir == null || !dir.exists())
            return null;
        IRealFile file = dir.getChild(CACHE_FILE);
        return file;
    }

    public void saveCache(IRealFile dir) throws Exception {
        IRealFile realFile = getCache(dir);
        if(realFile!=null && realFile.exists()) {
            boolean res = realFile.delete();
            if(!res) {
                System.err.println("Could not delete file");
            }
        }
        if(dir.getChild(CACHE_FILE) == null || !dir.getChild(CACHE_FILE).exists()) {
            dir.createFile(CACHE_FILE);
        }
        SalmonFile cache = new SalmonFile(realFile, drive);
        SalmonStream stream = cache.getOutputStream();
        StringBuilder sb = new StringBuilder();
        for (String key : filenames.keySet()) {
            sb.append(key);
            sb.append(" ");
            sb.append(filenames.get(key));
            sb.append("\n");
        }
        byte[] bytes = sb.toString().getBytes(Charset.defaultCharset());
        stream.write(bytes, 0, bytes.length);
        stream.flush();
        stream.close();
    }

    public String getString(String str) {
        if (filenames.containsKey(str))
            return filenames.get(str);
        return null;
    }

    public void addString(String str, String value) {
        filenames.put(str, value);
    }

}
