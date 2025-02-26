using Mku.File;
using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;
using static Mku.File.DotNetWSFile;

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
		
		SalmonStream.AesProviderType = ProviderType.Default;

        IRealFile[] filesToImport = [new DotNetFile("./data/file.txt")];

        IRealFile dir = new DotNetFile("./output");
        if (!dir.Exists)
            dir.Mkdir();
        IRealFile exportDir = dir.GetChild("export");
        if (!exportDir.Exists)
            exportDir.Mkdir();

        IRealFile driveDir = new DotNetWSFile(drivePath, wsServicePath, new Credentials(wsUser, wsPassword));
        if (!driveDir.Exists)
            driveDir.Mkdir();

        SalmonDrive wsDrive = DriveSample.CreateDrive(driveDir, password);
        wsDrive = DriveSample.OpenDrive(driveDir, password);
        DriveSample.ImportFiles(wsDrive, filesToImport);
        DriveSample.ListFiles(wsDrive);
        DriveSample.ExportFiles(wsDrive, exportDir);
        DriveSample.CloseDrive(wsDrive);
    }
}