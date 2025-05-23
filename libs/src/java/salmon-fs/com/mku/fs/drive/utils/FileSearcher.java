package com.mku.fs.drive.utils;
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

import com.mku.fs.file.IVirtualFile;
import com.mku.func.Consumer;

import java.util.HashMap;

/**
 * Class searches for files in an VirtualDrive by filename.
 */
public class FileSearcher {
    private boolean running;
    private boolean quit;

    /**
     * Event status types.
     */
    public enum SearchEvent {
        SearchingFiles, SearchingFinished
    }

    public void stop() {
        this.quit = true;
    }

    /**
     * Check if a search is running.
     *
     * @return True if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Check if last search was stopped by user.
     *
     * @return True if user stopped
     */
    public boolean isStopped() {
        return quit;
    }

    /**
     * Search files in directory and its subdirectories recursively for matching terms.
     *
     * @param dir     The directory to start the search.
     * @param terms   The terms to search for.
     * @param options The options
     * @return An array with all the results found.
     */
    public IVirtualFile[] search(IVirtualFile dir, String terms, SearchOptions options) {
        if (options == null)
            options = new SearchOptions();
        running = true;
        this.quit = false;
        HashMap<String, IVirtualFile> searchResults = new HashMap<>();
        if (options.onSearchEvent != null)
            options.onSearchEvent.accept(SearchEvent.SearchingFiles);
        searchDir(dir, terms, options.anyTerm, options.onResultFound, searchResults);
        if (options.onSearchEvent != null)
            options.onSearchEvent.accept(SearchEvent.SearchingFinished);
        running = false;
        return searchResults.values().toArray(new IVirtualFile[0]);
    }

    /**
     * Match the current terms in the filename.
     *
     * @param filename The filename to match.
     * @param terms    The terms to match.
     * @param any      True if you want to match any term otherwise match all terms.
     * @return A count of all matches.
     */
    private int getSearchResults(String filename, String[] terms, boolean any) {
        int count = 0;
        for (String term : terms) {
            try {
                if (term.length() > 0 && filename.toLowerCase().contains(term.toLowerCase())) {
                    count++;
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        if (any || count == terms.length) {
            return count;
        }
        return 0;
    }

    /**
     * Search a directory for all filenames matching the terms supplied.
     *
     * @param dir           The directory to start the search.
     * @param terms         The terms to search for.
     * @param any           True if you want to match any term otherwise match all terms.
     * @param onResultFound Callback interface to receive notifications when results found.
     * @param searchResults The array to store the search results.
     */
    private void searchDir(IVirtualFile dir, String terms, boolean any,
                           Consumer<IVirtualFile> onResultFound,
                           HashMap<String, IVirtualFile> searchResults) {
        if (quit)
            return;
        IVirtualFile[] files = dir.listFiles();
        String[] termsArray = terms.split(" ");
        for (IVirtualFile file : files) {
            if (quit)
                break;
            if (file.isDirectory()) {
                searchDir(file, terms, any, onResultFound, searchResults);
            } else {
                if (searchResults.containsKey(file.getRealPath()))
                    continue;
                try {
                    int hits = getSearchResults(file.getName(), termsArray, any);
                    if (hits > 0) {
                        searchResults.put(file.getRealPath(), file);
                        if (onResultFound != null)
                            onResultFound.accept(file);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * Search options
     */
    public static class SearchOptions {
        /**
         * True to search for any term, otherwise match all
         */
        public boolean anyTerm = false;

        /**
         * Callback when result found
         */
        public Consumer<IVirtualFile> onResultFound;

        /**
         * Callback when search event happens.
         */
        public Consumer<SearchEvent> onSearchEvent;
    }

}
