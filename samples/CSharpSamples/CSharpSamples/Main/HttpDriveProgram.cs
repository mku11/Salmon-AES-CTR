using Mku.FS.File;
using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;
using Mku.SalmonFS.Drive;
using File = Mku.FS.File.File;

namespace Mku.Salmon.Samples.Main;

class HttpDriveProgram
{
    public static void RunMain(string[] args)
    {
        string httpDriveURL = "http://localhost:8000/test/httpserv/vault";
        string password = "test123";
        int threads = 1;
		
		AesStream.AesProviderType = ProviderType.Default;

        IFile dir = new File("./output");
        if (!dir.Exists)
            dir.Mkdir();
        IFile exportDir = dir.GetChild("export");
        if (!exportDir.Exists)
            exportDir.Mkdir();

        HttpFile httpDir = new HttpFile(httpDriveURL);
        AesDrive httpDrive = DriveSample.OpenDrive(httpDir, password);
        DriveSample.ListFiles(httpDrive);
        DriveSample.ExportFiles(httpDrive, exportDir, threads);
        DriveSample.CloseDrive(httpDrive);
    }
}