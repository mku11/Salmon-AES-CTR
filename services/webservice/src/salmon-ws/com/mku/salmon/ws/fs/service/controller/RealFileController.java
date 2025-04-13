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

import com.mku.fs.file.IFile;
import com.mku.streams.InputStreamWrapper;
import com.mku.streams.RandomAccessStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
/**
 * Provides endpoints for manipulating the filesystem remotely. The file system is expected to contain encrypted
 * files. The function of these endpoints is to simply facilitate most common fs operations, all encryption and
 * decryption will happen at the client side.
 */
public class RealFileController {

    /**
     * Get details about a file<br>
     * example:
     * curl -X GET "http://localhost:8080/api/info?path=/fs/U0xNAgAAAABAAAAAAAADGOKoJtY0"
     *
     * @param path The file path
     * @return
     */
    @GetMapping("/info")
    public RealFileNode info(String path) throws IOException {
		path = FileSystem.getInstance().validateFilePath(path);
        System.out.println("INFO, path: " + path);
        IFile file = FileSystem.getInstance().getFile(path);
        if (file == null)
            throw new IOException("Partial path does not exist: " + path);
        return new RealFileNode(file);
    }

    /**
     * List files and directories under a directory<br>
     * example:
     * curl -X GET "http://localhost:8080/api/list?path=/fs/U0xNAgAAAABAAAAAAAADGOKoJtY0"
     *
     * @param path The directory path
     * @return
     */
    @GetMapping("/list")
    public List<RealFileNode> list(String path) throws IOException {
		path = FileSystem.getInstance().validateFilePath(path);
        System.out.println("LIST, path: " + path);
        ArrayList<RealFileNode> list = new ArrayList<>();
        IFile file = FileSystem.getInstance().getFile(path);
        if (file == null)
            throw new IOException("Directory does not exist");
        if (file.isDirectory()) {
            IFile[] files = file.listFiles();
            for (IFile rFile : files)
                list.add(new RealFileNode(rFile));
        } else {
            throw new IOException("Resource is a file");
        }
        return list;
    }

    /**
     * Create a directory<br>
     * example:
     * curl -X POST "http://localhost:8080/api/mkdir?path=/fs/U0xNAgAAAABAAAAAAAADGOKoJtY0"
     *
     * @param path The directory path
     * @return
     */
    @PostMapping("/mkdir")
    public RealFileNode mkdir(String path) throws IOException {
		path = FileSystem.getInstance().validateFilePath(path);
        System.out.println("MKDIR, path: " + path);
        IFile file = FileSystem.getInstance().getFile(path);
        IFile parent = file.getParent();
        if (parent == null || !parent.exists())
            throw new IOException("Parent does not exist");
        file = parent.createDirectory(file.getName());
        if (file == null || !file.exists() || !file.isDirectory())
            throw new IOException("Could not create dir");
        return new RealFileNode(file);
    }


    /**
     * Create a file<br>
     * example:
     * curl -X POST "http://localhost:8080/api/mkdir?path=/fs/U0xNAgAAAABAAAAAAAADGOKoJtY0"
     *
     * @param path The directory path
     * @return
     */
    @PostMapping("/create")
    public RealFileNode create(String path) throws IOException {
		path = FileSystem.getInstance().validateFilePath(path);
        System.out.println("CREATE, path: " + path);
        IFile file = FileSystem.getInstance().getFile(path);
        IFile parent = file.getParent();
        if (parent == null || !parent.exists() || !parent.isDirectory())
            throw new IOException("Parent does not exist");
        file = parent.createFile(file.getName());
        if (file == null || !file.exists() || !file.isFile())
            throw new IOException("Could not create file");
        return new RealFileNode(file);
    }

    /**
     * Upload a file<br>
     * example:
     * curl -X POST -F "file=@D:/tmp/testdata/data.dat" "http://localhost:8080/api/upload?path=/fs/U0xNAgAAAABAAAAAAAACB5s0xrH2KAs=&position=0"
     *
     * @param file The file data
     * @param path The path to the file
     * @param path The byte position of the file that writing will start
     * @return
     * @throws IOException
     */
    @PostMapping("/upload")
    public ResponseEntity<RealFileNode> upload(@RequestParam("file") MultipartFile file, String path, Long position) throws IOException {
		path = FileSystem.getInstance().validateFilePath(path);
        System.out.println("UPLOAD, path: " + path + ", position: " + position + ", size: " + file.getSize());
        IFile rFile = FileSystem.getInstance().write(path, file, position);
        return new ResponseEntity<>(new RealFileNode(rFile), position > 0 ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK);
    }

    /**
     * Get a file<br>
     * example:
     * curl -X GET "http://localhost:8080/api/get?path=/fs/U0xNAgAAAABAAAAAAAACB5s0xrH2KAs="
     *
     * @param path The path to the file
     * @param path The byte position of the file that reading will start from
     * @return
     * @throws IOException
     */
    @GetMapping(path = "/get")
    public ResponseEntity<Resource> get(String path, Long position) throws IOException {
		path = FileSystem.getInstance().validateFilePath(path);
        System.out.println("GET, path: " + path + ", position: " + position);
        IFile rFile = FileSystem.getInstance().getFile(path);
        if (rFile == null || !rFile.exists() || !rFile.isFile())
            throw new IOException("File does not exist");
        RandomAccessStream stream = rFile.getInputStream();
        stream.setPosition(position);
        InputStreamWrapper inputStreamWrapper = new InputStreamWrapper(stream);
        InputStreamResource resource = new InputStreamResource(inputStreamWrapper);
        return ResponseEntity.status(position > 0 ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .contentLength(rFile.getLength() - position)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * Copy a file to the destination directory<br>
     * example:
     * curl -X PUT "http://localhost:8080/api/copy?sourcePath=/U0xNAgAAAABAAAAAAAAA5hTh7E6oVTYJzH0=&destDir=/fs/U0xNAgAAAABAAAAAAAACB5s0xrH2KAs=&filename=U0xNAgAAAABAAAAAAAACB5s0xrH2KA1"
     *
     * @param path    The file to copy
     * @param destDir The destination directory
     * @return
     * @throws IOException
     */
    @PostMapping("/copy")
    public RealFileNode copy(String path, String destDir, String filename) throws IOException {
		path = FileSystem.getInstance().validateFilePath(path);
		destDir = FileSystem.getInstance().validateFilePath(destDir);
		filename = FileSystem.getInstance().validateFilePath(filename);
        System.out.println("COPY, path: " + path + ", filename: " + filename);
        IFile source = FileSystem.getInstance().getFile(path);
        if (source == null || !source.exists())
            throw new IOException("Path does not exist");
        IFile dest = FileSystem.getInstance().getFile(destDir);
        if (dest == null || !dest.exists() || !dest.isDirectory())
            throw new IOException("Destination directory does not exist");
        IFile nFile;
        if (source.isDirectory()) {
            throw new IOException("Cannot copy directories, use createDirectory and copy recursively");
        } else {
			IFile.CopyOptions options = new IFile.CopyOptions();
			options.newFilename = filename;
            nFile = source.copy(dest, options);
		}
        return new RealFileNode(nFile);
    }


    /**
     * Move a file to the destination directory<br>
     * example:
     * curl -X PUT "http://localhost:8080/api/move?sourcePath=/fs/U0xNAgAAAABAAAAAAAAA5hTh7E6oVTYJzH0=&destDir=/fs/U0xNAgAAAABAAAAAAAACB5s0xrH2KAs=&filename=U0xNAgAAAABAAAAAAAACB5s0xrH2KA1"
     *
     * @param path     The file to move
     * @param destDir  The destination directory
     * @param filename The new file name (optional)
     * @return
     * @throws IOException
     */
    @PutMapping("/move")
    public RealFileNode move(String path, String destDir, String filename) throws IOException {
		path = FileSystem.getInstance().validateFilePath(path);
		destDir = FileSystem.getInstance().validateFilePath(destDir);
		filename = FileSystem.getInstance().validateFilePath(filename);
        System.out.println("MOVE, path: " + path + ", destDir: " + destDir + ", filename: " + filename);
        IFile source = FileSystem.getInstance().getFile(path);
        if (source == null || !source.exists())
            throw new IOException("Path does not exist");
        IFile dest = FileSystem.getInstance().getFile(destDir);
        if (dest == null || !dest.exists() || !dest.isDirectory())
            throw new IOException("Destination directory does not exist");
        IFile nFile;
        if (source.isDirectory()) {
            throw new IOException("Cannot move directories, use createDirectory and move recursively");
        } else {
			IFile.MoveOptions options = new IFile.MoveOptions();
			options.newFilename = filename;
            nFile = source.move(dest, options);
		}
        return new RealFileNode(nFile);
    }

    /**
     * Rename a file or directory<br>
     * example:
     * curl -X PUT "http://localhost:8080/api/rename?path=/fs/U0xNAgAAAABAAAAAAAADEWyHD6u05Tq-UQ==&filename=U0xNAgAAAABAAAAAAAAA5hTh7E6oVTYJzH0="
     *
     * @param path     The file or directory path
     * @param filename The new filename
     * @return
     * @throws IOException
     */
    @PutMapping("/rename")
    public RealFileNode rename(String path, String filename) throws IOException {
		path = FileSystem.getInstance().validateFilePath(path);
		filename = FileSystem.getInstance().validateFilePath(filename);
        System.out.println("RENAME, path: " + path + ", filename: " + filename);
        IFile file = FileSystem.getInstance().getFile(path);
        if (file == null || !file.exists())
            throw new IOException("Path does not exist");
        file.renameTo(filename);
        return new RealFileNode(file);
    }

    /**
     * Delete a file or directory<br>
     * example:
     * curl -X DELETE "http://localhost:8080/api/delete?path=/fs/U0xNAgAAAABAAAAAAAADGOKoJtY0"
     *
     * @param path The file or directory path
     * @return
     */
    @DeleteMapping("/delete")
    public RealFileNode delete(String path) throws IOException {
		path = FileSystem.getInstance().validateFilePath(path);
        System.out.println("DELETE, path: " + path);
        IFile file = FileSystem.getInstance().getFile(path);
        if (file == null || !file.exists())
            throw new IOException("Path does not exist");
        file.delete();
        return new RealFileNode(file);
    }

    /**
     * Set the file length<br>
     * example:
     * curl -X PUT "http://localhost:8080/api/rename?path=/fs/U0xNAgAAAABAAAAAAAADEWyHD6u05Tq-UQ==&length=1204"
     *
     * @param path   The file
     * @param length The new size
     * @return
     * @throws IOException
     */
    @PutMapping("/setLength")
    public RealFileNode setLength(String path, long length) throws IOException {
		path = FileSystem.getInstance().validateFilePath(path);
        System.out.println("SETLENGTH, path: " + path + ", length: " + length);
        RandomAccessStream stream = null;
        try {
            IFile file = FileSystem.getInstance().getFile(path);
            if (file == null || !file.exists())
                throw new IOException("Path does not exist");
            stream = file.getOutputStream();
            stream.setLength(length);
            return new RealFileNode(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            stream.close();
        }
    }
}
