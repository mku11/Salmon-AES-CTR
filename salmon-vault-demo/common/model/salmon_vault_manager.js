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

import { IPropertyNotifier } from "../../common/binding/iproperty_notifier.js";
import { SalmonConfig } from "../../vault/config/salmon_config.js";
import { SalmonSettings } from "../../common/model/salmon_settings.js";
import { SalmonFileCommander } from "../../lib/salmon-fs/salmon/utils/salmon_file_commander.js";
import { autoRenameFile as SalmonFileAutoRename } from "../../lib/salmon-fs/salmon/salmon_file.js";
import { SalmonDrive } from "../../lib/salmon-fs/salmon/salmon_drive.js";
import { SalmonDialog } from "../../vault/dialog/salmon_dialog.js";
import { SalmonDialogs } from "../dialog/salmon_dialogs.js";
import { autoRenameFile as IRealFileAutoRename } from "../../lib/salmon-fs/file/ireal_file.js";
import { JsFile } from "../../lib/salmon-fs/file/js_file.js";
import { JsDrive } from "../../lib/salmon-fs/salmon/drive/js_drive.js";
import { JsLocalStorageFile } from "../../lib/salmon-fs/file/js_ls_file.js";
import { SalmonFileSequencer } from "../../lib/salmon-fs/salmon/sequence/salmon_file_sequencer.js";
import { SalmonSequenceSerializer } from "../../lib/salmon-fs/salmon/sequence/salmon_sequence_serializer.js";
import { SalmonAuthException } from "../../lib/salmon-fs/salmon/salmon_auth_exception.js";
import { ByteUtils } from "../../common/utils/byte_utils.js";

export class SalmonVaultManager extends IPropertyNotifier {
    static SEQUENCER_DIR_NAME = ".salmon";
    static SERVICE_PIPE_NAME = "SalmonService";

    static bufferSize = 512 * 1024;
    static threads = 2;

    static REQUEST_OPEN_VAULT_DIR = 1000;
    static REQUEST_CREATE_VAULT_DIR = 1001;
    static REQUEST_IMPORT_FILES = 1002;
    static REQUEST_EXPORT_DIR = 1003;
    static REQUEST_IMPORT_AUTH_FILE = 1004;
    static REQUEST_EXPORT_AUTH_FILE = 1005;

    sequencerDefaultDirPath = SalmonConfig.getPrivateDir() + JsFile.separator + SalmonVaultManager.SEQUENCER_DIR_NAME;
    observers = {};

    promptExitOnBack = false;
    drive = null;

    async getExportDir(self) {
        if (self == null)
            self == this;
        return await self.drive.getExportDir();
    }

    getDrive() {
        return this.drive;
    }

    getSequencerDefaultDirPath() {
        return this.sequencerDefaultDirPath;
    }

    setSequencerDefaultDirPath(value) {
        this.sequencerDefaultDirPath = value;
    }

    getSequencerFilepath() {
        return this.sequencerDefaultDirPath + JsFile.separator
            + SalmonConfig.FILE_SEQ_FILENAME;
    }

    openListItem = null;
    updateListItem = null;
    onFileItemAdded = null;
    sequencer = null;
    static instance = null;

    static getInstance() {
        if (SalmonVaultManager.instance == null) {
            SalmonVaultManager.instance = new SalmonVaultManager();
        }
        return SalmonVaultManager.instance;
    }

    static getBufferSize() {
        return SalmonVaultManager.bufferSize;
    }

    static setBufferSize(bufferSize) {
        SalmonVaultManager.bufferSize = bufferSize;
    }

    static getThreads() {
        return SalmonVaultManager.threads;
    }

    static setThreads(threads) {
        SalmonVaultManager.threads = threads;
    }

    fileItemList = null;

    getFileItemList() {
        return this.fileItemList;
    }

    setFileItemList(value) {
        if (this.fileItemList != value) {
            this.fileItemList = value;
            this.propertyChanged(this, "FileItemList");
        }
    }

    selectedFiles = new Set();

    getSelectedFiles() {
        return this.selectedFiles;
    }

    setSelectedFiles(value) {
        if (value != this.selectedFiles) {
            this.selectedFiles = value;
            this.propertyChanged(this, "SelectedFiles");
        }
    }

    _currentItem = null;

    getCurrentItem() {
        return this._currentItem;
    }

    setCurrentItem(value) {
        if (value != this._currentItem) {
            this._currentItem = value;
            this.propertyChanged(this, "CurrentItem");
        }
    }

    status = "";

    getStatus() {
        return this.status;
    }

    setStatus(value) {
        if (value != this.status) {
            this.status = value;
            this.propertyChanged(this, "Status");
        }
    }

    #isJobRunning = false;

    isJobRunning() {
        return this.#isJobRunning;
    }

    setJobRunning(value) {
        if (value != this.#isJobRunning) {
            this.#isJobRunning = value;
            this.propertyChanged(this, "IsJobRunning");
        }
    }

    path = null;

    getPath() {
        return this.path;
    }

    setPath(value) {
        if (value != this.path) {
            this.path = value;
            this.propertyChanged(this, "Path");
        }
    }

    fileProgress = 0;

    getFileProgress() {
        return this.fileProgress;
    }

    setFileProgress(value) {
        if (value != this.fileProgress) {
            this.fileProgress = value;
            this.propertyChanged(this, "FileProgress");
        }
    }

    filesProgress = 0;

    getFilesProgress() {
        return this.filesProgress;
    }

    setFilesProgress(value) {
        if (value != this.filesProgress) {
            this.filesProgress = value;
            this.propertyChanged(this, "FilesProgress");
        }
    }

    currDir = null;

    getCurrDir() {
        return this.currDir;
    }

    fileCommander = null;
    copyFiles = null;
    salmonFiles = null;
    searchTerm;
    fileManagerMode = SalmonVaultManager.Mode.Browse;

    getFileManagerMode() {
        return this.fileManagerMode;
    }

    constructor() {
        super();
        SalmonSettings.getInstance().load();
        this.setupFileCommander();
        this.setupSalmonManager();
    }

    async initialize() {
        
    }

    onOpenItem(selectedItem) {
        try {
            let selectedFile = this.fileItemList[selectedItem];
            return this.openItem(selectedFile);
        } catch (e) {
            console.error(e);
        }
        return false;
    }

    setPathText(value) {
        if (value.startsWith("/"))
            value = value.substring(1);
        this.setPath("fs://" + value);
    }

    stopOperation() {
        this.fileCommander.cancel();
        this.fileManagerMode = SalmonVaultManager.Mode.Browse;
        this.setTaskRunning(false);
    }

    copySelectedFiles() {
        if(this.selectedFiles.size == 0)
            return;
        this.fileManagerMode = SalmonVaultManager.Mode.Copy;
        this.copyFiles = Array.from(this.selectedFiles);
        this.setTaskRunning(true, false);
        this.setTaskMessage(this.copyFiles.length + " Items selected for copy");
    }

    cutSelectedFiles() {
        if(this.selectedFiles.size == 0)
            return;
        this.fileManagerMode = SalmonVaultManager.Mode.Move;
        this.copyFiles = Array.from(this.selectedFiles);
        this.setTaskRunning(true, false);
        this.setTaskMessage(this.copyFiles.length + " Items selected for move");
    }

    setupFileCommander() {
        this.fileCommander = new SalmonFileCommander(SalmonVaultManager.bufferSize, SalmonVaultManager.bufferSize, SalmonVaultManager.threads);
    }

    async refresh() {
        if (this.checkFileSearcher())
            return;
        if (this.drive == null)
            return;
            setTimeout(async () => {
                if (this.fileManagerMode != SalmonVaultManager.Mode.Search)
                    this.salmonFiles = await this.currDir.listFiles();
                let selectedFile = this.selectedFiles.size > 1 ? this.selectedFiles.values().next().value : null;
                this.populateFileList(selectedFile);
            });
    }

    checkFileSearcher() {
        if (this.fileCommander.isFileSearcherRunning()) {
            SalmonDialogs.promptAnotherProcessRunning();
            return true;
        }
        return false;
    }

    populateFileList(currentFile) {
        setTimeout(async () => {
            this.selectedFiles.clear();
            try {
                if (this.fileManagerMode == SalmonVaultManager.Mode.Search)
                    this.setPathText(await this.currDir.getPath() + "?search=" + this.searchTerm);
                else
                    this.setPathText(await this.currDir.getPath());
            } catch (exception) {
                console.error(exception);
                SalmonDialog.promptDialog("Error", exception);
            }

            let list = [];
            for (let file of this.salmonFiles) {
                try {
                    list.push(file);
                } catch (e) {
                    console.error(e);
                }
            }
            this.setFileItemList(list);
            let currFile = this.findCurrentItem(currentFile);
            this.setCurrentItem(currFile);
        });
    }

    setupSalmonManager() {
        try {
            this.setupFileSequencer();
        } catch (e) {
            console.error(e);
            SalmonDialog.promptDialog("Error", "Error during initializing: " + e);
        }
    }

    setupFileSequencer() {
        if (this.sequencer != null)
            this.sequencer.close();
        let seqFile = new JsLocalStorageFile(this.getSequencerFilepath());
        this.sequencer = new SalmonFileSequencer(seqFile, this.createSerializer());
    }

    createSerializer() {
        return new SalmonSequenceSerializer();
    }

    pasteSelected() {
        this.#copySelectedFiles(this.fileManagerMode == SalmonVaultManager.Mode.Move);
    }

    setTaskRunning(value, progress = true) {
        if (progress)
            this.setJobRunning(value);
    }

    setTaskMessage(msg) {
        this.setStatus(msg != null ? msg : "");
    }

    async openVault(dir, password) {
        if (dir == null)
            return;

        try {
            this.closeVault();
            this.drive = await SalmonDrive.openDrive(dir, JsDrive, password, this.sequencer);
            this.currDir = await this.drive.getRoot();
            SalmonSettings.getInstance().setVaultLocation(dir.getAbsolutePath());
        } catch (e) {
            console.error(e);
            SalmonDialog.promptDialog("Error", "Could not open vault: " + e);
        }
        await this.refresh();
    }

    deleteSelectedFiles() {
        this.deleteFiles(Array.from(this.selectedFiles));
        this.clearSelectedFiles();
    }

    #copySelectedFiles(move) {
        this.#copyFiles(this.copyFiles, this.currDir, move);
        this.clearSelectedFiles();
    }

    deleteFiles(files) {
        if (files == null)
            return;
        setTimeout(async () => {
            this.setFileProgress(0);
            this.setFilesProgress(0);
            this.setTaskRunning(true);

            let exception = null;
            let processedFiles = [-1];
            let failedFiles = [];
            try {
                await this.fileCommander.deleteFiles(files,
                    async (taskProgress) => {
                        if (processedFiles[0] < taskProgress.getProcessedFiles()) {
                            try {
                                if (taskProgress.getProcessedBytes() != taskProgress.getTotalBytes()) {
                                    this.setTaskMessage("Deleting: " + await taskProgress.getFile().getBaseName()
                                        + " " + (taskProgress.getProcessedFiles() + 1) + "/" + taskProgress.getTotalFiles());
                                }
                            } catch (e) {
                                console.error(e);
                            }
                            processedFiles[0] = taskProgress.getProcessedFiles();
                        }
                        this.setFileProgress(taskProgress.getProcessedBytes() / taskProgress.getTotalBytes());
                        this.setFilesProgress(taskProgress.getProcessedFiles() / taskProgress.getTotalFiles());
                    }, (file, ex) => {
                        failedFiles.push(file);
                        exception = ex;
                    });
            } catch (e) {
                if (!this.fileCommander.areJobsStopped()) {
                    console.error(e);
                    SalmonDialog.promptDialog("Error", "Could not delete files: " + e, "Ok");
                }
            }
            if (this.fileCommander.areJobsStopped())
                this.setTaskMessage("Delete Stopped");
            else if (failedFiles.length > 0)
                SalmonDialog.promptDialog("Delete", "Some files failed: " + exception);
            else
                this.setTaskMessage("Delete Complete");
            this.setFileProgress(1);
            this.setFilesProgress(1);
            await this.refresh();
            this.setTaskRunning(false);
            this.copyFiles = null;
            this.fileManagerMode = SalmonVaultManager.Mode.Browse;
        });
    }

    #copyFiles(files, dir, move) {
        if (files == null)
            return;
        setTimeout(async () => {
            this.setFileProgress(0);
            this.setFilesProgress(0);
            this.setTaskRunning(true);

            let action = move ? "Moving" : "Copying";
            let exception = null;
            let processedFiles = [-1];
            let failedFiles = [];
            try {
                await this.fileCommander.copyFiles(files, dir, move,
                    async (taskProgress) => {
                        if (processedFiles[0] < taskProgress.getProcessedFiles()) {
                            try {
                                this.setTaskMessage(action + ": " + await taskProgress.getFile().getBaseName()
                                    + " " + (taskProgress.getProcessedFiles() + 1) + "/" + taskProgress.getTotalFiles());
                            } catch (e) {
                                console.error(e);
                            }
                            processedFiles[0] = taskProgress.getProcessedFiles();
                        }
                        this.setFileProgress(taskProgress.getProcessedBytes() / taskProgress.getTotalBytes());
                        this.setFilesProgress(taskProgress.getProcessedFiles() / taskProgress.getTotalFiles());
                    }, SalmonFileAutoRename, true, (file, ex) => {
                        this.handleThrowException(ex);
                        failedFiles.push(file);
                        exception = ex;
                    });
            } catch (e) {
                if (!this.fileCommander.areJobsStopped()) {
                    console.error(e);
                    SalmonDialog.promptDialog("Error", "Could not copy files: " + e, "Ok");
                }
            }
            if (this.fileCommander.areJobsStopped())
                this.setTaskMessage(action + " Stopped");
            else if (failedFiles.length > 0)
                SalmonDialog.promptDialog(action, "Some files failed: " + exception);
            else
                this.setTaskMessage(action + " Complete");
            this.setFileProgress(1);
            this.setFilesProgress(1);
            this.setTaskRunning(false);
            await this.refresh();
            this.copyFiles = null;
            this.fileManagerMode = SalmonVaultManager.Mode.Browse;
        });
    }

    async exportSelectedFiles(deleteSource) {
        if (this.drive == null)
            return;
        await this.exportFiles(Array.from(this.selectedFiles), async (files) => {
            await this.refresh();
        }, deleteSource);
        this.clearSelectedFiles();
    }

    clearSelectedFiles() {
        this.setSelectedFiles(new Set());
    }

    handleException(exception) {
        return false;
    }

    closeVault() {
        try {
            this.setFileItemList(null);
            this.currDir = null;
            this.clearCopiedFiles();
            this.setPathText("");
            if (this.drive != null)
                this.drive.close();
        } catch (ex) {
            console.error(ex);
        }
    }

    async openItem(selectedFile) {
        let position = this.fileItemList.indexOf(selectedFile);
        if (position < 0)
            return true;
        if (await selectedFile.isDirectory()) {
            setTimeout(async () => {
                if (this.checkFileSearcher())
                    return;
                this.currDir = selectedFile;
                this.salmonFiles = await this.currDir.listFiles();
                this.populateFileList(null);
            });
            return true;
        }
        let item = this.fileItemList[position];
        return await this.openListItem(item);
    }

    async goBack() {
        if (this.fileManagerMode == SalmonVaultManager.Mode.Search && this.fileCommander.isFileSearcherRunning()) {
            this.fileCommander.stopFileSearch();
        } else if (this.fileManagerMode == SalmonVaultManager.Mode.Search) {
            setTimeout(async () => {
                this.fileManagerMode = SalmonVaultManager.Mode.Browse;
                this.salmonFiles = await this.currDir.listFiles();
                this.populateFileList(null);
            });
        } else if (await this.canGoBack()) {
            let finalParent = await this.currDir.getParent();
            setTimeout(async () => {
                if (this.checkFileSearcher())
                    return;
                let parentDir = this.currDir;
                this.currDir = finalParent;
                this.salmonFiles = await this.currDir.listFiles();
                this.populateFileList(parentDir);
            });
        } else if (this.promptExitOnBack) {
            SalmonDialogs.promptExit();
        }
    }

    findCurrentItem(currentFile) {
        if (currentFile == null)
            return null;
        for (let file of this.fileItemList) {
            if (file.getRealFile().getPath() == currentFile.getRealFile().getPath()) {
                this.selectedFiles.clear();
                this.selectedFiles.add(file);
                return file;
            }
        }
        return null;
    }

    getObservers() {
        return this.observers;
    }

    async renameFile(file, newFilename) {
        if(await file.getBaseName() == newFilename)
            return;
        await this.fileCommander.renameFile(file, newFilename);
    }

    static Mode = {
        Browse: 'Browse',
        Search: 'Search',
        Copy: 'Copy',
        Move: 'Move'
    }

    exportFiles(items, onFinished, deleteSource) {
        setTimeout(async () => {
            this.setFileProgress(0);
            this.setFilesProgress(0);
            this.setTaskRunning(true);

            let exception = null;
            let processedFiles = [-1];
            let files = null;
            let failedFiles = [];
            let exportDir = await this.getExportDir();
            try {
                files = await this.fileCommander.exportFiles(items,
                    exportDir,
                    deleteSource, true,
                    async (taskProgress) => {
                        if (processedFiles[0] < taskProgress.getProcessedFiles()) {
                            try {
                                this.setTaskMessage("Exporting: " + await taskProgress.getFile().getBaseName()
                                    + " " + (taskProgress.getProcessedFiles() + 1)
                                    + "/" + taskProgress.getTotalFiles());
                            } catch (e) {
                                console.error(e);
                            }
                            processedFiles[0] = taskProgress.getProcessedFiles();
                        }
                        this.setFileProgress(taskProgress.getProcessedBytes() / taskProgress.getTotalBytes());
                        this.setFilesProgress(taskProgress.getProcessedFiles() / taskProgress.getTotalFiles());
                    }, IRealFileAutoRename, (file, ex) => {
                        failedFiles.push(file);
                        exception = ex;
                    });
                if (onFinished != null)
                    onFinished(files);
            } catch (e) {
                console.error(e);
                SalmonDialog.promptDialog("Error", "Error while exporting files: " + e);
            }
            if (this.fileCommander.areJobsStopped())
                this.setTaskMessage("Export Stopped");
            else if (failedFiles.length > 0)
                SalmonDialog.promptDialog("Export", "Some files failed: " + exception);
            else if (files != null) {
                this.setTaskMessage("Export Complete");
                SalmonDialog.promptDialog("Export", "Files Exported To: " + exportDir.getAbsolutePath());
            }
            this.setFileProgress(1);
            this.setFilesProgress(1);

            this.setTaskRunning(false);
        });
    }

    importFiles(fileNames, importDir, deleteSource, onFinished) {
        setTimeout(async () => {
            this.setFileProgress(0);
            this.setFilesProgress(0);
            this.setTaskRunning(true);

            let exception = null;
            let processedFiles = [-1];
            let files = null;
            let failedFiles = [];
            try {
                files = await this.fileCommander.importFiles(fileNames, importDir,
                    deleteSource, true,
                    async (taskProgress) => {
                        if (processedFiles[0] < taskProgress.getProcessedFiles()) {
                            try {
                                this.setTaskMessage("Importing: " + await taskProgress.getFile().getBaseName()
                                    + " " + (taskProgress.getProcessedFiles() + 1) + "/" + taskProgress.getTotalFiles());
                            } catch (e) {
                                console.error(e);
                            }
                            processedFiles[0] = taskProgress.getProcessedFiles();
                        }
                        this.setFileProgress(taskProgress.getProcessedBytes() / taskProgress.getTotalBytes());
                        this.setFilesProgress(taskProgress.getProcessedFiles() / taskProgress.getTotalFiles());
                    }, IRealFileAutoRename, (file, ex) => {
                        this.handleThrowException(ex);
                        failedFiles.push(file);
                        exception = ex;
                    });
                onFinished(files);
            } catch (e) {
                console.error(e);
                if (!this.handleException(e)) {
                    SalmonDialog.promptDialog("Error", "Error while importing files: " + e);
                }
            }
            if (this.fileCommander.areJobsStopped())
                this.setTaskMessage("Import Stopped");
            else if (failedFiles.length > 0)
                SalmonDialog.promptDialog("Import", "Some files failed: " + exception);
            else if (files != null)
                this.setTaskMessage("Import Complete");
            this.setFileProgress(1);
            this.setFilesProgress(1);
            this.setTaskRunning(false);
        });
    }

    handleThrowException(ex) {
    }

    search(value, any) {
        this.searchTerm = value;
        if (this.checkFileSearcher())
            return;
        setTimeout(async () => {
            this.fileManagerMode = SalmonVaultManager.Mode.Search;
            this.setFileProgress(0);
            this.setFilesProgress(0);
            try {
                if (await this.currDir.getPath() != null)
                    this.setPathText(await this.currDir.getPath() + "?search=" + this.searchTerm);
            } catch (e) {
                console.error(e);
            }
            this.salmonFiles = [];
            this.populateFileList(null);
            this.setTaskRunning(true);
            this.setStatus("Searching");
            this.salmonFiles = await this.fileCommander.search(this.currDir, value, any, (salmonFile) => {
                let position = 0;
                for (let file of this.fileItemList) {
                    if (salmonFile.getTag() != null &&
                        (file.getTag() == null || salmonFile.getTag() > file.getTag()))
                        break;
                    position++;
                }
                try {
                    this.fileItemList[position] = salmonFile;
                    this.onFileItemAdded(position, salmonFile);
                } catch (e) {
                    console.error(e);
                }
            }, null);
            if (!this.fileCommander.isFileSearcherStopped())
                this.setStatus("Search Complete");
            else
                this.setStatus("Search Stopped");
            this.setTaskRunning(false);
        });
    }

    async createVault(dir, password) {
        this.drive = await SalmonDrive.createDrive(dir, JsDrive, password, this.sequencer);
        this.currDir = await this.drive.getRoot();
        SalmonSettings.getInstance().setVaultLocation(dir.getAbsolutePath());
        await this.refresh();
    }

    clearCopiedFiles() {
        this.copyFiles = null;
        this.fileManagerMode = SalmonVaultManager.Mode.Browse;
        this.setTaskRunning(false, false);
        this.setTaskMessage("");
    }

    async getFileProperties(item) {
        let fileChunkSize = await item.getFileChunkSize();
        return "Name: " + await item.getBaseName() + "\n" +
            "Path: " + await item.getPath() + "\n" +
            (!await item.isDirectory() ? ("Size: " + ByteUtils.getBytes(await item.getSize(), 2)
                + " (" + await item.getSize() + " bytes)") : "Items: " + (await item.listFiles()).length) + "\n" +
            "Encrypted name: " + item.getRealFile().getBaseName() + "\n" +
            "Encrypted path: " + item.getRealFile().getAbsolutePath() + "\n" +
            (!await item.isDirectory() ? "Encrypted size: " + ByteUtils.getBytes(item.getRealFile().length(), 2)
                + " (" + await item.getRealFile().length() + " bytes)" : "") + "\n" +
            "Integrity chunk size: " + (fileChunkSize == 0 ? "None" : fileChunkSize) + "\n";
    }

    async canGoBack() {
        return this.currDir != null && await this.currDir.getParent() != null;
    }

    setPromptExitOnBack(promptExitOnBack) {
        this.promptExitOnBack = promptExitOnBack;
    }

}