package com.mku.salmon.samples.samples;


import com.mku.android.fs.file.AndroidFile;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.android.salmon.sequence.AndroidSequenceSerializer;
import com.mku.android.salmonfs.drive.AndroidDrive;
import com.mku.fs.file.File;
import com.mku.fs.file.HttpFile;
import com.mku.fs.file.IFile;
import com.mku.fs.file.WSFile;
import com.mku.func.Consumer;
import com.mku.salmon.sequence.SequenceSerializer;
import com.mku.salmonfs.drive.AesDrive;
import com.mku.salmonfs.drive.Drive;
import com.mku.salmonfs.drive.HttpDrive;
import com.mku.salmonfs.drive.WSDrive;
import com.mku.salmonfs.drive.utils.AesFileCommander;
import com.mku.salmonfs.file.AesFile;
import com.mku.salmonfs.sequence.FileSequencer;
import com.mku.salmonfs.streams.AesFileInputStream;

import java.io.IOException;

public class DriveSample {
    static FileSequencer sequencer;

    static {
        try {
            sequencer = DriveSample.createSequencer();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static AesDrive createDrive(IFile vaultDir, String password,
                                       Consumer<String> log) throws IOException {
        // create a drive
        AesDrive drive = null;
        if (vaultDir instanceof File) { // local
            drive = Drive.create(vaultDir, password, sequencer);
        } else if (vaultDir instanceof AndroidFile) { // Android local
            drive = AndroidDrive.create(vaultDir, password, sequencer);
        } else if (vaultDir instanceof WSFile) { // web service
            drive = WSDrive.create(vaultDir, password, sequencer);
        }
        log.accept("drive created: " + drive.getRealRoot().getDisplayPath());
        return drive;
    }

    public static AesDrive openDrive(IFile vaultDir, String password,
                                     Consumer<String> log) throws IOException {
        // open a drive
        AesDrive drive = null;
        if (vaultDir instanceof File) { // local
            drive = Drive.open(vaultDir, password, sequencer);
        } else if (vaultDir instanceof AndroidFile) { // Android local
            drive = AndroidDrive.open(vaultDir, password, sequencer);
        } else if (vaultDir instanceof WSFile) { // web service
            drive = WSDrive.open(vaultDir, password, sequencer);
        } else if (vaultDir instanceof HttpFile) { // http (Read-only)
            drive = HttpDrive.open(vaultDir, password);
        }
        if (drive != null)
            log.accept("drive opened: " + drive.getRealRoot().getDisplayPath());
        return drive;
    }

    public static void importFiles(AesDrive drive, IFile[] filesToImport, Consumer<String> log) throws Exception {
        importFiles(drive, filesToImport, 1, log);
    }

    public static void importFiles(AesDrive drive, IFile[] filesToImport, int threads,
                                   Consumer<String> log) throws Exception {
        AesFileCommander commander = new AesFileCommander(256 * 1024, 256 * 1024, threads);


        // import multiple files
        AesFileCommander.BatchImportOptions importOptions = new AesFileCommander.BatchImportOptions();
        importOptions.integrity = true;
        importOptions.autoRename = IFile.autoRename;
        importOptions.onFailed = (file, ex) -> {
            log.accept("import failed: " + ex);
        };
        importOptions.onProgressChanged = (taskProgress) -> {
            log.accept("file importing: "
                    + taskProgress.getFile().getName() + ": "
                    + taskProgress.getProcessedBytes() + "/"
                    + taskProgress.getTotalBytes() + " bytes");
        };
        AesFile[] filesImported = commander.importFiles(filesToImport, drive.getRoot(), importOptions);
        log.accept("Files imported");

        // close the file commander
        commander.close();
    }

    public static void exportFiles(AesDrive drive, IFile dir, Consumer<String> log) throws Exception {
        exportFiles(drive, dir, 1, log);
    }

    public static void exportFiles(AesDrive drive, IFile dir, int threads,
                                   Consumer<String> log) throws Exception {
        AesFileCommander commander = new AesFileCommander(256 * 1024, 256 * 1024, threads);

        // export all files
        AesFile[] files = drive.getRoot().listFiles();
        AesFileCommander.BatchExportOptions exportOptions = new AesFileCommander.BatchExportOptions();
        exportOptions.integrity = true;
        exportOptions.autoRename = IFile.autoRename;
        exportOptions.onFailed = (file, ex) -> {
            System.err.println("export failed: " + ex);
        };
        exportOptions.onProgressChanged = (taskProgress) -> {
            try {
                log.accept("file exporting: "
                        + taskProgress.getFile().getName() + ": "
                        + taskProgress.getProcessedBytes() + "/"
                        + taskProgress.getTotalBytes() + " bytes");
            } catch (Exception ex) {
                ex.printStackTrace();
                log.accept(ex.getMessage());
            }
        };
        IFile[] filesExported = commander.exportFiles(files, dir, exportOptions);
        log.accept("Files exported");

        // close the file commander
        commander.close();
    }

    public static void listFiles(AesDrive drive, Consumer<String> log) throws IOException {
        // query for the file from the drive
        AesFile root = drive.getRoot();
        AesFile[] files = root.listFiles();
        if (files.length == 0) {
            log.accept("No files found");
            return;
        }
        log.accept("directory listing:");
        for (AesFile sfile : files) {
            log.accept("file: " + sfile.getName() + ", size: " + sfile.getLength());
        }

        // to read you can use file.getInputStream() to get a low level RandomAccessStream
        // or use a ReadableStream wrapper with parallel threads and caching, see below:
        AesFile file = files[0]; // pick the first file
        log.accept("reading file: " + file.getName());
        int buffers = 4;
        int bufferSize = 4 * 1024 * 1024;
        int bufferThreads = 1;
        int backOffset = 256 * 1024; // optional, use for Media consumption
        AesFileInputStream inputStream = new AesFileInputStream(file,
                buffers, bufferSize, bufferThreads, backOffset);
        byte[] buffer = new byte[256 * 1024];
        int totalBytesRead = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer, 0, buffer.length)) > 0) {
            // do whatever you want with the data...
            totalBytesRead += bytesRead;
        }
        log.accept("bytes read: " + totalBytesRead);
        inputStream.close();
    }

    public static void closeDrive(AesDrive drive, Consumer<String> log) {
        // close the drive
        drive.close();
        log.accept("drive closed");
    }

    public static FileSequencer createSequencer() throws IOException {
        // create a file nonce sequencer and place it in a private space
        // make sure you never edit or back up this file.
        String seqFilename = "sample_sequencer.xml";

        IFile privateDir = new File(AndroidFileSystem.getContext().getFilesDir().getPath());
        IFile sequencerDir = privateDir.getChild("sequencer");
        if (!sequencerDir.exists())
            sequencerDir.mkdir();
        IFile sequenceFile = sequencerDir.getChild(seqFilename);
        FileSequencer fileSequencer = new FileSequencer(sequenceFile, new AndroidSequenceSerializer());
        return fileSequencer;
    }
}
