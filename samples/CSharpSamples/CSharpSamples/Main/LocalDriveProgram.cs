using Mku.File;
using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;

namespace Mku.Salmon.Samples.Main;

class LocalDriveProgram
{
    public static void RunMain(string[] args)
    {
        string password = "test123";
        int threads = 2;
		
		SalmonStream.AesProviderType = ProviderType.Default;

        // directories and files
        IRealFile dir = new DotNetFile("./output");
        if (!dir.Exists)
            dir.Mkdir();

        // create
        IRealFile driveDir = dir.GetChild("drive_" + Time.Time.CurrentTimeMillis());
        if (!driveDir.Exists)
            driveDir.Mkdir();
        SalmonDrive localDrive = DriveSample.CreateDrive(driveDir, password);

        // open
        localDrive = DriveSample.OpenDrive(driveDir, password);

        // import
        IRealFile[] filesToImport = [new DotNetFile("./data/file.txt")];
        DriveSample.ImportFiles(localDrive, filesToImport, threads);

        // list
        DriveSample.ListFiles(localDrive);

        // export the files
        IRealFile exportDir = driveDir.GetChild("export");
        if (!exportDir.Exists)
            exportDir.Mkdir();
        DriveSample.ExportFiles(localDrive, exportDir, threads);

        DriveSample.CloseDrive(localDrive);
    }
}