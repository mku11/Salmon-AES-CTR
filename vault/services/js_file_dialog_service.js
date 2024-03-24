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

import { IFileDialogService } from "../../common/services/ifile_dialog_service.js";
import { JsFile } from "../../lib/salmon-fs/file/js_file.js";

export class JsFileDialogService extends IFileDialogService {
    stage;
    handlers = {};
    fileHandle;

    constructor() {
        super();
    }

    getCallback(requestCode) {
        return handlers.get(requestCode);
    }

    async openFile(title, filename, filter, initialDirectory, onFilePicked, requestCode) {
        this.handlers[requestCode] = onFilePicked;
        let types = await this.getTypes(filter);
        let fileHandle = await showOpenFilePicker({
            id: requestCode,
            mode: "read",
            types: types,
            multiple: false
        });
        if(fileHandle instanceof Array)
            fileHandle = fileHandle[0];
        let file = new JsFile(fileHandle);
        onFilePicked(file);
    }

    async openFiles(title, filter, initialDirectory, onFilesPicked, requestCode) {
        this.handlers[requestCode] = onFilesPicked;
        let types = await this.getTypes(filter);
        const fileHandles = await showOpenFilePicker({
            id: requestCode,
            mode: "read",
            types: types,
            multiple: true
        });
        let files = [];
        for (let fileHandle of fileHandles) {
            files.push(new JsFile(fileHandle));
        }
        onFilesPicked(files);
    }

    async pickFolder(title, initialDirectory, onFolderPicked, requestCode) {
        this.handlers[requestCode] = onFolderPicked;
        const dirHandle = await showDirectoryPicker({
            id: requestCode,
            mode: "readwrite",
            multiple: false
        });
        let dir = new JsFile(dirHandle);
        onFolderPicked(dir);
    }

    async saveFile(title, filename, filter, initialDirectory, onFilePicked, requestCode) {
        this.handlers[requestCode] = onFilePicked;
        let types = await this.getTypes(filter);
        let fileHandle = await showSaveFilePicker({
            id: requestCode,
            mode: "readwrite",
            types: types,
            multiple: false,
            suggestedName: filename
        });
        if(fileHandle instanceof Array)
            fileHandle = fileHandle[0];
        const file = new JsFile(fileHandle);
        onFilePicked(file);
    }

    async getTypes(filter) {
        let types = undefined;
        if (filter != null) {
            types = [];
            for (const [key, value] of Object.entries(filter)) {
                types.push({
                    description: key,
                    accept: {
                        "*/*": ["." + value]
                    }
                });
            }
        }
        return types;
    }
}