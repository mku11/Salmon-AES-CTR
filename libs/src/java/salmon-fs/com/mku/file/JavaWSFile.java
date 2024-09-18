package com.mku.file;
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
import com.mku.func.BiConsumer;
import com.mku.streams.RandomAccessStream;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Salmon RealFile implementation for Java.
 */
public class JavaWSFile implements IRealFile {
    private static final String PATH = "path";
    private static final String DEST_DIR = "path";
    private static final String FILENAME = "filename";
    public static String Separator = "/";
    public static CloseableHttpClient client = HttpClients.createDefault();
    private String filePath;

    public String getServicePath() {
        return servicePath;
    }

    private String servicePath;

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

    public static class Credentials {
        private final String serviceUser;

        public String getServiceUser() {
            return serviceUser;
        }

        public String getServicePassword() {
            return servicePassword;
        }

        private final String servicePassword;

        public Credentials(String serviceUser, String servicePassword) {
            this.serviceUser = serviceUser;
            this.servicePassword = servicePassword;
        }
    }

    private class Response {
        String path;
        String name;
        long length;
        long lastModified;
        boolean isDirectory;
        boolean isFile;
        boolean exists;
        Header[] headers;

        private Response(String jsonString, Header[] headers) {
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
     * Instantiate a real file represented by the filepath provided (Remote read-write drive)
     *
     * @param path        The filepath. This should be a relative path of the vault folder
     * @param servicePath The REST API server path (ie https://localhost:8080/)
     * @param credentials The REST API credentials
     */
    public JavaWSFile(String path, String servicePath, Credentials credentials) {
        this.servicePath = servicePath;
        this.filePath = path;
        this.credentials = credentials;
    }

    private Response getResponse() throws Exception {
        URIBuilder uriBuilder = new URIBuilder(this.servicePath + "/api/info");
        uriBuilder.addParameter(PATH, this.filePath);
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        setServiceAuth(httpGet);
        Response response;
        CloseableHttpResponse httpResponse = null;
        try {
            httpResponse = client.execute(httpGet);
            checkStatus(httpResponse, HttpStatus.SC_OK);
            response = new Response(new String(httpResponse.getEntity().getContent().readAllBytes()),
                    httpResponse.getAllHeaders());
        } finally {
            if (httpResponse != null)
                httpResponse.close();
        }
        return response;
    }

    private void setServiceAuth(HttpRequest httpRequest) {
        String encoding = new Base64().encode((credentials.getServiceUser() + ":" + credentials.getServicePassword()).getBytes());
        httpRequest.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
    }

    /**
     * Create a directory under this directory.
     *
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    public IRealFile createDirectory(String dirName) {
        String nDirPath = filePath + Separator + dirName;
        CloseableHttpResponse httpResponse = null;
        try {
            URIBuilder uriBuilder = new URIBuilder(this.servicePath + "/api/mkdir");
            uriBuilder.addParameter(PATH, nDirPath);
            HttpPost httpPost = new HttpPost(uriBuilder.build());
            setServiceAuth(httpPost);

            httpResponse = client.execute(httpPost);
            checkStatus(httpResponse, HttpStatus.SC_OK);
            httpResponse.close();
            JavaWSFile dir = new JavaWSFile(nDirPath, servicePath, credentials);
            return dir;
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        } finally {
            if (httpResponse != null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Create a file under this directory.
     *
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    public IRealFile createFile(String filename) throws IOException {
        String nFilePath = filePath + Separator + filename;
        URIBuilder uriBuilder;
        CloseableHttpResponse httpResponse = null;
        try {
            uriBuilder = new URIBuilder(this.servicePath + "/api/create");
            uriBuilder.addParameter(PATH, nFilePath);
            HttpPost httpPost = new HttpPost(uriBuilder.build());
            setServiceAuth(httpPost);
            httpResponse = client.execute(httpPost);
            checkStatus(httpResponse, HttpStatus.SC_OK);
            httpResponse.close();
            JavaWSFile JavaFile = new JavaWSFile(nFilePath, servicePath, credentials);
            return JavaFile;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } finally {
            if (httpResponse != null)
                httpResponse.close();
        }

    }

    /**
     * Delete this file or directory.
     *
     * @return True if deletion is successful.
     */
    public boolean delete() {
        if (isDirectory()) {
            IRealFile[] files = listFiles();
            for (IRealFile file : files) {
                CloseableHttpResponse httpResponse = null;
                try {
                    URIBuilder uriBuilder = new URIBuilder(this.servicePath + "/api/delete");
                    uriBuilder.addParameter(PATH, file.getPath());
                    HttpDelete httpDelete = new HttpDelete(uriBuilder.build());
                    setServiceAuth(httpDelete);
                    httpResponse = client.execute(httpDelete);
                    checkStatus(httpResponse, HttpStatus.SC_OK);
                    httpResponse.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    if (httpResponse != null) {
                        try {
                            httpResponse.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        CloseableHttpResponse httpResponse = null;
        try {
            URIBuilder dirUriBuilder = new URIBuilder(this.servicePath + "/api/delete");
            dirUriBuilder.addParameter(PATH, this.filePath);
            HttpDelete httpDelete = new HttpDelete(dirUriBuilder.build());
            setServiceAuth(httpDelete);
            httpResponse = client.execute(httpDelete);
            checkStatus(httpResponse, HttpStatus.SC_OK);
            httpResponse.close();
            return true;
        } catch (Exception e) {
            if (httpResponse != null) {
                try {
                    httpResponse.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * True if file or directory exists.
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
        return new JavaWSFileStream(this, "r");
    }

    /**
     * Get a stream for writing to this file.
     *
     * @return The stream to write to.
     * @throws FileNotFoundException Thrown if file not found
     */
    public RandomAccessStream getOutputStream() throws FileNotFoundException {
        return new JavaWSFileStream(this, "rw");
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
        JavaWSFile parent = new JavaWSFile(path.substring(0, index), servicePath, credentials);
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
        try {
            return getResponse().isDirectory;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
    public long length() {
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
        CloseableHttpResponse httpResponse = null;
        try {
            if (getResponse().isDirectory) {
                URIBuilder uriBuilder = new URIBuilder(this.servicePath + "/api/list");
                uriBuilder.addParameter(PATH, this.filePath);
                HttpGet httpGet = new HttpGet(uriBuilder.build());
                setServiceAuth(httpGet);
                httpResponse = client.execute(httpGet);
                checkStatus(httpResponse, HttpStatus.SC_OK);
                int res = getFileListCount(httpResponse.getEntity().getContent());
                httpResponse.close();
                return res;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (httpResponse != null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return 0;
    }

    private int getFileListCount(InputStream contentInputStream) throws IOException {
        String content = new String(contentInputStream.readAllBytes());
        JSONArray object = new JSONArray(content);
        return object.length();
    }

    private List<Response> parseFileList(InputStream contentInputStream) throws IOException {
        String content = new String(contentInputStream.readAllBytes());
        JSONArray object = new JSONArray(content);
        List<Response> list = new ArrayList<>();
        for (Object obj : object) {
            JSONObject jsonObject = (JSONObject) obj;
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
    public IRealFile[] listFiles() {
        Response[] files = null;
        CloseableHttpResponse httpResponse = null;
        try {
            if (getResponse().isDirectory) {
                URIBuilder uriBuilder = new URIBuilder(this.servicePath + "/api/list");
                uriBuilder.addParameter(PATH, this.filePath);
                HttpGet httpGet = new HttpGet(uriBuilder.build());
                setServiceAuth(httpGet);
                httpResponse = client.execute(httpGet);
                checkStatus(httpResponse, HttpStatus.SC_OK);
                files = parseFileList(httpResponse.getEntity().getContent()).toArray(new Response[0]);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (httpResponse != null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (files == null)
            return new JavaWSFile[0];

        List<JavaWSFile> realFiles = new ArrayList<>();
        List<JavaWSFile> realDirs = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            JavaWSFile file = new JavaWSFile(files[i].path, servicePath, credentials);
            if (file.isDirectory())
                realDirs.add(file);
            else
                realFiles.add(file);
        }
        realDirs.addAll(realFiles);
        return realDirs.toArray(new JavaWSFile[0]);
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
        newName = newName != null ? newName : getBaseName();
        //TODO: ToSync
        if (newDir == null || !newDir.exists())
            throw new IOException("Target directory does not exist");
        IRealFile newFile = newDir.getChild(newName);
        if (newFile != null && newFile.exists())
            throw new IOException("Another file/directory already exists");

        if (isDirectory()) {
            throw new IOException("Could not move directory use IRealFile moveRecursively() instead");
        } else {
            URIBuilder uriBuilder;
            CloseableHttpResponse httpResponse = null;
            try {
                uriBuilder = new URIBuilder(this.servicePath + "/api/move");
                uriBuilder.addParameter(PATH, this.filePath);
                uriBuilder.addParameter(DEST_DIR, newDir.getPath());
                uriBuilder.addParameter(FILENAME, newName);

                HttpPut httpPut = new HttpPut(uriBuilder.build());
                setServiceAuth(httpPut);
                httpResponse = client.execute(httpPut);
                checkStatus(httpResponse, HttpStatus.SC_OK);
                Response res = new Response(httpResponse.getEntity().toString(), httpResponse.getAllHeaders());
                newFile = new JavaWSFile(res.path, servicePath, credentials);
                return newFile;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (httpResponse != null)
                    httpResponse.close();
            }
        }
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
        newName = newName != null ? newName : getBaseName();
        if (newDir == null || !newDir.exists())
            throw new IOException("Target directory does not exists");
        IRealFile newFile = newDir.getChild(newName);
        if (newFile != null && newFile.exists())
            throw new IOException("Another file/directory already exists");
        if (isDirectory()) {
            throw new IOException("Could not copy directory use IRealFile copyRecursively() instead");
        } else {
            URIBuilder uriBuilder = null;
            CloseableHttpResponse httpResponse = null;
            try {
                uriBuilder = new URIBuilder(this.servicePath + "/api/copy");
                uriBuilder.addParameter(PATH, this.filePath);
                uriBuilder.addParameter(DEST_DIR, newDir.getPath());
                uriBuilder.addParameter(FILENAME, newName);
                HttpPut httpPut = new HttpPut(uriBuilder.build());
                setServiceAuth(httpPut);
                httpResponse = client.execute(httpPut);
                checkStatus(httpResponse, HttpStatus.SC_OK);
                Response res = new Response(httpResponse.getEntity().toString(), httpResponse.getAllHeaders());
                newFile = new JavaWSFile(res.path, this.servicePath, credentials);
                return newFile;
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            } finally {
                if (httpResponse != null)
                    httpResponse.close();
            }
        }
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
        JavaWSFile child = new JavaWSFile(filePath + Separator + filename, servicePath, credentials);
        return child;
    }

    /**
     * Rename the current file or directory.
     *
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    public boolean renameTo(String newFilename) {
        CloseableHttpResponse httpResponse = null;
        try {
            URIBuilder uriBuilder = new URIBuilder(this.servicePath + "/api/rename");
            uriBuilder.addParameter(PATH, this.filePath);
            uriBuilder.addParameter(FILENAME, newFilename);
            HttpPut httpPut = new HttpPut(uriBuilder.build());
            setServiceAuth(httpPut);
            httpResponse = client.execute(httpPut);
            checkStatus(httpResponse, HttpStatus.SC_OK);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (httpResponse != null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return false;
    }

    /**
     * Create this directory under the current filepath.
     *
     * @return True if created.
     */
    public boolean mkdir() {
        CloseableHttpResponse httpResponse = null;
        try {
            URIBuilder uriBuilder = new URIBuilder(this.servicePath + "/api/mkdir");
            uriBuilder.addParameter(PATH, this.filePath);
            HttpPost httpPost = new HttpPost(uriBuilder.build());
            setServiceAuth(httpPost);
            httpResponse = client.execute(httpPost);
            checkStatus(httpResponse, HttpStatus.SC_OK);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (httpResponse != null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return false;
    }

    /**
     * Returns a string representation of this object
     */
    @Override
    public String toString() {
        return filePath;
    }

    private void checkStatus(HttpResponse httpResponse, int status) throws IOException {
        if (httpResponse.getStatusLine().getStatusCode() != status) {
            throw new IOException(httpResponse.getStatusLine().getStatusCode()
                    + " " + httpResponse.getStatusLine().getReasonPhrase());
        }
    }
}
