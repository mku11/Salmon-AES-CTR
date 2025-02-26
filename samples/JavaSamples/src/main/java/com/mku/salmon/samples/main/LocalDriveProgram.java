package com.mku.salmon.samples.main;

import com.mku.file.IRealFile;
import com.mku.file.JavaFile;
import com.mku.salmon.SalmonDrive;
import com.mku.salmon.samples.samples.DriveSample;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonStream;

public class LocalDriveProgram
{
    public static void main(String[] args) throws Exception {
        String password = "test123";
        int threads = 2;

        SalmonStream.setAesProviderType(ProviderType.Default);

        // directories and files
        IRealFile dir = new JavaFile("./output");
        if (!dir.exists())
            dir.mkdir();

        // create
        IRealFile driveDir = dir.getChild("drive_" + System.currentTimeMillis());
        if (!driveDir.exists())
            driveDir.mkdir();
        SalmonDrive localDrive = DriveSample.createDrive(driveDir, password);

        // open
        localDrive = DriveSample.openDrive(driveDir, password);

        // import
        IRealFile[] filesToImport = new JavaFile[] {new JavaFile("./data/file.txt")};
        DriveSample.importFiles(localDrive, filesToImport, threads);

        // list
        DriveSample.listFiles(localDrive);

        // export the files
        IRealFile exportDir = driveDir.getChild("export");
        if (!exportDir.exists())
            exportDir.mkdir();
        DriveSample.exportFiles(localDrive, exportDir, threads);

        DriveSample.closeDrive(localDrive);
    }
}