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

using Mku.FS.Drive.Utils;
using Mku.FS.File;
using Mku.SalmonFS.File;
using System;

namespace Mku.SalmonFS.Utils;

/// <summary>
/// Import files from the real file system to the drive
/// </summary>
public class SalmonFileImporter : FileImporter
{
    /// <summary>
    /// Construct an importer for importing files to SalmonDrive
    /// </summary>
    /// <param name="bufferSize">The buffer size</param>
    /// <param name="threads">The threads to use</param>
    public SalmonFileImporter(int bufferSize, int threads)
    {
        base.Initialize(bufferSize, threads);
    }

    /// <summary>
    /// Prepare the files before import
    /// </summary>
    /// <param name="importedFile">The imported file</param>
    /// <param name="integrity">True if apply integrity</param>
    override
    protected void OnPrepare(IVirtualFile importedFile, bool integrity)
    {
        ((SalmonFile)importedFile).AllowOverwrite = true;
        // we use default chunk file size
        ((SalmonFile)importedFile).SetApplyIntegrity(integrity, null, null);
    }

    /// <summary>
    /// Get the minimum required part size for splitting a virtual file
    /// </summary>
    /// <param name="file">The virtual file</param>
    /// <returns>The part size</returns>
    override
    protected long GetMinimumPartSize(IVirtualFile file)
    {
        return ((SalmonFile)file).GetMinimumPartSize();
    }


    /// <summary>
    ///  Imports a real file into the drive.
    /// </summary>
    ///  <param name="fileToImport">The source file that will be imported in to the drive.</param>
    ///  <param name="dir">         The target directory in the drive that the file will be imported</param>
    ///  <param name="filename">The filename to use</param>
    ///  <param name="deleteSource">If true delete the source file.</param>
    ///  <param name="integrity">True to enable integrity</param>
    ///  <param name="OnProgress">    Progress observer</param>
    ///  <returns>The SalmonFile that was imported</returns>
    override
    public SalmonFile ImportFile(IRealFile fileToImport, IVirtualFile dir, String filename,
                                   bool deleteSource, bool integrity, Action<long, long> OnProgress)
    {
        return (SalmonFile)base.ImportFile(fileToImport, dir, filename, deleteSource, integrity, OnProgress);
    }
}