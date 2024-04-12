/*
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

using Mku.File;
using Mku.Salmon.Sequence;
using Salmon.Config;
using Salmon.Service.Sequence;
using Salmon.Win.Sequencer;

namespace SalmonWinService;

public class SalmonService
{
    private SequenceServer sequenceServer;
    public static string PIPE_NAME { get; set; } = "SalmonService";
    public static string SEQUENCER_FILENAME { get; set; } = "config.xml";
    public delegate void WriteEntryDelegate(string message, bool error);
    public WriteEntryDelegate WriteEntry;

    public SalmonService()
    {
        CreateSequenceServer();
    }

    private void CreateSequenceServer()
    {
        IRealFile file = InitFile();
        sequenceServer = new SequenceServer(PIPE_NAME, 
		new WinFileSequencer(file, new SalmonSequenceSerializer(), SalmonConfig.REGISTRY_CHKSUM_KEY),
            (entry, error) => WriteLog(entry, error), (ex) => { });
        sequenceServer.Start();
    }


    private void WriteLog(string entry, bool error)
    {
        if (WriteEntry != null)
            WriteEntry(entry, error);
    }

    private IRealFile InitFile()
    {
        string path = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        var dirPath = Path.Combine(path, "Salmon");
        if (!Directory.Exists(dirPath))
        {
            Directory.CreateDirectory(dirPath);
        }
        var filePath = Path.Combine(dirPath, SEQUENCER_FILENAME);
        return new DotNetFile(filePath);
    }
}
