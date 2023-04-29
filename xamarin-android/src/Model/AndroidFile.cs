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
using System.IO;
using Android.Provider;
using Android.App;
using Salmon.FS;
using AndroidX.DocumentFile.Provider;
using Android.Content;
using Android.OS;
using System;
using Salmon.Streams;
using Salmon.Droid.Main;

namespace Salmon.Droid.Model
{
    /// <summary>
    /// Implementation of the IRealFile for Android using Storage Access Framework that supports read/write to external SD cards.
    /// This class is used by the AndroidDrive implementation so you can use SalmonFile wrapper transparently
    /// </summary>
    public class AndroidFile : IRealFile
    {
        private readonly Context context;
        private DocumentFile documentFile = null;
		private string basename = null;

        /// <summary>
        /// Construct an AndroidFile wrapper from an Android DocumentFile.
        /// </summary>
        /// <param name="documentFile">The Android DocumentFile that will be associated to</param>
        /// <param name="context">Android Context</param>
        public AndroidFile(DocumentFile documentFile, Context context)
        {
            this.documentFile = documentFile;
            this.context = context;
        }

        public IRealFile CreateDirectory(string dirName)
        {
            DocumentFile dir = documentFile.CreateDirectory(dirName);
            if (dir == null)
                return null;
            AndroidFile newDir = new AndroidFile(dir, Application.Context);
            return newDir;

        }

        public IRealFile CreateFile(string filename)
        {
            DocumentFile doc = documentFile.CreateFile("*/*", filename);
            // for some reason android storage access framework eventhough it supports auto rename
            // somehow it includes the extension. to protect that we temporarily use another extension
            doc.RenameTo(filename + ".dat");
            doc.RenameTo(filename);
            AndroidFile newFile = new AndroidFile(doc, Application.Context);
            return newFile;
        }

        public bool Delete()
        {
            return documentFile.Delete();
        }

        public bool Exists()
        {
            return documentFile.Exists();
        }

        public string GetAbsolutePath()
        {
            try
            {
                string path = Android.Net.Uri.Decode(documentFile.Uri.ToString());
                int index = path.LastIndexOf(":");
                return "/" + path.Substring(index + 1);
            }
            catch (Exception ex)
            {
                return documentFile.Uri.Path;
            }
        }

        public string GetBaseName()
        {
            if (basename != null)
                return basename;

            if (documentFile != null)
            {
                basename = documentFile.Name;
            }
            return basename;
        }

        public Stream GetInputStream()
        {
            AndroidFileStream androidFileStream = new AndroidFileStream(this, "r");
			return androidFileStream;
        }

        public Stream GetOutputStream()
        {
            AndroidFileStream androidFileStream = new AndroidFileStream(this, "rw");
			return androidFileStream;
        }

        public IRealFile GetParent()
        {
            DocumentFile parentDocumentFile = documentFile.ParentFile;
            AndroidFile parent = new AndroidFile(parentDocumentFile, Application.Context);
            return parent;
        }

        public string GetPath()
        {
            return documentFile.Uri.ToString();
        }

        public bool IsDirectory()
        {
            return documentFile.IsDirectory;
        }

        public bool IsFile()
        {
            return documentFile.IsFile;
        }

        public long LastModified()
        {
            return documentFile.LastModified();
        }

        public long Length()
        {
            return documentFile.Length();
        }

        public IRealFile[] ListFiles()
        {
            DocumentFile[] files = documentFile.ListFiles();
            if (files == null)
                return new AndroidFile[0];

            IRealFile[] realFiles = new AndroidFile[files.Length];
            for (int i = 0; i < files.Length; i++)
            {
                realFiles[i] = new AndroidFile(files[i], context);
            }
            return realFiles;
        }
		
		public IRealFile Move(IRealFile newDir, AbsStream.OnProgressChanged progressListener)
        {
            AndroidFile androidDir = (AndroidFile)newDir;
            if (Build.VERSION.SdkInt >= Android.OS.BuildVersionCodes.N)
            {
                DocumentsContract.MoveDocument(SalmonApplication.GetInstance().ApplicationContext.ContentResolver,
                        documentFile.Uri, documentFile.ParentFile.Uri, androidDir.documentFile.Uri);
                return androidDir.GetChild(documentFile.Name);
            }
            else
            {
                return Copy(newDir, progressListener, true);
            }
        }
		
		public IRealFile Copy(IRealFile newDir, AbsStream.OnProgressChanged progressListener)
        {
            return Copy(newDir, progressListener, false);
        }

        private IRealFile Copy(IRealFile newDir, AbsStream.OnProgressChanged progressListener, bool delete)
        {
            if (this.IsDirectory())
            {
                IRealFile dir = newDir.CreateDirectory(GetBaseName());
                foreach (IRealFile ifile in this.ListFiles())
                {
                    ((AndroidFile)ifile).Copy(dir, progressListener, delete);
                }
                if (delete)
                    this.Delete();
                return dir;
            }
            else
            {
                IRealFile newFile = newDir.CreateFile(GetBaseName());
                Stream source = GetInputStream();
                Stream target = newFile.GetOutputStream();
                try
                {
                    source.CopyTo(target);
                }
                catch (Exception ex)
                {
                    newFile.Delete();
                    return null;
                }
                finally
                {
                    source.Close();
                    target.Close();
                    if (delete)
                        this.Delete();
                }
                return newFile;
            }
        }

        public IRealFile GetChild(string filename)
        {
            DocumentFile[] documentFiles = documentFile.ListFiles();
            foreach (DocumentFile documentFile in documentFiles)
            {
                if (documentFile.Name.Equals(filename))
                    return new AndroidFile(documentFile, context);
            }
            return null;
        }

        public bool RenameTo(string newFilename)
        {
            Android.Net.Uri uri = DocumentsContract.RenameDocument(context.ContentResolver, documentFile.Uri, newFilename);
			//TEST: check if the new documentfile can list if it's a dir
            documentFile = DocumentFile.FromSingleUri(context, uri);
            basename = null;
            return true;
        }

        public bool Mkdir()
        {
            IRealFile parent = GetParent();
            if (parent != null)
            {
                IRealFile dir = parent.CreateDirectory(GetBaseName());
                if (dir.Exists() && dir.IsDirectory())
                    return true;
            }
            return false;
        }
		
		public ParcelFileDescriptor GetFileDescriptor(String mode) {
			return SalmonApplication.GetInstance().ApplicationContext.ContentResolver.OpenFileDescriptor(documentFile.Uri, mode);
		}
    }
}