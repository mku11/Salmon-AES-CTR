
using Mku.FS.File;
using Mku.Salmon.Sequence;
using Mku.SalmonFS.Drive;
using Mku.SalmonFS.File;
using Mku.SalmonFS.Sequence;
using Mku.SalmonFS.Streams;
using Mku.SalmonFS.Utils;
using File = Mku.FS.File.File;

namespace Mku.Salmon.Samples.Samples;

class DriveSample
{
    static FileSequencer sequencer = DriveSample.CreateSequencer();

    public static AesDrive CreateDrive(IFile vaultDir, string password)
    {
        // create a drive
        AesDrive drive = null;
        if (vaultDir is File)
        { // local
            drive = Drive.Create(vaultDir, password, sequencer);
        }
        else if (vaultDir is WSFile)
        { // web service
            drive = WSDrive.Create(vaultDir, password, sequencer);
        }
        Console.WriteLine("drive created: " + drive.RealRoot.AbsolutePath);
        return drive;
    }

    public static AesDrive OpenDrive(IFile vaultDir, string password)
    {
        // open a drive
        AesDrive drive = null;
        if (vaultDir is File)
        { // local
            drive = Drive.Open(vaultDir, password, sequencer);
        }
        else if (vaultDir is WSFile)
        { // web service
            drive = Drive.Open(vaultDir, password, sequencer);
        }
        else if (vaultDir is HttpFile)
        { // http (Read-only)
            drive = HttpDrive.Open(vaultDir, password);
        }
        if (drive != null)
            Console.WriteLine("drive opened: " + drive.RealRoot.AbsolutePath);
        return drive;
    }

    public static void ImportFiles(AesDrive drive, IFile[] filesToImport, int threads = 1)
    {
        AesFileCommander commander = new AesFileCommander(256 * 1024, 256 * 1024, threads);


        // import multiple files
        AesFile[] filesImported = commander.ImportFiles(filesToImport, drive.Root, false, true,
            (taskProgress) =>
            {
                Console.WriteLine("file importing: " + taskProgress.File.Name + ": "
                        + taskProgress.ProcessedBytes + "/" + taskProgress.TotalBytes + " bytes");
            }, IFile.AutoRename, (file, ex) =>
            {
                // file failed to import
            });
        Console.WriteLine("Files imported");

        // close the file commander
        commander.Close();
    }

    public static void ExportFiles(AesDrive drive, IFile dir, int threads = 1)
    {
        AesFileCommander commander = new AesFileCommander(256 * 1024, 256 * 1024, threads);

        // export all files
        AesFile[] files = drive.Root.ListFiles();
        IFile[] filesExported = commander.ExportFiles(files, dir, false, true,
            (taskProgress) =>
            {
                try
                {
                    Console.WriteLine("file exporting: " + taskProgress.File.Name + ": "
                            + taskProgress.ProcessedBytes + "/" + taskProgress.TotalBytes + " bytes");
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                }
            }, IFile.AutoRename, (sfile, ex) =>
            {
                // file failed to import
            });
        Console.WriteLine("Files exported");

        // close the file commander
        commander.Close();
    }

    public static void ListFiles(AesDrive drive)
    {
        // query for the file from the drive
        AesFile root = drive.Root;
        AesFile[] files = root.ListFiles();
        Console.WriteLine("directory listing:");
        foreach (AesFile sfile in files)
        {
            Console.WriteLine("file: " + sfile.Name + ", size: " + sfile.Length);
        }

        // to read you can use file.getInputStream() to get a low level RandomAccessStream
        // or use a ReadableStream wrapper with parallel threads and caching, see below:
        AesFile file = files[0]; // pick the first file
        Console.WriteLine("reading file: " + file.Name);
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
        Console.WriteLine("bytes read: " + totalBytesRead);
        inputStream.Close();
    }

    public static void CloseDrive(AesDrive drive)
    {
        // close the drive
        drive.Close();
        Console.WriteLine("drive closed");
    }

    public static FileSequencer CreateSequencer()
    {
        // create a file nonce sequencer and place it in a private space
        // make sure you never edit or back up this file.
        string seqFilename = "sequencer.xml";
        IFile privateDir = new File(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData));

        IFile sequencerDir = privateDir.GetChild("sequencer");
        if (!sequencerDir.Exists)
            sequencerDir.Mkdir();
        IFile sequenceFile = sequencerDir.GetChild(seqFilename);
        FileSequencer fileSequencer = new FileSequencer(sequenceFile, new SequenceSerializer());
        return fileSequencer;
    }
}