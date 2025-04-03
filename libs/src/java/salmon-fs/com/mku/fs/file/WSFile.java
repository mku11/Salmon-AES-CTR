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

import com.mku.convert.Base64;
import com.mku.fs.streams.WSFileStream;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IFile implementation for a remote web service file.
 */
public class WSFile implements IFile {
    /**
     * Directory Separator
     */
    public static final String separator = "/";

    private static final String PATH = "path";
    private static final String DEST_DIR = "destDir";
    private static final String FILENAME = "filename";

    private String filePath;
    private Response response;
    private String servicePath;

    public String getServicePath() {
        return servicePath;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    /**
     * Salmon Web service credentials
     */
    private Credentials credentials;

    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    /**
     * Instantiate a real file represented by the filepath provided (Remote read-write drive)
     *
     * @param path        The filepath. This should be a relative path of the vault folder
     * @param servicePath The REST API server path
     * @param credentials The REST API credentials
     */
    public WSFile(String path, String servicePath, Credentials credentials) {
        if (!path.startsWith("/"))
            path = WSFile.separator + path;
        this.servicePath = servicePath;
        this.filePath = path;
        this.credentials = credentials;
    }

    private Response getResponse() throws Exception {
        if (this.response == null) {
            HttpURLConnection conn = null;
            try {
                HashMap<String, String> params = new HashMap<>();
                params.put(PATH, this.filePath);
                conn = createConnection("GET", this.servicePath + "/api/info", params);
                setDefaultHeaders(conn);
                setServiceAuth(conn);
                conn.connect();
                checkStatus(conn, HttpURLConnection.HTTP_OK);
                this.response = new Response(new String(readAllBytes(conn.getInputStream())),
                        conn.getHeaderFields());
            } finally {
                closeConnection(conn);
            }
        }
        return response;
    }

    /**
     * Create a directory under this directory.
     *
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    public IFile createDirectory(String dirName) {
        String nDirPath = this.getChildPath(dirName);
        HttpURLConnection conn = null;
        OutputStream outputStream = null;
        try {
            conn = createConnection("POST", this.servicePath + "/api/mkdir");
            HashMap<String, String> params = new HashMap<>();
            params.put(PATH, nDirPath);

            setDefaultHeaders(conn);
            setServiceAuth(conn);

            conn.connect();
            outputStream = conn.getOutputStream();
            addParameters(outputStream, params);

            checkStatus(conn, HttpURLConnection.HTTP_OK);
            WSFile dir = new WSFile(nDirPath, servicePath, credentials);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            closeConnection(conn);
            closeStream(outputStream);
        }
    }

    /**
     * Create a file under this directory.
     *
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    public IFile createFile(String filename) throws IOException {
        String nFilePath = this.getChildPath(filename);
        HttpURLConnection conn = null;
        OutputStream outputStream = null;
        try {
            conn = createConnection("POST", this.servicePath + "/api/create");
            HashMap<String, String> params = new HashMap<>();
            params.put(PATH, nFilePath);
            setDefaultHeaders(conn);
            setServiceAuth(conn);
            conn.connect();
            outputStream = conn.getOutputStream();
            addParameters(outputStream, params);
            checkStatus(conn, HttpURLConnection.HTTP_OK);
            WSFile nFile = new WSFile(nFilePath, servicePath, credentials);
            return nFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            closeConnection(conn);
            closeStream(outputStream);
        }
    }

    /**
     * Delete this file or directory.
     *
     * @return True if deletion is successful.
     */
    public boolean delete() {
        this.reset();
        if (isDirectory()) {
            IFile[] files = listFiles();
            for (IFile file : files) {
                HttpURLConnection conn = null;
                try {
                    HashMap<String, String> params = new HashMap<>();
                    params.put(PATH, file.getPath());
                    conn = createConnection("DELETE", this.servicePath + "/api/delete", params);
                    setDefaultHeaders(conn);
                    setServiceAuth(conn);
                    conn.connect();
                    checkStatus(conn, HttpURLConnection.HTTP_OK);
                    conn.disconnect();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    closeConnection(conn);
                }
            }
        }

        HttpURLConnection conn = null;
        try {
            HashMap<String, String> params = new HashMap<>();
            params.put(PATH, this.filePath);
            conn = createConnection("DELETE", this.servicePath + "/api/delete", params);
            setDefaultHeaders(conn);
            setServiceAuth(conn);
            conn.connect();
            checkStatus(conn, HttpURLConnection.HTTP_OK);
            this.reset();
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            closeConnection(conn);
        }
    }

    /**
     * Check if file or directory exists.
     *
     * @return True if exists.
     */
    public boolean exists() {
        try {
            return getResponse().exists;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the display path on the physical disk. For java this is the same as the filepath.
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
        try {
            return getResponse().name;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get a stream for reading the file.
     *
     * @return The stream to read from.
     * @throws FileNotFoundException Thrown if file not found
     */
    public RandomAccessStream getInputStream() throws FileNotFoundException {
        this.reset();
        return new WSFileStream(this, "r");
    }

    /**
     * Get a stream for writing to this file.
     *
     * @return The stream to write to.
     * @throws FileNotFoundException Thrown if file not found
     */
    public RandomAccessStream getOutputStream() throws FileNotFoundException {
        this.reset();
        return new WSFileStream(this, "rw");
    }

    /**
     * Get the parent directory of this file or directory.
     *
     * @return The parent directory.
     */
    public IFile getParent() {
        if (filePath.length() == 0 || filePath.equals(WSFile.separator))
            return null;
        String path = filePath;
        if (path.endsWith(WSFile.separator))
            path = path.substring(0, path.length() - 1);
        int index = path.lastIndexOf(WSFile.separator);
        if (index == -1)
            return null;
        WSFile parent = new WSFile(path.substring(0, index), servicePath, credentials);
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
        try {
            return getResponse().isDirectory;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check if this is a file.
     *
     * @return True if it's a file
     */
    public boolean isFile() {
        try {
            return getResponse().isFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the last modified date on disk.
     *
     * @return The last modified date in milliseconds
     */
    public long getLastDateModified() {
        try {
            return getResponse().lastModified;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the size of the file on disk.
     *
     * @return The length
     */
    public long getLength() {
        try {
            return getResponse().length;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the count of files and subdirectories
     *
     * @return The children count
     */
    public int getChildrenCount() {
        HttpURLConnection conn = null;
        try {
            if (getResponse().isDirectory) {
                HashMap<String, String> params = new HashMap<>();
                params.put(PATH, this.filePath);
                conn = createConnection("GET", this.servicePath + "/api/list", params);
                setDefaultHeaders(conn);
                setServiceAuth(conn);
                conn.connect();
                checkStatus(conn, HttpURLConnection.HTTP_OK);
                int res = getFileListCount(conn.getInputStream());
                return res;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            closeConnection(conn);
        }
        return 0;
    }

    private int getFileListCount(InputStream contentInputStream) throws IOException {
        String content = new String(readAllBytes(contentInputStream));
        JSONArray object = new JSONArray(content);
        return object.length();
    }

    private List<Response> parseFileList(InputStream contentInputStream) throws IOException {
        String content = new String(readAllBytes(contentInputStream));
        JSONArray object = new JSONArray(content);
        List<Response> list = new ArrayList<>();
        for (int i = 0; i < object.length(); i++) {
            JSONObject jsonObject = (JSONObject) object.get(i);
            Response response = new Response(jsonObject.toString(), null);
            list.add(response);
        }
        return list;
    }

    /**
     * List all files under this directory.
     *
     * @return The list of files.
     */
    public IFile[] listFiles() {
        Response[] files = null;
        HttpURLConnection conn = null;
        try {
            if (getResponse().isDirectory) {
                HashMap<String, String> params = new HashMap<>();
                params.put(PATH, this.filePath);
                conn = createConnection("GET", this.servicePath + "/api/list", params);
                setDefaultHeaders(conn);
                setServiceAuth(conn);
                conn.connect();
                checkStatus(conn, HttpURLConnection.HTTP_OK);
                files = parseFileList(conn.getInputStream()).toArray(new Response[0]);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            closeConnection(conn);
        }
        if (files == null)
            return new WSFile[0];

        List<WSFile> realFiles = new ArrayList<>();
        List<WSFile> realDirs = new ArrayList<>();
        for (Response resFile : files) {
            WSFile file = new WSFile(resFile.path, servicePath, credentials);
            if (resFile.isDirectory)
                realDirs.add(file);
            else
                realFiles.add(file);
        }
        realDirs.addAll(realFiles);
        return realDirs.toArray(new WSFile[0]);
    }

    /**
     * Move this file or directory under a new directory.
     *
     * @param newDir The target directory.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public IFile move(IFile newDir) throws IOException {
        return move(newDir, null);
    }

    /**
     * Move this file or directory under a new directory.
     *
     * @param newDir  The target directory.
     * @param options The options
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public IFile move(IFile newDir, MoveOptions options) throws IOException {
        if (options == null)
            options = new MoveOptions();
        String newName = options.newFilename != null ? options.newFilename : getName();
        if (newDir == null || !newDir.exists())
            throw new IOException("Target directory does not exist");
        IFile newFile = newDir.getChild(newName);
        if (newFile != null && newFile.exists())
            throw new IOException("Another file/directory already exists");

        if (isDirectory()) {
            throw new IOException("Could not move directory use IFile moveRecursively() instead");
        } else {
            HttpURLConnection conn = null;
            try {
                HashMap<String, String> params = new HashMap<>();
                params.put(PATH, this.filePath);
                params.put(DEST_DIR, newDir.getPath());
                params.put(FILENAME, newName);
                conn = createConnection("PUT", this.servicePath + "/api/move", params);
                setDefaultHeaders(conn);
                setServiceAuth(conn);
                conn.connect();
                checkStatus(conn, HttpURLConnection.HTTP_OK);
                Response response = new Response(new String(readAllBytes(conn.getInputStream())),
                        conn.getHeaderFields());
                newFile = new WSFile(response.path, servicePath, credentials);
                this.reset();
                return newFile;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                closeConnection(conn);
            }
        }
    }

    /**
     * Copy this file or directory under a new directory.
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
     * Copy this file or directory under a new directory.
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
            HttpURLConnection conn = null;
            OutputStream outputStream = null;
            try {
                conn = createConnection("POST", this.servicePath + "/api/copy");
                HashMap<String, String> params = new HashMap<>();
                params.put(PATH, this.filePath);
                params.put(DEST_DIR, newDir.getPath());
                params.put(FILENAME, newName);
                setDefaultHeaders(conn);
                setServiceAuth(conn);
                conn.connect();
                outputStream = conn.getOutputStream();
                addParameters(outputStream, params);
                checkStatus(conn, HttpURLConnection.HTTP_OK);
                Response response = new Response(new String(readAllBytes(conn.getInputStream())),
                        conn.getHeaderFields());
                newFile = new WSFile(response.path, this.servicePath, credentials);
                this.reset();
                return newFile;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                closeStream(outputStream);
                closeConnection(conn);
            }
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
        String nFilepath = this.getChildPath(filename);
        WSFile child = new WSFile(nFilepath, servicePath, credentials);
        return child;
    }

    /**
     * Rename the current file or directory.
     *
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    public boolean renameTo(String newFilename) {
        HttpURLConnection conn = null;
        try {
            this.reset();
            HashMap<String, String> params = new HashMap<>();
            params.put(PATH, this.filePath);
            params.put(FILENAME, newFilename);
            conn = createConnection("PUT", this.servicePath + "/api/rename", params);
            setDefaultHeaders(conn);
            setServiceAuth(conn);
            conn.connect();
            checkStatus(conn, HttpURLConnection.HTTP_OK);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeConnection(conn);
        }
        return false;
    }

    /**
     * Create this directory under the current filepath.
     *
     * @return True if created.
     */
    public boolean mkdir() {
        HttpURLConnection conn = null;
        try {
            this.reset();
            HashMap<String, String> params = new HashMap<>();
            params.put(PATH, this.filePath);
            conn = createConnection("PUT", this.servicePath + "/api/mkdir", params);
            setDefaultHeaders(conn);
            setServiceAuth(conn);
            conn.connect();
            checkStatus(conn, HttpURLConnection.HTTP_OK);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeConnection(conn);
        }
        return false;
    }

    /**
     * Reset cached properties
     */
    public void reset() {
        this.response = null;
    }

    private String getChildPath(String filename) {
        String nFilepath = this.filePath;
        if (!nFilepath.endsWith(WSFile.separator))
            nFilepath += WSFile.separator;
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

    private byte[] readAllBytes(InputStream stream) throws IOException {
        MemoryStream ms = new MemoryStream();
        byte[] buffer = new byte[32768];
        int bytesRead;
        try {
            while ((bytesRead = stream.read(buffer, 0, buffer.length)) > 0) {
                ms.write(buffer, 0, bytesRead);
            }
        } finally {
            ms.close();
            stream.close();
        }
        return ms.toArray();
    }

    private HttpURLConnection createConnection(String method, String url) throws IOException {
        return createConnection(method, url, null);
    }

    private HttpURLConnection createConnection(String method, String url, HashMap<String, String> params) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (stringBuilder.length() == 0)
                    stringBuilder.append("?");
                else
                    stringBuilder.append("&");
                stringBuilder.append(entry.getKey());
                stringBuilder.append("=");
                stringBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
            }
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(url + stringBuilder).openConnection();
        conn.setUseCaches(false);
        conn.setDefaultUseCaches(false);
        conn.setRequestMethod(method);
        conn.setDoInput(true);
		if(!method.equals("GET") && !method.equals("HEAD"))
			conn.setDoOutput(true);
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
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void setServiceAuth(HttpURLConnection conn) {
        String encoding = new Base64().encode((credentials.getServiceUser()
                + ":" + credentials.getServicePassword()).getBytes());
        conn.setRequestProperty("Authorization", "Basic " + encoding);
    }

    private void addParameters(OutputStream os, HashMap<String, String> params) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (stringBuilder.length() > 0)
                stringBuilder.append("&");
            stringBuilder.append(entry.getKey());
            stringBuilder.append("=");
            stringBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
        }
        byte[] paramData = stringBuilder.toString().getBytes();
        os.write(paramData, 0, paramData.length);
        os.flush();
    }

    private void checkStatus(HttpURLConnection conn, int status) throws IOException {
        if (conn.getResponseCode() != status)
            throw new IOException(conn.getResponseCode()
                    + " " + conn.getResponseMessage() + "\n"
                    + new String(readAllBytes(conn.getInputStream())));
    }

    private void setDefaultHeaders(HttpURLConnection conn) {
        conn.setRequestProperty("Cache", "no-store");
        conn.setRequestProperty("Connection", "keep-alive");
    }

    private static class Response {
        String path;
        String name;
        long length;
        long lastModified;
        boolean isDirectory;
        boolean isFile;
        boolean exists;
        Map<String, List<String>> headers;

        Response(String jsonString, Map<String, List<String>> headers) {
            JSONObject obj = new JSONObject(jsonString);
            this.name = obj.getString("name");
            this.path = obj.getString("path");
            this.length = obj.getLong("length");
            this.lastModified = obj.getLong("lastModified");
            this.isDirectory = obj.getBoolean("directory");
            this.isFile = obj.getBoolean("file");
            this.exists = obj.getBoolean("present");
            this.headers = headers;
        }
    }

    /**
     * Web service credentials.
     */
    public static class Credentials {
        private final String serviceUser;

        /**
         * Get the user name.
         *
         * @return The user name.
         */
        public String getServiceUser() {
            return serviceUser;
        }

        /**
         * Get the user password.
         *
         * @return The user password.
         */
        public String getServicePassword() {
            return servicePassword;
        }

        private final String servicePassword;

        /**
         * Instantiate a Credentials object.
         *
         * @param serviceUser     The user name.
         * @param servicePassword The user password.
         */
        public Credentials(String serviceUser, String servicePassword) {
            this.serviceUser = serviceUser;
            this.servicePassword = servicePassword;
        }
    }
}