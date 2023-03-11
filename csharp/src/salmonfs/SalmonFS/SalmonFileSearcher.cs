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

using Salmon.FS;
using System;
using System.Collections.Generic;

namespace SalmonFS
{
    public class SalmonFileSearcher {
        private bool running;
        private bool quit;

        public void Stop() {
            this.quit = true;
        }

        public delegate void OnResultFound(SalmonFile file);

        public bool IsRunning() {
            return running;
        }
        public bool IsStopped() {
            return quit;
        }

        public SalmonFile[] Search(SalmonFile dir, string value, bool any, OnResultFound OnResultFound) {
            running = true;
            this.quit = false;
            List<SalmonFile> searchResults = SearchDir(dir, value, any, OnResultFound);
            running = false;
            if (dir.GetDrive() != null)
                dir.GetDrive().SaveCache();
            return searchResults.ToArray();
        }

        private void GetSearchResults(SalmonFile file, string[] terms, bool any) {
            int count = 0;
            foreach (string term in terms) {
                try {
                    if (term.Length > 0 && file.GetBaseName().Contains(term)) {
                        count++;
                    }
                } catch (Exception e) {
                    Console.Error.WriteLine(e);
                }
            }
            if (any || count == terms.Length) {
                file.SetTag(count);
            }
        }

        private List<SalmonFile> SearchDir(SalmonFile dir, string keywords, bool any, OnResultFound OnResultFound) {
            List<SalmonFile> searchResults = new List<SalmonFile>();
            if (quit)
                return searchResults;
            SalmonFile[] files = dir.ListFiles();
            string[] terms = keywords.Split(' ');
            foreach (SalmonFile file in files) {
                if (quit)
                    break;
                if (file.IsDirectory())
                    searchResults.AddRange(SearchDir(file, keywords, any, OnResultFound));
                else {
                    GetSearchResults(file, terms, any);
                    if (file.GetTag() != null && ((int) file.GetTag()) > 0) {
                        searchResults.Add(file);
                        if (OnResultFound != null)
                            OnResultFound.Invoke(file);
                    }
                }
            }
            return searchResults;
        }
    }
}