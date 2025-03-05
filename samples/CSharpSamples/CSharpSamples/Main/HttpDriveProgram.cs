using Mku.File;
using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;

namespace Mku.Salmon.Samples.Main;

class HttpDriveProgram
{
    public static void RunMain(string[] args)
    {
        string httpDriveURL = "http://localhost:8000/test/httpserv/vault";
        string password = "test123";
        int threads = 1;
		
		SalmonStream.AesProviderType = ProviderType.Default;

        IRealFile dir = new DotNetFile("./output");
        if (!dir.Exists)
            dir.Mkdir();
        IRealFile exportDir = dir.GetChild("export");
        if (!exportDir.Exists)
            exportDir.Mkdir();

        DotNetHttpFile httpDir = new DotNetHttpFile(httpDriveURL);
        SalmonDrive httpDrive = DriveSample.OpenDrive(httpDir, password);
        DriveSample.ListFiles(httpDrive);
        DriveSample.ExportFiles(httpDrive, exportDir, threads);
        DriveSample.CloseDrive(httpDrive);
    }
}