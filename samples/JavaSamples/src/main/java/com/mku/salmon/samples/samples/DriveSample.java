package com.mku.salmon.samples.samples;

import com.mku.file.IRealFile;
import com.mku.file.JavaFile;
import com.mku.file.JavaHttpFile;
import com.mku.file.JavaWSFile;
import com.mku.salmon.SalmonDrive;
import com.mku.salmon.SalmonFile;
import com.mku.salmon.drive.JavaDrive;
import com.mku.salmon.drive.JavaHttpDrive;
import com.mku.salmon.drive.JavaWSDrive;
import com.mku.salmon.sequence.SalmonFileSequencer;
import com.mku.salmon.sequence.SalmonSequenceSerializer;
import com.mku.salmon.streams.SalmonFileInputStream;
import com.mku.salmon.utils.SalmonFileCommander;

import java.io.IOException;

public class DriveSample {
    static SalmonFileSequencer sequencer;

    static {
        try {
            sequencer = DriveSample.createSequencer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static SalmonDrive createDrive(IRealFile vaultDir, String password) throws IOException {
        // create a drive
        SalmonDrive drive = null;
        if (vaultDir instanceof JavaFile) { // local
            drive = JavaDrive.create(vaultDir, password, sequencer);
        } else if (vaultDir instanceof JavaWSFile) { // web service
            drive = JavaWSDrive.create(vaultDir, password, sequencer);
        }
        System.out.println("drive created: " + drive.getRealRoot().getAbsolutePath());
        return drive;
    }

    public static SalmonDrive openDrive(IRealFile vaultDir, String password) throws IOException {
        // open a drive
        SalmonDrive drive = null;
        if (vaultDir instanceof JavaFile) { // local
            drive = JavaDrive.open(vaultDir, password, sequencer);
        } else if (vaultDir instanceof JavaWSFile) { // web service
            drive = JavaDrive.open(vaultDir, password, sequencer);
        } else if (vaultDir instanceof JavaHttpFile) { // http (Read-only)
            drive = JavaHttpDrive.open(vaultDir, password);
        }
        if (drive != null)
            System.out.println("drive opened: " + drive.getRealRoot().getAbsolutePath());
        return drive;
    }

    public static void importFiles(SalmonDrive drive, IRealFile[] filesToImport) throws Exception {
        importFiles(drive, filesToImport, 1);
    }

    public static void importFiles(SalmonDrive drive, IRealFile[] filesToImport, int thread) throws Exception {
        SalmonFileCommander commander = new SalmonFileCommander(256 * 1024, 256 * 1024, threads);


        // import multiple files
        SalmonFile[] filesImported = commander.importFiles(filesToImport, drive.getRoot(), false, true,
                (taskProgress) ->
                {
                    System.out.println("file importing: " + taskProgress.getFile().getBaseName() + ": "
                            + taskProgress.getProcessedBytes() + "/" + taskProgress.getTotalBytes() + " bytes");
                }, IRealFile.autoRename, (file, ex) ->
                {
                    // file failed to import
                });
        System.out.println("Files imported");

        // close the file commander
        commander.close();
    }

    public static void exportFiles(SalmonDrive drive, IRealFile dir) throws Exception {
        exportFiles(drive, dir, 1);
    }

    public static void exportFiles(SalmonDrive drive, IRealFile dir, int threads) throws Exception {
        SalmonFileCommander commander = new SalmonFileCommander(256 * 1024, 256 * 1024, threads);

        // export all files
        SalmonFile[] files = drive.getRoot().listFiles();
        IRealFile[] filesExported = commander.exportFiles(files, dir, false, true,
                (taskProgress) ->
                {
                    try {
                        System.out.println("file exporting: " + taskProgress.getFile().getBaseName() + ": "
                                + taskProgress.getProcessedBytes() + "/" + taskProgress.getTotalBytes() + " bytes");
                    } catch (Exception e) {
                        System.err.println(e);
                    }
                }, IRealFile.autoRename, (sfile, ex) ->
                {
                    // file failed to import
                });
        System.out.println("Files exported");

        // close the file commander
        commander.close();
    }

    public static void listFiles(SalmonDrive drive) throws IOException {
        // query for the file from the drive
        SalmonFile root = drive.getRoot();
        SalmonFile[] files = root.listFiles();
        System.out.println("directory listing:");
        for (SalmonFile sfile : files) {
            System.out.println("file: " + sfile.getBaseName() + ", size: " + sfile.getSize());
        }

        // to read you can use file.getInputStream() to get a low level RandomAccessStream
        // or use a ReadableStream wrapper with parallel threads and caching, see below:
        SalmonFile file = files[0]; // pick the first file
        System.out.println("reading file: " + file.getBaseName());
        int buffers = 4;
        int bufferSize = 4 * 1024 * 1024;
        int bufferThreads = 1;
        int backOffset = 256 * 1024; // optional, use for Media consumption
        SalmonFileInputStream inputStream = new SalmonFileInputStream(file,
                buffers, bufferSize, bufferThreads, backOffset);
        byte[] buffer = new byte[256 * 1024];
        int totalBytesRead = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer, 0, buffer.length)) > 0) {
            // do whatever you want with the data...
            totalBytesRead += bytesRead;
        }
        System.out.println("bytes read: " + totalBytesRead);
        inputStream.close();
    }

    public static void closeDrive(SalmonDrive drive) {
        // close the drive
        drive.close();
        System.out.println("drive closed");
    }

    public static SalmonFileSequencer createSequencer() throws IOException {
        // create a file nonce sequencer and place it in a private space
        // make sure you never edit or back up this file.
        String seqFilename = "sequencer.xml";
        IRealFile privateDir = new JavaFile(System.getenv("LOCALAPPDATA"));

        IRealFile sequencerDir = privateDir.getChild("sequencer");
        if (!sequencerDir.exists())
            sequencerDir.mkdir();
        IRealFile sequenceFile = sequencerDir.getChild(seqFilename);
        SalmonFileSequencer fileSequencer = new SalmonFileSequencer(sequenceFile, new SalmonSequenceSerializer());
        return fileSequencer;
    }
}