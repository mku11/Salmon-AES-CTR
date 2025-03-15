using Mku.FS.File;
using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;
using Mku.SalmonFS.Drive;
using File = Mku.FS.File.File;

namespace Mku.Salmon.Samples.Main;

class LocalDriveProgram
{
    public static void RunMain(string[] args)
    {
        string password = "test123";
        int threads = 2;

        AesStream.AesProviderType = ProviderType.Default;

        // directories and files
        IFile dir = new File("./output");
        if (!dir.Exists)
            dir.Mkdir();

        // create
        IFile driveDir = dir.GetChild("drive_" + Time.Time.CurrentTimeMillis());
        if (!driveDir.Exists)
            driveDir.Mkdir();
        AesDrive localDrive = DriveSample.CreateDrive(driveDir, password);

        // open
        localDrive = DriveSample.OpenDrive(driveDir, password);

        // import
        IFile[] filesToImport = [new File("./data/file.txt")];
        DriveSample.ImportFiles(localDrive, filesToImport, threads);

        // list
        DriveSample.ListFiles(localDrive);

        // export the files
        IFile exportDir = driveDir.GetChild("export");
        if (!exportDir.Exists)
            exportDir.Mkdir();
        DriveSample.ExportFiles(localDrive, exportDir, threads);

        DriveSample.CloseDrive(localDrive);
    }
}