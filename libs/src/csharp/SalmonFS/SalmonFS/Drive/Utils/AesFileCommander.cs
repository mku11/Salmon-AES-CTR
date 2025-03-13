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

using System;
using System.Linq;
using Mku.SalmonFS.File;
using Mku.FS.File;
using Mku.FS.Drive.Utils;

namespace Mku.SalmonFS.Utils;

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

    /// <summary>
    ///  Import files to the drive.
    /// </summary>
    ///  <param name="filesToImport">The files to import.</param>
    ///  <param name="importDir">The target directory.</param>
    ///  <param name="deleteSource">True if you want to delete the source files.</param>
    ///  <param name="integrity">True to apply integrity to imported files</param>
    ///  <param name="OnProgressChanged">Observer to notify when progress changes.</param>
    ///  <param name="AutoRename">Function to rename file if another file with the same filename exists.</param>
    ///  <param name="OnFailed">Observer to notify when a file fails importing.</param>
    ///  <returns>The imported files.</returns>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    override
    public SalmonFile[] ImportFiles(IRealFile[] filesToImport, IVirtualFile importDir,
        bool deleteSource, bool integrity, Action<RealFileTaskProgress> OnProgressChanged,
                                      Func<IRealFile, string> AutoRename,
                                      Action<IRealFile, Exception> OnFailed)
    {
        IVirtualFile[] files = base.ImportFiles(filesToImport, importDir, deleteSource, integrity, OnProgressChanged,
                AutoRename, OnFailed);
        return files.Cast<SalmonFile>().ToArray();
    }
}
