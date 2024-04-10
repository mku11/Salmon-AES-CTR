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
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _FileSearcher_instances, _FileSearcher_running, _FileSearcher_quit, _FileSearcher_getSearchResults, _FileSearcher_searchDir;
/**
 * Class searches for files in a SalmonDrive by filename.
 */
export class FileSearcher {
    constructor() {
        _FileSearcher_instances.add(this);
        _FileSearcher_running.set(this, false);
        _FileSearcher_quit.set(this, false);
    }
    stop() {
        __classPrivateFieldSet(this, _FileSearcher_quit, true, "f");
    }
    /**
     * True if a search is running.
     * @return True if running
     */
    isRunning() {
        return __classPrivateFieldGet(this, _FileSearcher_running, "f");
    }
    /**
     * True if last search was stopped by user.
     * @return True if stopped.
     */
    isStopped() {
        return __classPrivateFieldGet(this, _FileSearcher_quit, "f");
    }
    /**
     * Search files in directory and its subdirectories recursively for matching terms.
     * @param dir The directory to start the search.
     * @param terms The terms to search for.
     * @param any True if you want to match any term otherwise match all terms.
     * @param OnResultFound Callback interface to receive notifications when results found.
     * @param onSearchEvent Callback interface to receive status events.
     * @return An array with all the results found.
     */
    async search(dir, terms, any, OnResultFound, onSearchEvent) {
        __classPrivateFieldSet(this, _FileSearcher_running, true, "f");
        __classPrivateFieldSet(this, _FileSearcher_quit, false, "f");
        let searchResults = {};
        if (onSearchEvent != null)
            onSearchEvent(SearchEvent.SearchingFiles);
        await __classPrivateFieldGet(this, _FileSearcher_instances, "m", _FileSearcher_searchDir).call(this, dir, terms, any, OnResultFound, searchResults);
        if (onSearchEvent != null)
            onSearchEvent(SearchEvent.SearchingFinished);
        __classPrivateFieldSet(this, _FileSearcher_running, false, "f");
        return Object.values(searchResults);
    }
}
_FileSearcher_running = new WeakMap(), _FileSearcher_quit = new WeakMap(), _FileSearcher_instances = new WeakSet(), _FileSearcher_getSearchResults = function _FileSearcher_getSearchResults(filename, terms, any) {
    let count = 0;
    for (let term of terms) {
        try {
            if (term.length > 0 && filename.toLowerCase().includes(term.toLowerCase())) {
                count++;
            }
        }
        catch (exception) {
            console.error(exception);
        }
    }
    if (any || count == terms.length) {
        return count;
    }
    return 0;
}, _FileSearcher_searchDir = 
/**
 * Search a directory for all filenames matching the terms supplied.
 * @param dir The directory to start the search.
 * @param terms The terms to search for.
 * @param any True if you want to match any term otherwise match all terms.
 * @param OnResultFound Callback interface to receive notifications when results found.
 * @param searchResults The array to store the search results.
 */
async function _FileSearcher_searchDir(dir, terms, any, OnResultFound, searchResults) {
    if (__classPrivateFieldGet(this, _FileSearcher_quit, "f"))
        return;
    let files = await dir.listFiles();
    let termsArray = terms.split(" ");
    for (let i = 0; i < files.length; i++) {
        let file = files[i];
        if (__classPrivateFieldGet(this, _FileSearcher_quit, "f"))
            break;
        if (await file.isDirectory()) {
            await __classPrivateFieldGet(this, _FileSearcher_instances, "m", _FileSearcher_searchDir).call(this, file, terms, any, OnResultFound, searchResults);
        }
        else {
            if (file.getRealPath() in searchResults)
                continue;
            try {
                let hits = __classPrivateFieldGet(this, _FileSearcher_instances, "m", _FileSearcher_getSearchResults).call(this, await file.getBaseName(), termsArray, any);
                if (hits > 0) {
                    searchResults[file.getRealPath()] = file;
                    if (OnResultFound != null)
                        OnResultFound(file);
                }
            }
            catch (ex) {
                console.error(ex);
            }
        }
    }
};
/**
 * Event status types.
 */
export var SearchEvent;
(function (SearchEvent) {
    /**
     * Search files
     */
    SearchEvent[SearchEvent["SearchingFiles"] = 0] = "SearchingFiles";
    /**
     * Search is complete
     */
    SearchEvent[SearchEvent["SearchingFinished"] = 1] = "SearchingFinished";
})(SearchEvent || (SearchEvent = {}));
