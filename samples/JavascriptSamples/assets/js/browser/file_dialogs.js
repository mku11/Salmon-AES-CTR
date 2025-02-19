import { JsFile } from '../lib/salmon-fs/file/js_file.js';

export class FileDialogs {
    static async openFile(filename, onFilePicked) {
        let fileHandle = await showOpenFilePicker({
            id: 1,
            mode: "read",
            multiple: false,
            types: [
                {
                    description: "Salmon Files",
                    accept: {
                        "*/*": [".dat"],
                    },
                },
            ],
            excludeAcceptAllOption: true,
        });
        if(!fileHandle)
            return;
        if(fileHandle instanceof Array)
            fileHandle = fileHandle[0];
        const file = new JsFile(fileHandle);
        onFilePicked(file);
    }
    
    static async openFiles(onFilesPicked) {
        const fileHandles = await showOpenFilePicker({
            id: 1,
            mode: "read",
            multiple: true
        });
        if(!fileHandles)
            return;
        let files = [];
        for (let fileHandle of fileHandles) {
            files.push(new JsFile(fileHandle));
        }
        onFilesPicked(files);
    }

    static async openFolder(onFolderPicked) {
        const dirHandle = await showDirectoryPicker({
            id: 2,
            mode: "readwrite",
            multiple: false
        });
        if(!dirHandle)
            return;
        let dir = new JsFile(dirHandle);
        onFolderPicked(dir);
    }
	
	static async saveFile(filename, onFilePicked) {
        let fileHandle = await showSaveFilePicker({
            id: 3,
            mode: "readwrite",
            multiple: false,
            suggestedName: filename
        });
        if(!fileHandle)
            return;
        if(fileHandle instanceof Array)
            fileHandle = fileHandle[0];
        const file = new JsFile(fileHandle);
        onFilePicked(file);
    }
}