package com.mku.salmon.samples.main;

import com.mku.file.IRealFile;
import com.mku.file.JavaFile;
import com.mku.file.JavaWSFile;
import com.mku.salmon.SalmonDrive;
import com.mku.salmon.samples.samples.DriveSample;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonStream;

public class WebServiceDriveProgram {
    public static void main(String[] args) throws Exception {
        String wsServicePath = "http://localhost:8080";
        String wsUser = "user";
        String wsPassword = "password";
        String drivePath = "/example_drive_" + System.currentTimeMillis();
        String password = "test123";

        SalmonStream.setAesProviderType(ProviderType.Default);

        IRealFile[] filesToImport = new JavaFile[]{new JavaFile("./data/file.txt")};

        IRealFile dir = new JavaFile("./output");
        if (!dir.exists())
            dir.mkdir();
        IRealFile exportDir = dir.getChild("export");
        if (!exportDir.exists())
            exportDir.mkdir();

        IRealFile driveDir = new JavaWSFile(drivePath, wsServicePath, new JavaWSFile.Credentials(wsUser, wsPassword));
        if (!driveDir.exists())
            driveDir.mkdir();

        SalmonDrive wsDrive = DriveSample.createDrive(driveDir, password);
        wsDrive = DriveSample.openDrive(driveDir, password);
        DriveSample.importFiles(wsDrive, filesToImport);
        DriveSample.listFiles(wsDrive);
        DriveSample.exportFiles(wsDrive, exportDir);
        DriveSample.closeDrive(wsDrive);
    }
}