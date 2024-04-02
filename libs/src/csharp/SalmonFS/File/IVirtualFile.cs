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
using System.IO;

namespace Mku.File;

/*
 * A virtual file. Read-only operations are included. Since write operations can be implementation
 * specific ie for encryption they can be implemented by extending this class.
 */
public abstract class IVirtualFile
{
    public abstract Stream GetInputStream();
    public abstract Stream GetOutputStream();
    public abstract IVirtualFile[] ListFiles();
    public abstract IVirtualFile GetChild(string filename);
    public abstract bool IsFile { get; }
    public abstract bool IsDirectory { get; }
    public abstract string Path { get; }
    public abstract string RealPath { get; }
    public abstract IRealFile RealFile { get; protected set; }
    public abstract string BaseName { get; }
    public abstract IVirtualFile Parent { get; }
    public abstract void Delete();
    public abstract void Mkdir();
    public abstract long LastDateTimeModified { get; }
    public abstract long Size { get; }
    public abstract bool Exists { get; }
    public abstract IVirtualFile CreateDirectory(string dirName);
    public abstract IVirtualFile CreateDirectory(string dirName, byte[] key, byte[] dirNameNonce);
    public abstract IVirtualFile CreateFile(string realFilename);
    public abstract void Rename(string newFilename);
    public abstract void Rename(string newFilename, byte[] nonce);
    public abstract IVirtualFile Move(IVirtualFile dir, Action<long, long> OnProgressListener);
    public abstract IVirtualFile Copy(IVirtualFile dir, Action<long, long> OnProgressListener);

    public abstract void MoveRecursively(IVirtualFile dest,
                                Action<IVirtualFile, long, long> progressListener,
                                Func<IVirtualFile, string> autoRename,
                                bool autoRenameFolders,
                                Action<IVirtualFile, Exception> onFailed);
    public abstract void CopyRecursively(IVirtualFile dest,
                                Action<IVirtualFile, long, long> progressListener,
                                Func<IVirtualFile, string> autoRename,
                                bool autoRenameFolders,
                                Action<IVirtualFile, Exception> onFailed);

    public abstract void DeleteRecursively(Action<IVirtualFile, long, long> progressListener,
                           Action<IVirtualFile, Exception> onFailed);
}
