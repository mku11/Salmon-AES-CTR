using Mku.File;
using Mku.Salmon.Drive;
using Mku.Salmon.Sequence;
using Mku.Salmon.Streams;
using Mku.Salmon.Utils;

namespace Mku.Salmon.Samples.Samples;

class DriveSample
{
    static SalmonFileSequencer sequencer = DriveSample.CreateSequencer();

    public static SalmonDrive CreateDrive(IRealFile vaultDir, string password)
    {
        // create a drive
        SalmonDrive drive = null;
        if (vaultDir is DotNetFile)
        { // local
            drive = DotNetDrive.Create(vaultDir, password, sequencer);
        }
        else if (vaultDir is DotNetWSFile)
        { // web service
            drive = DotNetWSDrive.Create(vaultDir, password, sequencer);
        }
        Console.WriteLine("drive created: " + drive.RealRoot.AbsolutePath);
        return drive;
    }

    public static SalmonDrive OpenDrive(IRealFile vaultDir, string password)
    {
        // open a drive
        SalmonDrive drive = null;
        if (vaultDir is DotNetFile)
        { // local
            drive = DotNetDrive.Open(vaultDir, password, sequencer);
        }
        else if (vaultDir is DotNetWSFile)
        { // web service
            drive = DotNetDrive.Open(vaultDir, password, sequencer);
        }
        else if (vaultDir is DotNetHttpFile)
        { // http (Read-only)
            drive = DotNetHttpDrive.Open(vaultDir, password, sequencer);
        }
        if (drive != null)
            Console.WriteLine("drive opened: " + drive.RealRoot.AbsolutePath);
        return drive;
    }

    public static void ImportFiles(SalmonDrive drive, IRealFile[] filesToImport, int thread = 1)
    {
        SalmonFileCommander commander = new SalmonFileCommander(256 * 1024, 256 * 1024, 2);


        // import multiple files
        SalmonFile[] filesImported = commander.ImportFiles(filesToImport, drive.Root, false, true,
            (taskProgress) =>
            {
                Console.WriteLine("file importing: " + taskProgress.File.BaseName + ": "
                        + taskProgress.ProcessedBytes + "/" + taskProgress.TotalBytes + " bytes");
            }, IRealFile.AutoRename, (file, ex) =>
            {
                // file failed to import
            });
        Console.WriteLine("Files imported");

        // close the file commander
        commander.Close();
    }

    public static void ExportFiles(SalmonDrive drive, IRealFile dir, int threads = 1)
    {
        SalmonFileCommander commander = new SalmonFileCommander(256 * 1024, 256 * 1024, 2);

        // export all files
        SalmonFile[] files = drive.Root.ListFiles();
        IRealFile[] filesExported = commander.ExportFiles(files, dir, false, true,
            (taskProgress) =>
            {
                try
                {
                    Console.WriteLine("file exporting: " + taskProgress.File.BaseName + ": "
                            + taskProgress.ProcessedBytes + "/" + taskProgress.TotalBytes + " bytes");
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                }
            }, IRealFile.AutoRename, (sfile, ex) =>
            {
                // file failed to import
            });
        Console.WriteLine("Files exported");

        // close the file commander
        commander.Close();
    }

    public static void ListFiles(SalmonDrive drive)
    {
        // query for the file from the drive
        SalmonFile root = drive.Root;
        SalmonFile[] files = root.ListFiles();
        Console.WriteLine("directory listing:");
        foreach (SalmonFile sfile in files)
        {
            Console.WriteLine("file: " + sfile.BaseName + ", size: " + sfile.Size);
        }

        // to read you can use file.getInputStream() to get a low level RandomAccessStream
        // or use a ReadableStream wrapper with parallel threads and caching, see below:
        SalmonFile file = files[0]; // pick the first file
        Console.WriteLine("reading file: " + file.BaseName);
        int buffers = 4;
        int bufferSize = 4 * 1024 * 1024;
        int bufferThreads = 1;
        int backOffset = 256 * 1024; // optional, use for Media consumption
        SalmonFileInputStream inputStream = new SalmonFileInputStream(file,
                        buffers, bufferSize, bufferThreads, backOffset);
        byte[] buffer = new byte[256 * 1024];
        int totalBytesRead = 0;
        int bytesRead;
        while ((bytesRead = inputStream.Read(buffer, 0, buffer.Length)) > 0)
        {
            // do whatever you want with the data...
            totalBytesRead += bytesRead;
        }
        Console.WriteLine("bytes read: " + totalBytesRead);
        inputStream.Close();
    }

    public static void CloseDrive(SalmonDrive drive)
    {
        // close the drive
        drive.Close();
        Console.WriteLine("drive closed");
    }

    public static SalmonFileSequencer CreateSequencer()
    {
        // create a file nonce sequencer and place it in a private space
        // make sure you never edit or back up this file.
        string seqFilename = "sequencer.xml";
        IRealFile privateDir = new DotNetFile(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData));

        IRealFile sequencerDir = privateDir.GetChild("sequencer");
        if (!sequencerDir.Exists)
            sequencerDir.Mkdir();
        IRealFile sequenceFile = sequencerDir.GetChild(seqFilename);
        SalmonFileSequencer fileSequencer = new SalmonFileSequencer(sequenceFile, new SalmonSequenceSerializer());
        return fileSequencer;
    }
}