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
 * Provides endpoints for manipulating the filesystem remotely.
 */
public class RealFileController {

    /**
     * Get details about a file
     * @param path The file path
     * @return
     */
    @GetMapping("/info")
    public RealFileNode info(String path) {
        return new RealFileNode(FileSystem.getFile(path));
    }

    /**
     * List files and directories under a directory
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
     * Create a directory
     * @param path The directory path
     * @return
     */
    @PostMapping("/mkdir")
    public RealFileNode mkdir(String path, boolean isDirectory) throws IOException {
        IRealFile file = FileSystem.getFile(path);
        IRealFile parent = file.getParent();
        if (isDirectory)
            file = parent.createDirectory(file.getBaseName());
        else
            file = parent.createFile(file.getBaseName());
        return new RealFileNode(file);
    }

    /**
     * Upload a file under a directory
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
     * Copy a file to the destination directory
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
     * Move a file to the destination directory
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
     * Rename a file or directory
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
     * Delete a file or directory
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
