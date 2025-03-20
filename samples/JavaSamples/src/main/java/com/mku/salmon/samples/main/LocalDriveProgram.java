package com.mku.salmon.samples.main;

import com.mku.fs.file.File;
import com.mku.fs.file.IFile;
import com.mku.salmon.samples.samples.DriveSample;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.AesStream;
import com.mku.salmonfs.drive.AesDrive;

public class LocalDriveProgram
{
    public static void main(String[] args) throws Exception {
        String password = "test123";
        int threads = 1;

        AesStream.setAesProviderType(ProviderType.Default);

        // directories and files
        IFile dir = new File("./output");
        if (!dir.exists())
            dir.mkdir();

        // create
        IFile driveDir = dir.getChild("drive_" + System.currentTimeMillis());
        if (!driveDir.exists())
            driveDir.mkdir();
        AesDrive localDrive = DriveSample.createDrive(driveDir, password);

        // open
        localDrive = DriveSample.openDrive(driveDir, password);

        // import
        IFile[] filesToImport = new File[] {new File("./data/file.txt")};
        DriveSample.importFiles(localDrive, filesToImport, threads);

        // list
        DriveSample.listFiles(localDrive);

        // export the files
        IFile exportDir = driveDir.getChild("export");
        if (!exportDir.exists())
            exportDir.mkdir();
        DriveSample.exportFiles(localDrive, exportDir, threads);

        DriveSample.closeDrive(localDrive);
    }
}