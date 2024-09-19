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

import com.mku.file.IRealFile;
import com.mku.salmon.ws.fs.service.FileSystem;
import com.mku.streams.InputStreamWrapper;
import com.mku.streams.RandomAccessStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
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
    public RealFileNode info(String path) {
        return new RealFileNode(FileSystem.getFile(path));
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
        ArrayList<RealFileNode> list = new ArrayList<>();
        IRealFile file = FileSystem.getFile(path);
        if (file.isDirectory()) {
            IRealFile[] files = file.listFiles();
            for (IRealFile rFile : files)
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
        IRealFile file = FileSystem.getFile(path);
        IRealFile parent = file.getParent();
        file = parent.createDirectory(file.getBaseName());
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
        IRealFile file = FileSystem.getFile(path);
        IRealFile parent = file.getParent();
        file = parent.createFile(file.getBaseName());
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
    public ResponseEntity<RealFileNode> upload(@RequestParam("file") MultipartFile file, String path, long position) throws IOException {
        IRealFile rFile = FileSystem.write(path, file, position);
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
    public ResponseEntity<Resource> get(String path, long position) throws IOException {
        IRealFile rFile = FileSystem.getFile(path);
        RandomAccessStream stream = rFile.getInputStream();
        stream.setPosition(position);
        InputStreamWrapper inputStreamWrapper = new InputStreamWrapper(stream);
        InputStreamResource resource = new InputStreamResource(inputStreamWrapper);
        return ResponseEntity.status(position > 0?HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .contentLength(rFile.length() - position)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * Copy a file to the destination directory<br>
     * example:
     * curl -X PUT "http://localhost:8080/api/copy?sourcePath=/U0xNAgAAAABAAAAAAAAA5hTh7E6oVTYJzH0=&destDir=/fs/U0xNAgAAAABAAAAAAAACB5s0xrH2KAs=&filename=U0xNAgAAAABAAAAAAAACB5s0xrH2KA1"
     *
     * @param sourcePath The file to copy
     * @param destDir    The destination directory
     * @return
     * @throws IOException
     */
    @PostMapping("/copy")
    public RealFileNode copy(String sourcePath, String destDir, String filename) throws IOException {
        IRealFile source = FileSystem.getFile(sourcePath);
        IRealFile dest = FileSystem.getFile(destDir);
        IRealFile nFile;
        if (source.isDirectory()) {
            throw new IOException("Cannot copy directories, use createDirectory and copy recursively");
        } else
            nFile = source.copy(dest, filename);
        return new RealFileNode(nFile);
    }


    /**
     * Move a file to the destination directory<br>
     * example:
     * curl -X PUT "http://localhost:8080/api/move?sourcePath=/fs/U0xNAgAAAABAAAAAAAAA5hTh7E6oVTYJzH0=&destDir=/fs/U0xNAgAAAABAAAAAAAACB5s0xrH2KAs=&filename=U0xNAgAAAABAAAAAAAACB5s0xrH2KA1"
     *
     * @param sourcePath The file to move
     * @param destDir    The destination directory
     * @param filename   The new file name (optional)
     * @return
     * @throws IOException
     */
    @PutMapping("/move")
    public RealFileNode move(String sourcePath, String destDir, String filename) throws IOException {
        IRealFile source = FileSystem.getFile(sourcePath);
        IRealFile dest = FileSystem.getFile(destDir);
        IRealFile nFile;
        if (source.isDirectory()) {
            throw new IOException("Cannot move directories, use createDirectory and move recursively");
        } else
            nFile = source.move(dest, filename);
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
     * @throws FileNotFoundException
     */
    @PutMapping("/rename")
    public RealFileNode rename(String path, String filename) throws FileNotFoundException {
        IRealFile file = FileSystem.getFile(path);
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
    public RealFileNode delete(String path) {
        IRealFile file = FileSystem.getFile(path);
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
     * @throws FileNotFoundException
     */
    @PutMapping("/setLength")
    public RealFileNode setLength(String path, long length) throws IOException {
        RandomAccessStream stream = null;
        try {
            IRealFile file = FileSystem.getFile(path);
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
