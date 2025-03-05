package com.mku.salmon.samples.main;

import com.mku.file.IRealFile;
import com.mku.file.JavaFile;
import com.mku.file.JavaHttpFile;
import com.mku.salmon.SalmonDrive;
import com.mku.salmon.samples.samples.DriveSample;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonStream;

import java.io.IOException;

public class HttpDriveProgram {
    public static void main(String[] args) throws Exception {
        String httpDriveURL = "http://localhost:8000/test/httpserv/vault";
        String password = "test123";
        int threads = 1;

        SalmonStream.setAesProviderType(ProviderType.Default);

        IRealFile dir = new JavaFile("./output");
        if (!dir.exists())
            dir.mkdir();
        IRealFile exportDir = dir.getChild("export");
        if (!exportDir.exists())
            exportDir.mkdir();

        JavaHttpFile httpDir = new JavaHttpFile(httpDriveURL);
        SalmonDrive httpDrive = DriveSample.openDrive(httpDir, password);
        DriveSample.listFiles(httpDrive);
        DriveSample.exportFiles(httpDrive, exportDir, threads);
        DriveSample.closeDrive(httpDrive);
    }
}