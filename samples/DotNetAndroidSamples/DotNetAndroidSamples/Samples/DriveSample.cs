using Mku.Android.FS.File;
using Mku.Android.SalmonFS.Drive;
using Mku.FS.File;
using Mku.Salmon.Sequence;
using Mku.SalmonFS.Drive;
using Mku.SalmonFS.Drive.Utils;
using Mku.SalmonFS.File;
using Mku.SalmonFS.Sequence;
using Mku.SalmonFS.Streams;
using System;

namespace Mku.Salmon.Samples.Samples;

public class DriveSample
{
    static FileSequencer sequencer;

    static DriveSample()
    {
        sequencer = DriveSample.CreateSequencer();
    }

    public static AesDrive CreateDrive(IFile vaultDir, string password,
                                       Action<string> log)
    {
        // create a drive
        AesDrive drive = null;
        if (vaultDir.GetType() == typeof(File))
        { // local
            drive = Drive.Create(vaultDir, password, sequencer);
        }
        else if (vaultDir.GetType() == typeof(AndroidFile))
        { // Android local
            drive = AndroidDrive.Create(vaultDir, password, sequencer);
        }
        else if (vaultDir.GetType() == typeof(WSFile))
        { // web service
            drive = WSDrive.Create(vaultDir, password, sequencer);
        }
        log("drive created: " + drive.RealRoot.DisplayPath);
        return drive;
    }

    public static AesDrive OpenDrive(IFile vaultDir, string password,
                                     Action<string> log)
    {
        // open a drive
        AesDrive drive = null;
        if (vaultDir.GetType() == typeof(File))
        { // local
            drive = Drive.Open(vaultDir, password, sequencer);
        }
        else if (vaultDir.GetType() == typeof(AndroidFile))
        { // Android local
            drive = AndroidDrive.Open(vaultDir, password, sequencer);
        }
        else if (vaultDir.GetType() == typeof(WSFile))
        { // web service
            drive = WSDrive.Open(vaultDir, password, sequencer);
        }
        else if (vaultDir.GetType() == typeof(HttpFile))
        { // http (Read-only)
            drive = HttpDrive.Open(vaultDir, password);
        }
        if (drive != null)
            log("drive opened: " + drive.RealRoot.DisplayPath);
        return drive;
    }

    public static void ImportFiles(AesDrive drive, IFile[] filesToImport, Action<string> log)
    {
        ImportFiles(drive, filesToImport, 1, log);
    }

    public static void ImportFiles(AesDrive drive, IFile[] filesToImport, int threads,
                                   Action<string> log)
    {
        AesFileCommander commander = new AesFileCommander(256 * 1024, 256 * 1024, threads);


        // import multiple files
        AesFileCommander.BatchImportOptions importOptions = new AesFileCommander.BatchImportOptions();
        importOptions.integrity = true;
        importOptions.autoRename = IFile.AutoRename;
        importOptions.onFailed = (file, ex) =>
        {
            Console.Error.WriteLine(ex);
            log("import failed: " + ex);
        };
        importOptions.onProgressChanged = (taskProgress) =>
        {
            log("file importing: "
                    + taskProgress.File.Name + ": "
                    + taskProgress.ProcessedBytes + "/"
                    + taskProgress.TotalBytes + " bytes");
        };
        AesFile[] filesImported = commander.ImportFiles(filesToImport, drive.Root, importOptions);
        log("Files imported");

        // close the file commander
        commander.Close();
    }

    public static void ExportFiles(AesDrive drive, IFile dir, Action<string> log)
    {
        ExportFiles(drive, dir, 1, log);
    }

    public static void ExportFiles(AesDrive drive, IFile dir, int threads,
                                   Action<string> log)
    {
        AesFileCommander commander = new AesFileCommander(256 * 1024, 256 * 1024, threads);

        // export all files
        AesFile[] files = drive.Root.ListFiles();
        AesFileCommander.BatchExportOptions exportOptions = new AesFileCommander.BatchExportOptions();
        exportOptions.integrity = true;
        exportOptions.autoRename = IFile.AutoRename;
        exportOptions.onFailed = (file, ex) =>
        {
            Console.Error.WriteLine(ex);
            log("export failed: " + ex);
        };
        exportOptions.onProgressChanged = (taskProgress) =>
        {
            try
            {
                log("file exporting: "
                        + taskProgress.File.Name + ": "
                        + taskProgress.ProcessedBytes + "/"
                        + taskProgress.TotalBytes + " bytes");
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
                log(ex.Message);
            }
        };
        IFile[] filesExported = commander.ExportFiles(files, dir, exportOptions);
        log("Files exported");

        // close the file commander
        commander.Close();
    }

    public static void ListFiles(AesDrive drive, Action<string> log)
    {
        // query for the file from the drive
        AesFile root = drive.Root;
        AesFile[] files = root.ListFiles();
        if (files.Length == 0)
        {
            log("No files found");
            return;
        }
        log("directory listing:");
        foreach (AesFile sfile in files)
        {
            log("file: " + sfile.Name + ", size: " + sfile.Length);
        }

        // for reading files you have the following options:
        // a) file.GetInputStream() to get a low level RandomAccessStream
        // b) wrap the RandomAccessStream in a BufferedIOWrapper (a native .NET Stream with aligned buffers)
        // c) wrap the RandomAccessStream in an AesFileInputStream (a native .NET Stream with aligned buffers and multithreading)
        AesFile file = files[0]; // pick the first file
        log("reading file: " + file.Name);
        int buffers = 4;
        int bufferSize = 4 * 1024 * 1024;
        int bufferThreads = 1;
        int backOffset = 256 * 1024; // optional, use for Media consumption
        AesFileInputStream inputStream = new AesFileInputStream(file,
                buffers, bufferSize, bufferThreads, backOffset);
        byte[] buffer = new byte[256 * 1024];
        int totalBytesRead = 0;
        int bytesRead;
        while ((bytesRead = inputStream.Read(buffer, 0, buffer.Length)) > 0)
        {
            // do whatever you want with the data...
            totalBytesRead += bytesRead;
        }
        log("bytes read: " + totalBytesRead);
        inputStream.Close();
    }

    public static void CloseDrive(AesDrive drive, Action<string> log)
    {
        // close the drive
        drive.Close();
        log("drive closed");
    }

    public static FileSequencer CreateSequencer()
    {
        // create a file nonce sequencer and place it in a private space
        // make sure you never edit or back up this file.
        string seqFilename = "sample_sequencer.xml";

        IFile privateDir = new File(AndroidFileSystem.GetContext().FilesDir.Path);
        IFile sequencerDir = privateDir.GetChild("sequencer");
        if (!sequencerDir.Exists)
            sequencerDir.Mkdir();
        IFile sequenceFile = sequencerDir.GetChild(seqFilename);
        FileSequencer fileSequencer = new FileSequencer(sequenceFile, new SequenceSerializer());
        return fileSequencer;
    }
}
