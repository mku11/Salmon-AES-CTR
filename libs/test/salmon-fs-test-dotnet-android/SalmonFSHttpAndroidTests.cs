/*
MIT License

Copyright (c) 2025 Max Kas

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

using Microsoft.VisualStudio.TestTools.UnitTesting;
using Mku.Android.FS.File;
using Mku.Salmon.Sequence;
using Mku.Salmon.Streams;
using System;
using System.Collections.Generic;
using System.Reflection;

namespace Mku.Salmon.Test;

public class SalmonFSHttpAndroidTests
{
    public MainActivity Activity { get; set; }
    private static bool initialized;


    public void BeforeAll(Action<string> log)
    {
        SalmonFSTestHelper.sequenceSerializer = new SequenceSerializer();
        SalmonFSTestHelper.ENABLE_FILE_PROGRESS = true;

        if (initialized)
            return;
        initialized = true;

        AndroidFileSystem.Initialize(Activity);
        string testDir = SalmonFSAndroidTestHelper.GetTestDir(Activity);
        if (testDir == null)
            throw new Exception("Set a test dir in the device before running tests");

        // use TestMode: Http only
        TestMode testMode = TestMode.Http;
        int threads = System.Environment.GetEnvironmentVariable("ENC_THREADS") != null
            && !System.Environment.GetEnvironmentVariable("ENC_THREADS").Equals("") ?
            int.Parse(System.Environment.GetEnvironmentVariable("ENC_THREADS")) : 1;

        SalmonFSAndroidTestHelper.SetTestParams(Activity, testDir, testMode);

        log("testDir: " + testDir);
        log("testMode: " + testMode);
        log("threads: " + threads);
        log("http server url: " + SalmonFSTestHelper.HTTP_SERVER_URL);
        log("HTTP_VAULT_DIR_URL: " + SalmonFSTestHelper.HTTP_VAULT_DIR_URL);

        SalmonFSTestHelper.TEST_HTTP_FILE = SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE;

        // SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        // SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
        SalmonCoreTestHelper.TEST_ENC_THREADS = threads;
        SalmonCoreTestHelper.TEST_DEC_THREADS = threads;

        SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_IMPORT_THREADS = threads;
        SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_EXPORT_THREADS = threads;

        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = threads;
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = true;

        SalmonCoreTestHelper.Initialize();
        SalmonFSTestHelper.Initialize();

        // for remote drive make sure you turn on the web service either manually
        // or start the test case from gradle:
        // gradlew.bat :salmon-ws:test --tests "com.mku.salmon.ws.fs.service.test.SalmonWSTests.testStartServer" --rerun-tasks -i

        // use the native library
        ProviderType providerType = ProviderType.Default;
        String aesProviderType = System.Environment.GetEnvironmentVariable("AES_PROVIDER_TYPE");
        log("ProviderTypeEnv: " + aesProviderType);
        if (aesProviderType != null && !aesProviderType.Equals(""))
            providerType = (ProviderType)System.Enum.Parse(typeof(ProviderType), aesProviderType);
        log("ProviderType: " + providerType);

        AesStream.AesProviderType = providerType;
    }

    public void AfterAll()
    {
        SalmonFSTestHelper.Close();
        SalmonCoreTestHelper.Close();
        initialized = false;
    }
	
	public void Run(Action<string> log) {
		
        MethodInfo[] methods = typeof(SalmonFSHttpTests).GetMethods(BindingFlags.Public | BindingFlags.Instance);
        List<MethodInfo> testMethods = new List<MethodInfo>();
        foreach (MethodInfo method in methods)
        {
            foreach (CustomAttributeData attr in method.CustomAttributes)
            {
                if (attr.AttributeType == typeof(TestMethodAttribute))
                {
                    testMethods.Add(method);
                    break;
                }
            }
        }

        SalmonFSHttpTests salmonFSHttpTests = new SalmonFSHttpTests();
        int passed = 0;
        long tstart = Time.Time.CurrentTimeMillis();
        foreach (MethodInfo method in testMethods)
        {
            //if (method.Name != "ShouldReadFromFileLarge")
            //    continue;    
            log("Running: " + method.Name);
            try
            {
                long start = Time.Time.CurrentTimeMillis();
                method.Invoke(salmonFSHttpTests, null);
                long end = Time.Time.CurrentTimeMillis();
                passed++;
                log(method.Name + ": Passed");
                log("Time: " + (end - start) + " ms");
            }
            catch (System.Exception ex)
            {
                log(method.Name + ": Failed");
                log(ex.ToString());
                Console.Error.WriteLine(ex);
            }
        }
        long tend = Time.Time.CurrentTimeMillis();
        log("Test cases passed: " + passed + "/" + testMethods.Count);
        log("Total Time: " + (tend - tstart) + " ms");

    }

}
