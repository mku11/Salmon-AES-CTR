package com.mku.salmon.samples.main;

import com.mku.fs.file.File;
import com.mku.fs.file.HttpFile;
import com.mku.fs.file.IFile;
import com.mku.salmon.samples.samples.DriveSample;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.AesStream;
import com.mku.salmonfs.drive.AesDrive;

import java.io.IOException;

public class HttpDriveProgram {
    public static void main(String[] args) throws Exception {
        String httpDriveURL = "http://localhost/test/httpserv/vault";
        String password = "test123";
        int threads = 1;

        AesStream.setAesProviderType(ProviderType.Default);

        IFile dir = new File("./output");
        if (!dir.exists())
            dir.mkdir();
        IFile exportDir = dir.getChild("export");
        if (!exportDir.exists())
            exportDir.mkdir();

        HttpFile httpDir = new HttpFile(httpDriveURL);
        AesDrive httpDrive = DriveSample.openDrive(httpDir, password);
        DriveSample.listFiles(httpDrive);
        DriveSample.exportFiles(httpDrive, exportDir, threads);
        DriveSample.closeDrive(httpDrive);
    }
}