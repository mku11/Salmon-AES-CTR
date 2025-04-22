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

import com.mku.fs.streams.HttpFileStream;
import com.mku.salmon.encode.Base64Utils;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IFile implementation for a remote HTTP file.
 */
public class HttpFile implements IFile {
    /**
     * Directory separator
     */
    public static final String separator = "/";
    private static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private String filePath;
    private HttpFile.Response response;


	/**
	 * Get the credentials
	 * @return The credentials
	 */
    public Credentials getCredentials() {
        return credentials;
    }

    private Credentials credentials;

	/**
	 * Set the user credentials
	 * @param credentials The credentials
	 */
    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    /**
     * Instantiate a real file represented by the filepath provided (Remote read-write drive)
     *
     * @param path The filepath. This should be a relative path of the vault folder
     */
    public HttpFile(String path) {
        this(path, null);
    }
    /**
     * Instantiate a real file represented by the filepath provided (Remote read-write drive)
     *
     * @param path The filepath. This should be a relative path of the vault folder
	 * @param credentials The credentials (Basic Auth)
     */
    public HttpFile(String path, Credentials credentials) {
        this.filePath = path;
		this.credentials = credentials;
    }

    private synchronized HttpFile.Response getResponse() throws Exception {
        if(this.response == null) {
			HttpURLConnection conn = null;
            try {
                // TODO: should this be INFO method?
                conn = createConnection("GET", this.filePath);
                setDefaultHeaders(conn);
				setServiceAuth(conn);
                conn.connect();
                checkStatus(conn, HttpURLConnection.HTTP_OK);
				this.response = new Response(conn.getHeaderFields());
            } finally {
                closeConnection(conn);
            }
        }
        return this.response;
    }

    /**
     * Create a directory under this directory.
     *
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    public IFile createDirectory(String dirName) {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
    }

    /**
     * Create a file under this directory.
     *
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    public IFile createFile(String filename) throws IOException {
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
     * Check if file or directory exists.
     *
     * @return True if exists.
     */
    public boolean exists() {
        try {
            return this.getResponse() != null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the display path on the physical disk.
     *
     * @return The display path.
     */
    public String getDisplayPath() {
        return filePath;
    }

    /**
     * Get the name of this file or directory.
     *
     * @return The name of this file or directory.
     */
    public String getName() {
        if (this.filePath == null)
            throw new RuntimeException("Filepath is not assigned");
        String nFilePath = this.filePath;
        if (nFilePath.endsWith(HttpFile.separator))
            nFilePath = nFilePath.substring(0, nFilePath.length() - 1);
        String[] parts = nFilePath.split(HttpFile.separator);
        String basename = parts[parts.length - 1];
        if (basename == null)
            throw new RuntimeException("Could not get the file name");
        if (basename.contains("%")) {
			try {
				// do not use the charset variable for backwards compatibility with android
				basename = URLDecoder.decode(basename, StandardCharsets.UTF_8.name());
			} catch (Exception e) {
                throw new RuntimeException(e);
            } 
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
    public IFile getParent() {
        if (filePath.length() == 0 || filePath.equals(HttpFile.separator))
            return null;
        String path = filePath;
        if (path.endsWith(HttpFile.separator))
            path = path.substring(0, path.length() - 1);
        int index = path.lastIndexOf(HttpFile.separator);
        if (index == -1)
            return null;
        HttpFile parent = new HttpFile(path.substring(0, index), credentials);
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
        HttpFile.Response res = null;
        try {
            res = this.getResponse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (res == null)
            throw new RuntimeException("Could not get response");
        String contentType = res.getHeader("Content-Type");
        if (contentType == null)
            throw new RuntimeException("Could not get content type");
        return contentType.startsWith("text/html");
    }

    /**
     * Check if this is a file.
     *
     * @return True if it's a file
     */
    public boolean isFile() {
        return !isDirectory() && exists();
    }

    /**
     * Get the last modified date on disk.
     *
     * @return The last modified date in milliseconds
     */
    public long getLastDateModified() {
        String lastDateModified = null;
        try {
            lastDateModified = getResponse().getHeader("last-modified");
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
    public long getLength() {
        HttpFile.Response res;
        try {
            res = this.getResponse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (res == null)
            throw new RuntimeException("Could not get response");

        long length = 0;
        String lenStr = res.getHeader("content-length");
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
    public IFile[] listFiles() {
        List<IFile> files = new ArrayList<>();
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
						// do not use the charset variable for backwards compatibility with android
                        filename = URLDecoder.decode(filename, StandardCharsets.UTF_8.name());
                    }
                    IFile file = new HttpFile(this.filePath + HttpFile.separator + filename, credentials);
                    files.add(file);
                }
            } catch (Exception e) {
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
        return files.toArray(new IFile[0]);
    }

    /**
     * Move this file or directory under a new directory. Not supported.
     *
     * @param newDir  The target directory.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public IFile move(IFile newDir) throws IOException {
        return move(newDir, null);
    }

    /**
     * Move this file or directory under a new directory. Not supported.
     *
     * @param newDir  The target directory.
     * @param options The options
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public IFile move(IFile newDir, MoveOptions options) throws IOException {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
    }

    /**
     * Move this file or directory under a new directory. Not supported.
     *
     * @param newDir The target directory.
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public IFile copy(IFile newDir) throws IOException {
        return copy(newDir, null);
    }

    /**
     * Move this file or directory under a new directory. Not supported.
     *
     * @param newDir           The target directory.
     * @param options          The options
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public IFile copy(IFile newDir, CopyOptions options) throws IOException {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
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
        String nFilepath = this.getChildPath(filename);
        HttpFile child = new HttpFile(nFilepath, credentials);
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
     */
    public void reset() {
        this.response = null;
    }

    private String getChildPath(String filename) {
        String nFilepath = this.filePath;
        if (!nFilepath.endsWith(HttpFile.separator))
            nFilepath += HttpFile.separator;
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


    private HttpURLConnection createConnection(String method, String url) throws IOException {
        HttpURLConnection conn = HttpSyncClient.createConnection(url);
        conn.setUseCaches(false);
        conn.setDefaultUseCaches(false);
        conn.setRequestMethod(method);
        conn.setDoInput(true);
        return conn;
    }

    private void closeConnection(HttpURLConnection conn) {
        if (conn != null) {
            try {
                conn.disconnect();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void closeStream(Closeable stream) {
        if(stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

	private void setServiceAuth(HttpURLConnection conn) {
		if(credentials != null) {
			String encoding = Base64Utils.getBase64().encode((credentials.getServiceUser()
					+ ":" + credentials.getServicePassword()).getBytes());
			conn.setRequestProperty("Authorization", "Basic " + encoding);
		}
    }
	
    private void checkStatus(HttpURLConnection conn, int status) throws IOException {
        if (conn.getResponseCode() != status)
            throw new IOException(conn.getResponseCode()
                    + " " + conn.getResponseMessage());
    }

    private void setDefaultHeaders(HttpURLConnection conn) {
        conn.setRequestProperty("Cache", "no-store");
    }
	
    private static class Response {
        private Map<String, List<String>> headers;

        Response(Map<String, List<String>> headers) {
            this.headers = headers;
        }
		
		String getHeader(String name) {
			for(Map.Entry<String, List<String>> entry : headers.entrySet()){
				// status has a null key
				if(entry.getKey() == null)
					continue;
				if(entry.getKey().toLowerCase().equals(name.toLowerCase())) {
					if(entry.getValue().size() > 0) {
						return entry.getValue().get(0);
					} else {
						return null;
					}
				}
			}
			return null;
		}
    }
}