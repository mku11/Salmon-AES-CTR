package com.mku.salmon.ws.fs.service.controller;

import com.mku.file.IRealFile;
import com.mku.salmon.ws.fs.service.FileSystem;
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
     * curl -X GET "http://localhost:8080/api/info?path=/U0xNAgAAAABAAAAAAAADGOKoJtY0"
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
     * curl -X GET "http://localhost:8080/api/list?path=/U0xNAgAAAABAAAAAAAADGOKoJtY0"
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
     * curl -X POST "http://localhost:8080/api/mkdir?path=/U0xNAgAAAABAAAAAAAADGOKoJtY0"
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
     * Upload a file under a directory<br>
     * example:
     * curl -X POST -F "file=@D:/tmp/testdata/image.jpg" "http://localhost:8080/api/upload?destDir=/U0xNAgAAAABAAAAAAAACB5s0xrH2KAs="
     *
     * @param file The file
     * @param destDir The destination directory
     * @return
     * @throws IOException
     */
    @PostMapping("/upload")
    public RealFileNode upload(@RequestParam("file") MultipartFile file, String destDir) throws IOException {
        IRealFile rFile = FileSystem.copy(destDir, file);
        return new RealFileNode(rFile);
    }

    /**
     * Copy a file to the destination directory<br>
     * example:
     *  curl -X PUT "http://localhost:8080/api/copy?sourcePath=/U0xNAgAAAABAAAAAAAAA5hTh7E6oVTYJzH0=&destDir=/U0xNAgAAAABAAAAAAAACB5s0xrH2KAs="
     * @param sourcePath The file to copy
     * @param destDir The destination directory
     * @return
     * @throws IOException
     */
    @PostMapping("/copy")
    public RealFileNode copy(String sourcePath, String destDir) throws IOException {
        IRealFile source = FileSystem.getFile(sourcePath);
        IRealFile dest = FileSystem.getFile(destDir);
        IRealFile nFile;
        if (source.isDirectory()) {
            throw new IOException("Cannot copy directories, use createDirectory and copy recursively");
        } else
            nFile = source.copy(dest);
        return new RealFileNode(nFile);
    }


    /**
     * Move a file to the destination directory<br>
     * example:
     *  curl -X PUT "http://localhost:8080/api/move?sourcePath=/U0xNAgAAAABAAAAAAAAA5hTh7E6oVTYJzH0=&destDir=/U0xNAgAAAABAAAAAAAACB5s0xrH2KAs="
     * @param sourcePath The file to move
     * @param destDir The destination directory
     * @return
     * @throws IOException
     */
    @PutMapping("/move")
    public RealFileNode move(String sourcePath, String destDir) throws IOException {
        IRealFile source = FileSystem.getFile(sourcePath);
        IRealFile dest = FileSystem.getFile(destDir);
        IRealFile nFile;
        if (source.isDirectory()) {
            throw new IOException("Cannot move directories, use createDirectory and move recursively");
        } else
            nFile = source.move(dest);
        return new RealFileNode(nFile);
    }

    /**
     * Rename a file or directory<br>
     * example:
     * curl -X PUT "http://localhost:8080/api/rename?path=/U0xNAgAAAABAAAAAAAADEWyHD6u05Tq-UQ==&filename=U0xNAgAAAABAAAAAAAAA5hTh7E6oVTYJzH0="
     * @param path The file or directory path
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
     * curl -X DELETE "http://localhost:8080/api/delete?path=/U0xNAgAAAABAAAAAAAADGOKoJtY0"
     * @param path The file or directory path
     * @return
     */
    @DeleteMapping("/delete")
    public RealFileNode delete(String path) {
        IRealFile file = FileSystem.getFile(path);
        file.delete();
        return new RealFileNode(file);
    }
}
