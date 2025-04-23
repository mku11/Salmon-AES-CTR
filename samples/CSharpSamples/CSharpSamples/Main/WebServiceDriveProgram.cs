using Mku.FS.File;
using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;
using Mku.SalmonFS.Drive;
using File = Mku.FS.File.File;
using IFile = Mku.FS.File.IFile;

namespace Mku.Salmon.Samples.Main;

class WebServiceDriveProgram
{
    public static void RunMain(string[] args)
    {
        string wsServicePath = "http://localhost:8080";
        string wsUser = "user";
        string wsPassword = "password";
        string drivePath = "/example_drive_" + Time.Time.CurrentTimeMillis();
        string password = "test123";

		// only for demo purposes, you should be using HTTPS traffic
        HttpSyncClient.AllowClearTextTraffic = true;
		
        AesStream.AesProviderType = ProviderType.Default;

        IFile[] filesToImport = [new File("./data/file.txt")];

        IFile dir = new File("./output");
        if (!dir.Exists)
            dir.Mkdir();
        IFile exportDir = dir.GetChild("export");
        if (!exportDir.Exists)
            exportDir.Mkdir();

        IFile driveDir = new WSFile(drivePath, wsServicePath, new Credentials(wsUser, wsPassword));
        if (!driveDir.Exists)
            driveDir.Mkdir();

        AesDrive wsDrive = DriveSample.CreateDrive(driveDir, password);
        wsDrive = DriveSample.OpenDrive(driveDir, password);
        DriveSample.ImportFiles(wsDrive, filesToImport);
        DriveSample.ListFiles(wsDrive);
        DriveSample.ExportFiles(wsDrive, exportDir);
        DriveSample.CloseDrive(wsDrive);
    }
}