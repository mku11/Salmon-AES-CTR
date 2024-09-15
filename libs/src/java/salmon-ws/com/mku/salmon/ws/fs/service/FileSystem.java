package com.mku.salmon.ws.fs.service;

import com.mku.file.IRealFile;
import com.mku.file.JavaFile;
import com.mku.salmon.SalmonDrive;
import com.mku.streams.RandomAccessStream;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility for file system operations
 *
 */
public class FileSystem {
    private static final int BUFF_LENGTH = 32768;

    static void setPath(String path) {
        FileSystem.path = path;
    }

    private static String path;

    public static IRealFile getRoot() {
        IRealFile realRoot = new JavaFile(path);
        IRealFile root = realRoot.getChild(SalmonDrive.getVirtualDriveDirectoryName());
        return root;
    }

    public static IRealFile getFile(String path) {
        String[] parts = path.split("/");
        IRealFile file = getRoot();
        for (String part : parts) {
            if (part.length() == 0)
                continue;
            if (part.equals(".."))
                throw new RuntimeException("Backwards traversing (..) is not supported");
            file = file.getChild(part);
        }
        return file;
    }

    public static String getRelativePath(IRealFile file) {
        return file.getPath().replace(getRoot().getPath(), "").replace("\\", "/");
    }

    public static IRealFile copy(String destDir, MultipartFile file) throws IOException {
        IRealFile dir = getFile(destDir);
        IRealFile rFile = dir.createFile(file.getOriginalFilename());
        InputStream inputStream = null;
        RandomAccessStream outputStream = null;
        try {
            inputStream = file.getInputStream();
            outputStream = rFile.getOutputStream();

            byte[] buff = new byte[BUFF_LENGTH];
            int bytesRead;
            while ((bytesRead = inputStream.read(buff, 0, buff.length)) > 0) {
                outputStream.write(buff, 0, bytesRead);
            }
        } catch (Exception ex) {
            throw ex;
        } finally {
            if (inputStream != null)
                inputStream.close();
            if (outputStream != null)
                outputStream.close();
        }
        return rFile;
    }
}
