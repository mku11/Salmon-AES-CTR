import { File } from '../lib/salmon-fs/fs/file/file.js';

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
        const file = new File(fileHandle);
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
            files.push(new File(fileHandle));
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
        let dir = new File(dirHandle);
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
        const file = new File(fileHandle);
        onFilePicked(file);
    }
}