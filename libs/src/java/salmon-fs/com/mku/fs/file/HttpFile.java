package com.mku.fs.file;
/*
MIT License

Copyright (c) 2025 Max Kas

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
import com.mku.fs.stream.HttpFileStream;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java Http file implementation.
 */
public class HttpFile implements IRealFile {
    public static final String Separator = "/";
    private static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private String filePath;
	private CloseableHttpResponse response;
    public static CloseableHttpClient client = HttpClients.createDefault();

    /**
     * Instantiate a real file represented by the filepath provided (Remote read-write drive)
     *
     * @param path The filepath. This should be a relative path of the vault folder
     */
    public HttpFile(String path) {
        this.filePath = path;
    }

    private CloseableHttpResponse getResponse() throws Exception {
        URIBuilder uriBuilder = new URIBuilder(this.filePath);
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        setDefaultHeaders(httpGet);
        try {
            this.response = client.execute(httpGet);
            checkStatus(this.response, HttpStatus.SC_OK);
        } finally {
            if (this.response != null)
                this.response.close();
        }
        return this.response;
    }

    /**
     * Create a directory under this directory.
     *
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    public IRealFile createDirectory(String dirName) {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
    }

    /**
     * Create a file under this directory.
     *
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    public IRealFile createFile(String filename) throws IOException {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
    }

    /**
     * Delete this file or directory.
     *
     * @return True if deletion is successful.
     */
    public boolean delete() {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
    }

    /**
     * True if file or directory exists.
     *
     * @return True if exists.
     */
    public boolean exists() {
        try {
            return this.getResponse().getStatusLine().getStatusCode() == 200
                    || this.getResponse().getStatusLine().getStatusCode() == 206;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the absolute path on the physical disk. For java this is the same as the filepath.
     *
     * @return The absolute path.
     */
    public String getAbsolutePath() {
        return filePath;
    }

    /**
     * Get the name of this file or directory.
     *
     * @return The name of this file or directory.
     */
    public String getBaseName() {
        if (this.filePath == null)
            throw new RuntimeException("Filepath is not assigned");
        String nFilePath = this.filePath;
        if (nFilePath.endsWith("/"))
            nFilePath = nFilePath.substring(0, nFilePath.length() - 1);
        String[] parts = nFilePath.split(HttpFile.Separator);
        String basename = parts[parts.length - 1];
        if (basename == null)
            throw new RuntimeException("Could not get basename");
        if (basename.contains("%")) {
            basename = URLDecoder.decode(basename, StandardCharsets.UTF_8);
        }
        return basename;
    }

    /**
     * Get a stream for reading the file.
     *
     * @return The stream to read from.
     * @throws FileNotFoundException Thrown if file not found
     */
    public RandomAccessStream getInputStream() throws FileNotFoundException {
        return new HttpFileStream(this, "r");
    }

    /**
     * Get a stream for writing to this file.
     *
     * @return The stream to write to.
     * @throws FileNotFoundException Thrown if file not found
     */
    public RandomAccessStream getOutputStream() throws FileNotFoundException {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
    }

    /**
     * Get the parent directory of this file or directory.
     *
     * @return The parent directory.
     */
    public IRealFile getParent() {
        if (filePath.length() == 0 || filePath.equals("/"))
            return null;
        String path = filePath;
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        int index = path.lastIndexOf("/");
        if (index == -1)
            return null;
        HttpFile parent = new HttpFile(path.substring(0, index));
        return parent;
    }

    /**
     * Get the path of this file. For java this is the same as the absolute filepath.
     *
     * @return The path
     */
    public String getPath() {
        return filePath;
    }

    /**
     * True if this is a directory.
     *
     * @return True if it's a directory.
     */
    public boolean isDirectory() {
        HttpResponse res = null;
        try {
            res = this.getResponse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (res == null)
            throw new RuntimeException("Could not get response");
        String contentType = res.getFirstHeader("Content-Type").getValue();
        if (contentType == null)
            throw new RuntimeException("Could not get content type");
        return contentType.startsWith("text/html");
    }

    /**
     * True if this is a file.
     *
     * @return True if it's a file
     */
    public boolean isFile() {
        return !isDirectory();
    }

    /**
     * Get the last modified date on disk.
     *
     * @return The last modified date in milliseconds
     */
    public long lastModified() {
        String lastDateModified = null;
        try {
            lastDateModified = getResponse().getFirstHeader("last-modified").getValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (lastDateModified == null) {
            lastDateModified = "0";
        }
        Date date = null;
        try {
            date = new SimpleDateFormat(DATE_FORMAT).parse(lastDateModified);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        long lastModified = date.getTime() / 1000;
        return lastModified;
    }

    /**
     * Get the size of the file on disk.
     *
     * @return The length
     */
    public long length() {
        HttpResponse res = null;
        try {
            res = this.getResponse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (res == null)
            throw new RuntimeException("Could not get response");

        long length = 0;
        String lenStr = res.getFirstHeader("content-length").getValue();
        if (lenStr != null) {
            length = Integer.parseInt(lenStr);
        }
        return length;
    }

    /**
     * Get the count of files and subdirectories
     *
     * @return The children count
     */
    public int getChildrenCount() {
        return this.listFiles().length;
    }

    /**
     * List all files under this directory.
     *
     * @return The list of files.
     */
    public IRealFile[] listFiles() {
        List<IRealFile> files = new ArrayList<>();
        if (this.isDirectory()) {
            RandomAccessStream stream = null;
            MemoryStream ms = null;
            try {
                stream = this.getInputStream();
                ms = new MemoryStream();
                stream.copyTo(ms);
                String contents = new String(ms.toArray());

                Matcher matcher = Pattern.compile("(.+?)HREF=\"(.+?)\"(.+?)", Pattern.CASE_INSENSITIVE).matcher(contents);
                while (matcher.find()) {
                    String filename = matcher.group(2);
                    if (filename.contains(":") || filename.contains(".."))
                        continue;
                    if (filename.contains("%")) {
                        filename = URLDecoder.decode(filename, StandardCharsets.UTF_8);
                    }
                    IRealFile file = new HttpFile(this.filePath + HttpFile.Separator + filename);
                    files.add(file);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (ms != null)
                    ms.close();
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return files.toArray(new IRealFile[0]);
    }

    /**
     * Move this file or directory under a new directory.
     *
     * @param newDir The target directory.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public IRealFile move(IRealFile newDir) throws IOException {
        return move(newDir, null, null);
    }

    /**
     * Move this file or directory under a new directory.
     *
     * @param newDir  The target directory.
     * @param newName The new filename
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public IRealFile move(IRealFile newDir, String newName) throws IOException {
        return move(newDir, newName, null);
    }

    /**
     * Move this file or directory under a new directory.
     *
     * @param newDir           The target directory.
     * @param newName          The new filename
     * @param progressListener Observer to notify when progress changes.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public IRealFile move(IRealFile newDir, String newName, BiConsumer<Long, Long> progressListener) throws IOException {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
    }

    /**
     * Move this file or directory under a new directory.
     *
     * @param newDir The target directory.
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public IRealFile copy(IRealFile newDir) throws IOException {
        return copy(newDir, null, null);
    }

    /**
     * Move this file or directory under a new directory.
     *
     * @param newDir  The target directory.
     * @param newName New filename
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public IRealFile copy(IRealFile newDir, String newName) throws IOException {
        return copy(newDir, newName, null);
    }

    /**
     * Move this file or directory under a new directory.
     *
     * @param newDir           The target directory.
     * @param newName          New filename
     * @param progressListener Observer to notify when progress changes.
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public IRealFile copy(IRealFile newDir, String newName, BiConsumer<Long, Long> progressListener) throws IOException {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
    }

    /**
     * Get the file or directory under this directory with the provided name.
     *
     * @param filename The name of the file or directory.
     * @return The child
     */
    public IRealFile getChild(String filename) {
        if (isFile())
            return null;
		String nFilepath = this.getChildPath(filename);
        HttpFile child = new HttpFile(nFilepath);
        return child;
    }

    /**
     * Rename the current file or directory.
     *
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    public boolean renameTo(String newFilename) {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
    }

    /**
     * Create this directory under the current filepath.
     *
     * @return True if created.
     */
    public boolean mkdir() {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
    }
	
	/**
     * Reset cached properties
     *
     */
    public void reset() {
		this.response = null;
	}

    private String getChildPath(String filename) {
        String nFilepath = this.filePath;
        if(!nFilepath.endsWith(HttpFile.Separator))
            nFilepath += HttpFile.Separator;
        nFilepath += filename;
        return nFilepath;
    }
	
    /**
     * Returns a string representation of this object
     */
    @Override
    public String toString() {
        return filePath;
    }

    private void checkStatus(HttpResponse httpResponse, int status) throws IOException {
        if (httpResponse.getStatusLine().getStatusCode() != status)
            throw new IOException(httpResponse.getStatusLine().getStatusCode()
                    + " " + httpResponse.getStatusLine().getReasonPhrase());
    }

    private void setDefaultHeaders(HttpRequest request) {
        request.addHeader("Cache", "no-store");
        request.addHeader("Connection", "keep-alive");
    }
}