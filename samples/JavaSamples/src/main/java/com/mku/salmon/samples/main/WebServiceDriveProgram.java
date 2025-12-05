package com.mku.salmon.samples.main;

import com.mku.fs.file.*;
import com.mku.salmon.samples.samples.DriveSample;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.AesStream;
import com.mku.salmonfs.drive.AesDrive;

public class WebServiceDriveProgram {
    public static void main(String[] args) throws Exception {
        String wsServicePath = "https://localhost:8443";
        String wsUser = "user";
        String wsPassword = "password";
        String drivePath = "/example_drive_" + System.currentTimeMillis();
        String password = "test123";

		System.out.println("Starting Salmon WebFS Sample");
		System.out.println("make sure your WebFS server is up and running to run this sample");
		
        // enable only if you're testing with an HTTP server
		// In all other cases you should be using an HTTPS server
        // HttpSyncClient.setAllowClearTextTraffic(true);

        AesStream.setAesProviderType(ProviderType.Default);

        IFile[] filesToImport = new File[]{new File("./data/file.txt")};

        IFile dir = new File("./output");
        if (!dir.exists())
            dir.mkdir();
        IFile exportDir = dir.getChild("export");
        if (!exportDir.exists())
            exportDir.mkdir();

        IFile driveDir = new WSFile(drivePath, wsServicePath, new Credentials(wsUser, wsPassword));
        if (!driveDir.exists())
            driveDir.mkdir();

        AesDrive wsDrive = DriveSample.createDrive(driveDir, password);
        wsDrive = DriveSample.openDrive(driveDir, password);
        DriveSample.importFiles(wsDrive, filesToImport);
        DriveSample.listFiles(wsDrive);
        DriveSample.exportFiles(wsDrive, exportDir);
        DriveSample.closeDrive(wsDrive);
    }
}