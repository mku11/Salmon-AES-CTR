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
using System;
using System.Linq;
using Mku.Utils;

namespace Mku.Salmon.Utils;

/// <summary>
///  Facade class for file operations.
/// </summary>
public class SalmonFileCommander : FileCommander
{
    /**
     * Instantiate a new file commander object.
     *
     * @param importBufferSize The buffer size to use for importing files.
     * @param exportBufferSize The buffer size to use for exporting files.
     */
    public SalmonFileCommander(int importBufferSize, int exportBufferSize, int threads)
        : base(new SalmonFileImporter(importBufferSize, threads),
                new SalmonFileExporter(exportBufferSize, threads),
                new FileSearcher())
    {

    } 

    public SalmonFile[] ImportFiles(IRealFile[] filesToImport, IVirtualFile importDir,
        bool deleteSource, bool integrity, Action<RealFileTaskProgress> onProgressChanged,
                                      Func<IRealFile, string> autoRename,
                                      Action<IRealFile, Exception> onFailed)
    {
        IVirtualFile[] files = base.ImportFiles(filesToImport, importDir, deleteSource, integrity, onProgressChanged,
                autoRename, onFailed);
        return files.Cast<SalmonFile>().ToArray();
    }
}
