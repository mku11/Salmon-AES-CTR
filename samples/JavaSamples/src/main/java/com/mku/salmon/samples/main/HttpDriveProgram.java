package com.mku.salmon.samples.main;

import com.mku.fs.file.*;
import com.mku.salmon.samples.samples.DriveSample;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.AesStream;
import com.mku.salmonfs.drive.AesDrive;

public class HttpDriveProgram {
    public static void main(String[] args) throws Exception {
        String httpDriveURL = "https://localhost/testvault";
        String password = "test123";
        String httpUser = "user";
        String httpPassword = "password";
        int threads = 1;

		System.out.println("Starting HTTP Sample");
		System.out.println("make sure your HTTP server is up and running to run this sample");
		
        // enable only if you're testing with an HTTP server
		// In all other cases you should be using an HTTPS server
        // HttpSyncClient.setAllowClearTextTraffic(true);

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