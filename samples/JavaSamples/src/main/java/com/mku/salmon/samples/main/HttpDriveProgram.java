package com.mku.salmon.samples.main;

import com.mku.fs.file.*;
import com.mku.salmon.samples.samples.DriveSample;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.AesStream;
import com.mku.salmonfs.drive.AesDrive;

public class HttpDriveProgram {
    public static void main(String[] args) throws Exception {
        String httpDriveURL = "http://localhost/test/httpserv/vault";
        String password = "test123";
        String httpUser = "user";
        String httpPassword = "password";
        int threads = 1;

        // only for demo purposes, you should be using HTTPS traffic
        HttpSyncClient.setAllowClearTextTraffic(true);

        AesStream.setAesProviderType(ProviderType.Default);

        IFile dir = new File("./output");
        if (!dir.exists())
            dir.mkdir();
        IFile exportDir = dir.getChild("export");
        if (!exportDir.exists())
            exportDir.mkdir();

        HttpFile httpDir = new HttpFile(httpDriveURL, new Credentials(httpUser, httpPassword));
        AesDrive httpDrive = DriveSample.openDrive(httpDir, password);
        DriveSample.listFiles(httpDrive);
        DriveSample.exportFiles(httpDrive, exportDir, threads);
        DriveSample.closeDrive(httpDrive);
    }
}