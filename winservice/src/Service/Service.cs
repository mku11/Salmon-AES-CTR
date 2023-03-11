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
using System.Diagnostics;
using System.ServiceProcess;
using System.Timers;
using System.Runtime.InteropServices;
using System.IO;
using Salmon.FS;
using System;
using Salmon.Net.FS;

namespace SalmonService
{
    public partial class SalmonService : ServiceBase
    {
        private Timer timer;
        private SequenceServer sequenceServer;
        private readonly string PIPE_NAME = "SalmonService";
        private readonly string SEQUENCER_FILENAME = "config.xml";

        public FileStream stream { get; private set; }

        public enum ServiceState
        {
            SERVICE_STOPPED = 0x00000001,
            SERVICE_START_PENDING = 0x00000002,
            SERVICE_STOP_PENDING = 0x00000003,
            SERVICE_RUNNING = 0x00000004,
            SERVICE_CONTINUE_PENDING = 0x00000005,
            SERVICE_PAUSE_PENDING = 0x00000006,
            SERVICE_PAUSED = 0x00000007,
        }

        [StructLayout(LayoutKind.Sequential)]
        public struct ServiceStatus
        {
            public int dwServiceType;
            public ServiceState dwCurrentState;
            public int dwControlsAccepted;
            public int dwWin32ExitCode;
            public int dwServiceSpecificExitCode;
            public int dwCheckPoint;
            public int dwWaitHint;
        };

        [DllImport("advapi32.dll", SetLastError = true)]
        private static extern bool SetServiceStatus(System.IntPtr handle, ref ServiceStatus serviceStatus);

        public SalmonService()
        {
            InitializeComponent();
            if (!EventLog.SourceExists("Salmon"))
            {
                EventLog.CreateEventSource("Salmon", "SalmonLog");
            }
            eventLog1.Source = "SalmonLog";
            eventLog1.Log = "SalmonLog";
        }

        protected override void OnStart(string[] args)
        {
            UpdateStatus(ServiceState.SERVICE_START_PENDING);
            eventLog1.WriteEntry("Service Started");
            CreateSequenceServer();
            SetupTimer();
            UpdateStatus(ServiceState.SERVICE_RUNNING);
        }

        private void CreateSequenceServer()
        {
            IRealFile file = InitFile();
            sequenceServer = new SequenceServer(PIPE_NAME, new FileSequencer(file, new SalmonSequenceParser()),
                (entry) => eventLog1.WriteEntry(entry), (ex)=> { });
            sequenceServer.Start();
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

        private void UpdateStatus(ServiceState state)
        {
            ServiceStatus serviceStatus = new ServiceStatus();
            serviceStatus.dwCurrentState = state;
            serviceStatus.dwWaitHint = 100000;
            SetServiceStatus(ServiceHandle, ref serviceStatus);
        }

        private void SetupTimer()
        {
            timer = new Timer();
            timer.Interval = 60000;
            timer.Elapsed += new ElapsedEventHandler(this.OnTimer);
            timer.Start();
        }

        public void OnTimer(object sender, ElapsedEventArgs args)
        {
            
        }

        protected override void OnStop()
        {
            UpdateStatus(ServiceState.SERVICE_STOP_PENDING);
            eventLog1.WriteEntry("Service Stopped");
            UpdateStatus(ServiceState.SERVICE_STOPPED);
        }

    }
}
