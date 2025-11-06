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

import { IVirtualFile } from "../../file/ivirtual_file.js";

/**
 * Class searches for files in a SalmonDrive by filename.
 */
export class FileSearcher {
    #running: boolean = false;
    #quit: boolean = false;

    public stop(): void {
        this.#quit = true;
    }

    /**
     * True if a search is running.
     * @returns {boolean} True if running
     */
    public isRunning(): boolean {
        return this.#running;
    }

    /**
     * True if last search was stopped by user.
     * @returns {boolean} True if stopped.
     */
    public isStopped(): boolean {
        return this.#quit;
    }

    /**
     * Search files in directory and its subdirectories recursively for matching terms.
     * @param {IVirtualFile} dir The directory to start the search.
     * @param {string} terms The terms to search for.
     * @param {SearchOptions} [options] The options
     * @returns {Promise<IVirtualFile[]>} An array with all the results found.
     */
    public async search(dir: IVirtualFile, terms: string, options?: SearchOptions): Promise<IVirtualFile[]> {
        if(!options)
            options = new SearchOptions();
        this.#running = true;
        this.#quit = false;
        let searchResults: Map<string,IVirtualFile> = new Map<string,IVirtualFile>();
        if (options.onSearchEvent)
            options.onSearchEvent(SearchEvent.SearchingFiles);
        await this.#searchDir(dir, terms, options.anyTerm, options.onResultFound, searchResults);
        if (options.onSearchEvent)
            options.onSearchEvent(SearchEvent.SearchingFinished);
        this.#running = false;
        return Array.from(searchResults.values());
    }

    /**
     * Match the current terms in the filename.
     * @param {string} filename The filename to match.
     * @param {string} terms The terms to match.
     * @param {boolean} any True if you want to match any term otherwise match all terms.
     * @returns {number} A count of all matches.
     */
    #getSearchResults(filename: string, terms: string[], any: boolean): number {
        let count: number = 0;
        for (let term of terms) {
            try {
                if (term.length > 0 && filename.toLowerCase().includes(term.toLowerCase())) {
                    count++;
                }
            } catch (exception) {
                console.error(exception);
            }
        }
        if (any || count == terms.length) {
            return count;
        }
        return 0;
    }

    /**
     * Search a directory for all filenames matching the terms supplied.
     */
    async #searchDir(dir: IVirtualFile, terms: string, any: boolean,
        onResultFound: ((file: IVirtualFile) => void) | null,
        searchResults: Map<string,IVirtualFile>): Promise<void> {
        if (this.#quit)
            return;
        let files: IVirtualFile[] = await dir.listFiles();
        let termsArray: string[] = terms.split(" ");
        for (let i = 0; i < files.length; i++) {
            let file: IVirtualFile = files[i];
            if (this.#quit)
                break;
            if (await file.isDirectory()) {
                await this.#searchDir(file, terms, any, onResultFound, searchResults);
            } else {
                if (file.getRealPath() in searchResults)
                    continue;
                try {
                    let hits: number = this.#getSearchResults(await file.getName(), termsArray, any);
                    if (hits > 0) {
                        searchResults.set(file.getRealPath(), file);
                        if (onResultFound)
                            onResultFound(file);
                    }
                } catch (ex) {
                    console.error(ex);
                }
            }
        }
    }
}

/**
 * Event status types.
 */
export enum SearchEvent {
    /**
     * Search files
     */
    SearchingFiles,
    /**
     * Search is complete
     */ 
    SearchingFinished
}

/**
 * Search options
 */
export class SearchOptions {
    /**
     * True to search for any term, otherwise match all
     */
    anyTerm: boolean = false;

    /**
     * Callback when result found
     */
    onResultFound: ((searchResult: IVirtualFile) => void) | null = null;

    /**
     * Callback when search event happens.
     */
    onSearchEvent: ((event: SearchEvent) => void) | null = null;
}
