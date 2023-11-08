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

using Mku.SalmonFS;
using System;
using System.Collections.Generic;
using System.Linq;

namespace Mku.Utils;
/// <summary>
///  Class searches for files in a SalmonDrive by filename.
/// </summary>
public class SalmonFileSearcher
{
    private bool running;
    private bool quit;

    /// <summary>
    ///  Event status types.
    /// </summary>
    public enum SearchEvent
    {
        SearchingFiles, SearchingFinished
    }

    public void Stop()
    {
        this.quit = true;
    }

    /// <summary>
    ///  Fired when search result found.
    /// </summary>
    ///  <param name="searchResult">The search result.</param>
    public delegate void OnResultFoundListener(SalmonFile searchResult);

    /// <summary>
    ///  True if a search is running.
	/// </summary>
	///  <returns></returns>
    public bool IsRunning()
    {
        return running;
    }

    /// <summary>
    ///  True if last search was stopped by user.
	/// </summary>
	///  <returns></returns>
    public bool IsStopped()
    {
        return quit;
    }

    /// <summary>
    ///  Search files in directory and its subdirectories recursively for matching terms.
	/// </summary>
	///  <param name="dir">The directory to start the search.</param>
    ///  <param name="terms">The terms to search for.</param>
    ///  <param name="any">True if you want to match any term otherwise match all terms.</param>
    ///  <param name="OnResultFound">Callback interface to receive notifications when results found.</param>
    ///  <param name="onSearchEvent">Callback interface to receive status events.</param>
    ///  <returns>An array with all the results found.</returns>
    public SalmonFile[] Search(SalmonFile dir, string terms, bool any,
                               OnResultFoundListener OnResultFound,
                               Action<SearchEvent> onSearchEvent)
    {
        running = true;
        this.quit = false;
        Dictionary<string, SalmonFile> searchResults = new Dictionary<string, SalmonFile>();
        if (onSearchEvent != null)
            onSearchEvent(SearchEvent.SearchingFiles);
        SearchDir(dir, terms, any, OnResultFound, searchResults);
        if (onSearchEvent != null)
            onSearchEvent(SearchEvent.SearchingFinished);
        running = false;
        return searchResults.Values.ToArray();
    }

    /// <summary>
    ///  Match the current terms in the filename.
	/// </summary>
	///  <param name="filename">The filename to match.</param>
    ///  <param name="terms">The terms to match.</param>
    ///  <param name="any">True if you want to match any term otherwise match all terms.</param>
    ///  <returns>A count of all matches.</returns>
    private int GetSearchResults(string filename, string[] terms, bool any)
    {
        int count = 0;
        foreach (string term in terms)
        {
            try
            {
                if (term.Length > 0 && filename.ToLower().Contains(term.ToLower()))
                {
                    count++;
                }
            }
            catch (Exception exception)
            {
                Console.Error.WriteLine(exception);
            }
        }
        if (any || count == terms.Length)
        {
            return count;
        }
        return 0;
    }

    /// <summary>
    ///  Search a directory for all filenames matching the terms supplied.
	/// </summary>
	///  <param name="dir">The directory to start the search.</param>
    ///  <param name="terms">The terms to search for.</param>
    ///  <param name="any">True if you want to match any term otherwise match all terms.</param>
    ///  <param name="OnResultFound">Callback interface to receive notifications when results found.</param>
    ///  <param name="searchResults">The array to store the search results.</param>
    private void SearchDir(SalmonFile dir, string terms, bool any, OnResultFoundListener OnResultFound,
                           Dictionary<string, SalmonFile> searchResults)
    {
        if (quit)
            return;
        SalmonFile[] files = dir.ListFiles();
        string[] termsArray = terms.Split(" ", StringSplitOptions.RemoveEmptyEntries);
        foreach (SalmonFile file in files)
        {
            if (quit)
                break;
            if (file.IsDirectory)
            {
                SearchDir(file, terms, any, OnResultFound, searchResults);
            }
            else
            {
                if (searchResults.ContainsKey(file.RealPath))
                    continue;
                try
                {
                    int hits = GetSearchResults(file.BaseName, termsArray, any);
                    if (hits > 0)
                    {
                        searchResults[file.RealPath] = file;
                        if (OnResultFound != null)
                            OnResultFound(file);
                    }
                }
                catch (Exception ex)
                {
                    throw new Exception("Could not search for file", ex);
                }
            }
        }
    }
}
