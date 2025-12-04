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
        string httpDriveURL = "https://localhost/testvault";
        string password = "test";
		string httpUser = "user";
        string httpPassword = "password";
        int threads = 1;
		
		Console.WriteLine("Starting Salmon HTTP Sample");
		Console.WriteLine("make sure your HTTP server is up and running to run this sample, see scripts/misc/start_http_server.bat");
		
		// enable only if you're testing with an HTTP server
		// In all other cases you should be using an HTTPS server
        // HttpSyncClient.AllowClearTextTraffic = true;
		
		AesStream.AesProviderType = ProviderType.Default;

        IFile dir = new File("./output");
        if (!dir.Exists)
            dir.Mkdir();
        IFile exportDir = dir.GetChild("export");
        if (!exportDir.Exists)
            exportDir.Mkdir();

        HttpFile httpDir = new HttpFile(httpDriveURL, new Credentials(httpUser, httpPassword));
        AesDrive httpDrive = DriveSample.OpenDrive(httpDir, password);
        DriveSample.ListFiles(httpDrive);
        DriveSample.ExportFiles(httpDrive, exportDir, threads);
        DriveSample.CloseDrive(httpDrive);
    }
}