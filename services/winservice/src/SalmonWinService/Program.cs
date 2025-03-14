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
using Microsoft.Extensions.Logging.Configuration;
using Microsoft.Extensions.Logging.EventLog;
using Mku.SalmonWinService.Service;

namespace Mku.SalmonWinService;

public class Program
{
    private static IHost host;

    public static void Main(string[] args)
    {
        IHostBuilder builder = Host.CreateDefaultBuilder(args)
            .UseWindowsService(options =>
            {
                options.ServiceName = "New Salmon Cryptographic Service";
            }).ConfigureServices((context, services) =>
            {
                LoggerProviderOptions.RegisterProviderOptions<
                    EventLogSettings, EventLogLoggerProvider>(services);
                services.AddSingleton<WinService>();
                services.AddHostedService<WindowsBackgroundService>();
            }).ConfigureLogging(logging =>
            {
                // FIXME:
                logging.AddEventSourceLogger();
                logging.AddEventLog(new EventLogSettings()
                {
                    LogName = "Salmon Log",
                    SourceName = "Salmon Service"
                });
            });

        host = builder.Build();
        host.Run();
    }

    public static void Stop()
    {
        host.StopAsync();
    }
}