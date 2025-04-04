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

namespace Mku.SalmonFS.Drive.Utils;

/// <summary>
///  Facade class for encrypted file operations in batch.
/// </summary>
public class AesFileCommander : FileCommander
{
    /// <summary>
    /// Instantiate a new file commander object.
    /// </summary>
    /// <param name="importBufferSize">The buffer size to use for importing files.</param>
    /// <param name="exportBufferSize">The buffer size to use for exporting files.</param>
    /// <param name="threads">The number of parallel threads</param>
    public AesFileCommander(int importBufferSize = 0, int exportBufferSize = 0, int threads = 1)
        : base(new AesFileImporter(importBufferSize, threads),
                new AesFileExporter(exportBufferSize, threads),
                new FileSearcher())
    {

    }

    /// <summary>
    ///  Import files to the drive.
    /// </summary>
    ///  <param name="filesToImport">The files to import.</param>
    ///  <param name="importDir">The target directory.</param>
    ///  <param name="options">The options</param>
    ///  <returns>The imported files.</returns>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    override
    public AesFile[] ImportFiles(IFile[] filesToImport, IVirtualFile importDir, BatchImportOptions options = null)
    {
        IVirtualFile[] files = base.ImportFiles(filesToImport, importDir, options);
        return files.Cast<AesFile>().ToArray();
    }
}
