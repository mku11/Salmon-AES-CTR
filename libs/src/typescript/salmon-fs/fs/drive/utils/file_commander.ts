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

import { IFile, RecursiveMoveOptions } from "../../file/ifile.js";
import { FileImporter, FileImportOptions } from "./file_importer.js";
import { FileExporter, FileExportOptions } from "./file_exporter.js";
import { SearchEvent, FileSearcher, SearchOptions } from "./file_searcher.js";
import { IVirtualFile, VirtualRecursiveCopyOptions, VirtualRecursiveDeleteOptions, VirtualRecursiveMoveOptions } from "../../file/ivirtual_file.js";

/**
 * Facade class for file operations.
 */
export class FileCommander {
    protected fileImporter: FileImporter;
    protected fileExporter: FileExporter;
    protected fileSearcher: FileSearcher;
    private stopJobs: boolean = false;

    /**
     * Instantiate a new file commander object.
     *
     */
    public constructor(fileImporter: FileImporter, fileExporter: FileExporter, fileSearcher: FileSearcher) {
        this.fileImporter = fileImporter;
        this.fileExporter = fileExporter;
        this.fileSearcher = fileSearcher;
    }

    public getFileImporter(): FileImporter {
        return this.fileImporter;
    }

    public getFileExporter(): FileExporter {
        return this.fileExporter;
    }

    /**
     * Import files to the drive.
     *
     * @param {IFile[]} filesToImport     The files to import.
     * @param {IVirtualFile} importDir         The target directory.
     * @param {BatchImportOptions} options The options
     * @return {Promise<IVirtualFile[]>} The imported files if completes successfully.
     * @throws Exception If there was an error during import
     */
    public async importFiles(filesToImport: IFile[], importDir: IVirtualFile, options: BatchImportOptions | null = null): Promise<IVirtualFile[]> {
        if(options == null)
            options = new BatchImportOptions();
        this.stopJobs = false;
        let importedFiles: IVirtualFile[] = [];

        let total: number = 0;
        for (let i = 0; i < filesToImport.length; i++) {
            if (this.stopJobs)
                break;
            total += await this.getRealFilesCountRecursively(filesToImport[i]);
        }

        let count: number[] = [0];
        let existingFiles: { [key: string]: IVirtualFile } = await this.getExistingIVirtualFiles(importDir);
        for (let i = 0; i < filesToImport.length; i++) {
            if (this.stopJobs)
                break;
            await this.importRecursively(filesToImport[i], importDir,
                options.deleteSource, options.integrity,
                options.onProgressChanged, options.autoRename, options.onFailed,
                importedFiles, count, total,
                existingFiles);
        }
        return importedFiles;
    }

    private async getExistingIVirtualFiles(importDir: IVirtualFile): Promise<{ [key: string]: IVirtualFile }> {
        let files: { [key: string]: IVirtualFile } = {};
        let realFiles: IVirtualFile[] = await importDir.listFiles();
        for (let i = 0; i < realFiles.length; i++) {
            if (this.stopJobs)
                break;
            let file: IVirtualFile = realFiles[i];
            try {
                files[await file.getName()] = file;
            } catch (ignored) {
            }
        }
        return files;
    }

    private async importRecursively(fileToImport: IFile, importDir: IVirtualFile,
        deleteSource: boolean, integrity: boolean,
        onProgressChanged: ((progress: RealFileTaskProgress) => void) | null,
        autoRename: ((realFile: IFile) => Promise<string>) | null,
        onFailed: ((realFile: IFile, error: Error | unknown) => void) | null,
        importedFiles: IVirtualFile[], count: number[], total: number,
        existingFiles: { [key: string]: IVirtualFile }): Promise<void> {

        let sfile: IVirtualFile | null = fileToImport.getName() in existingFiles
            ? existingFiles[fileToImport.getName()] : null;
        if (await fileToImport.isDirectory()) {
            if (onProgressChanged != null)
                onProgressChanged(new RealFileTaskProgress(fileToImport, 0, 1, count[0], total));
            if (sfile == null || !await sfile.exists())
                sfile = await importDir.createDirectory(fileToImport.getName());
            else if (sfile != null && await sfile.exists() && await sfile.isFile() && autoRename != null)
                sfile = await importDir.createDirectory(await autoRename(fileToImport));
            if (onProgressChanged != null)
                onProgressChanged(new RealFileTaskProgress(fileToImport, 1, 1, count[0], total));
            count[0]++;
            if (sfile == null)
                throw new Error("Could not get import directory");
            let nExistingFiles: { [key: string]: IVirtualFile } = await this.getExistingIVirtualFiles(sfile);
            let lFiles = await fileToImport.listFiles();
            for (let i = 0; i < lFiles.length; i++) {
                if (this.stopJobs)
                    break;
                let child: IFile = lFiles[i];
                await this.importRecursively(child, sfile, deleteSource, integrity, onProgressChanged,
                    autoRename, onFailed, importedFiles, count, total,
                    nExistingFiles);
            }
            if (deleteSource && !this.stopJobs)
                await fileToImport.delete();
        } else {
            try {
                let filename: string = fileToImport.getName();
                if (sfile != null && (await sfile.exists() || await sfile.isDirectory()) && autoRename != null)
                    filename = await autoRename(fileToImport);
                let importOptions: FileImportOptions = new FileImportOptions();
                importOptions.filename = filename;
                importOptions.deleteSource = deleteSource;
                importOptions.integrity = integrity;
                importOptions.onProgress = (bytes, totalBytes) => {
                    if (onProgressChanged != null) {
                        onProgressChanged(new RealFileTaskProgress(fileToImport,
                            bytes, totalBytes, count[0], total));
                    }
                };
                sfile = await this.fileImporter.importFile(fileToImport, importDir, importOptions);
                if (sfile != null) {
                    existingFiles[await sfile.getName()] = sfile;
                    importedFiles.push(sfile);
                }
                count[0]++;
            } catch (ex) {
                if (!this.onError(ex)) {
                    if (onFailed != null)
                        onFailed(fileToImport, ex);
                }
            }
        }
    }

    /**
     * Export a file from a drive.
     *
     * @param {IVirtualFile[]} filesToExport     The files to export.
     * @param {IFile} exportDir         The export target directory
     * @param {BatchExportOptions | null} options The options
     * @return {Promise<IFile[]>} The exported files
     * @throws Exception
     */
    public async exportFiles(filesToExport: IVirtualFile[], exportDir: IFile, options: BatchExportOptions | null = null): Promise<IFile[]> {
        if(options == null)
            options = new BatchExportOptions();
        this.stopJobs = false;
        let exportedFiles: IFile[] = [];

        let total: number = 0;
        for (let i = 0; i < filesToExport.length; i++) {
            if (this.stopJobs)
                break;
            total += await this.getIVirtualFilesCountRecursively(filesToExport[i]);
        }

        let existingFiles: { [key: string]: IFile } = await this.getExistingRealFiles(exportDir);

        let count: number[] = [0];
        for (let i = 0; i < filesToExport.length; i++) {
            if (this.stopJobs)
                break;
            await this.exportRecursively(filesToExport[i], exportDir,
                options.deleteSource, options.integrity,
                options.onProgressChanged, options.autoRename, options.onFailed,
                exportedFiles, count, total,
                existingFiles);
        }
        return exportedFiles;
    }

    private async getExistingRealFiles(exportDir: IFile): Promise<{ [key: string]: IFile }> {
        let files: { [key: string]: IFile } = {};
        let lFiles: IFile[] = await exportDir.listFiles();
        for (let i = 0; i < lFiles.length; i++) {
            if (this.stopJobs)
                break;
            let file: IFile = lFiles[i]
            files[file.getName()] = file;
        }
        return files;
    }

    private async exportRecursively(fileToExport: IVirtualFile, exportDir: IFile,
        deleteSource: boolean, integrity: boolean,
        onProgressChanged: ((progress: IVirtualFileTaskProgress) => void) | null,
        autoRename: ((file: IFile) => Promise<string>) | null,
        onFailed: ((file: IVirtualFile, error: Error | unknown) => void) | null,
        exportedFiles: IFile[], count: number[], total: number,
        existingFiles: { [key: string]: IFile }): Promise<void> {
        let rfile: IFile | null = await fileToExport.getName() in existingFiles
            ? existingFiles[await fileToExport.getName()] : null;

        if (await fileToExport.isDirectory()) {
            if (rfile == null || !await rfile.exists())
                rfile = await exportDir.createDirectory(await fileToExport.getName());
            else if (rfile != null && await rfile.isFile() && autoRename != null)
                rfile = await exportDir.createDirectory(await autoRename(rfile));
            if (onProgressChanged != null)
                onProgressChanged(new IVirtualFileTaskProgress(fileToExport as IVirtualFile, 1, 1, count[0], total));
            count[0]++;
            let nExistingFiles: { [key: string]: IFile } = await this.getExistingRealFiles(rfile);
            let lFiles: IVirtualFile[] = await fileToExport.listFiles();
            for (let i = 0; i < lFiles.length; i++) {
                if (this.stopJobs)
                    break;
                let child: IVirtualFile = lFiles[i];
                await this.exportRecursively(child, rfile, deleteSource, integrity, onProgressChanged,
                    autoRename, onFailed, exportedFiles, count, total,
                    nExistingFiles);
            }
            if (deleteSource && !this.stopJobs) {
                fileToExport.delete();
            }
        } else {
            try {
                let filename: string = await fileToExport.getName();
                if (rfile != null && await rfile.exists() && autoRename != null)
                    filename = await autoRename(rfile);
                let exportOptions: FileExportOptions = new FileExportOptions();
                exportOptions.filename = filename;
                exportOptions.deleteSource = deleteSource;
                exportOptions.integrity = integrity;
                exportOptions.onProgress = (bytes, totalBytes) => {
                    if (onProgressChanged != null) {
                        onProgressChanged(new IVirtualFileTaskProgress(fileToExport as IVirtualFile,
                            bytes, totalBytes, count[0], total));
                    }
                };
                exportOptions.filename = filename;
                rfile = await this.fileExporter.exportFile(fileToExport as IVirtualFile, exportDir, exportOptions);
                if (rfile != null) {
                    existingFiles[rfile.getName()] = rfile;
                    exportedFiles.push(rfile);
                }
                count[0]++;
            } catch (ex) {
                if (!this.onError(ex)) {
                    if (onFailed != null)
                        onFailed(fileToExport as IVirtualFile, ex);
                }
            }
        }
    }

    private async getIVirtualFilesCountRecursively(file: IVirtualFile): Promise<number> {
        let count: number = 1;
        if (await file.isDirectory()) {
            let lFiles: IVirtualFile[] = await file.listFiles();
            for (let i = 0; i < lFiles.length; i++) {
                if (this.stopJobs)
                    break;
                let child: IVirtualFile = lFiles[i];
                count += await this.getIVirtualFilesCountRecursively(child);
            }
        }
        return count;
    }

    private async getRealFilesCountRecursively(file: IFile): Promise<number> {
        let count: number = 1;
        if (await file.isDirectory()) {
            let lFiles: IFile[] = await file.listFiles();
            for (let i = 0; i < lFiles.length; i++) {
                if (this.stopJobs)
                    break;
                let child: IFile = lFiles[i];
                count += await this.getRealFilesCountRecursively(child);
            }
        }
        return count;
    }

    /**
     * Delete files from a drive.
     *
     * @param {IVirtualFile[]} filesToDelete The files to delete.
     * @param {FileDeleteOptions | null} options The options.
     */
    public async deleteFiles(filesToDelete: IVirtualFile[], options: FileDeleteOptions | null = null): Promise<void> {
        if(options == null)
            options = new FileDeleteOptions();
        this.stopJobs = false;
        let count: number[] = [0];
        let total: number = 0;
        for (let i = 0; i < filesToDelete.length; i++) {
            if (this.stopJobs)
                break;
            total += await this.getIVirtualFilesCountRecursively(filesToDelete[i]);
        }
        for (let i = 0; i < filesToDelete.length; i++) {
            let fileToDelete: IVirtualFile = filesToDelete[i];
            if (this.stopJobs)
                break;
            let finalTotal: number = total;
            let deleteOptions: VirtualRecursiveDeleteOptions = new VirtualRecursiveCopyOptions();
            deleteOptions.onFailed = options.onFailed;
            deleteOptions.onProgressChanged = (file, position, length) => {
                if (this.stopJobs)
                    throw new Error();
                if (options.onProgressChanged != null) {
                    try {
                        options.onProgressChanged(new IVirtualFileTaskProgress(
                            file, position, length, count[0], finalTotal));
                    }
                    catch (ex) {
                    }
                }
                if (position == length)
                    count[0]++;
            }
            await fileToDelete.deleteRecursively(deleteOptions);
        }
    }

    /**
     * Copy files to another directory.
     *
     * @param {IVirtualFile[]} filesToCopy       The array of files to copy.
     * @param {IVirtualFile} dir               The target directory.
     * @param {BatchCopyOptions | null} options The options
     * @throws Exception When a error during copying occurs.
     */
    public async copyFiles(filesToCopy: IVirtualFile[], dir: IVirtualFile, options: BatchCopyOptions | null = null): Promise<void> {
        if(options == null)
            options = new BatchCopyOptions();
        this.stopJobs = false;
        let count: number[] = [0];
        let total: number = 0;
        for (let i = 0; i < filesToCopy.length; i++) {
            if (this.stopJobs)
                break;
            total += await this.getIVirtualFilesCountRecursively(filesToCopy[i]);
        }
        const finalTotal: number = total;
        for (let i = 0; i < filesToCopy.length; i++) {
            if (this.stopJobs)
                break;
            let fileToCopy: IVirtualFile = filesToCopy[i];
            if (dir.getRealFile().getDisplayPath().startsWith(fileToCopy.getRealFile().getDisplayPath()))
                continue;

            if (this.stopJobs)
                break;

            if (options.move) {
                let moveOptions: VirtualRecursiveMoveOptions = new VirtualRecursiveMoveOptions();
                moveOptions.autoRename = options.autoRename;
                moveOptions.autoRenameFolders = options.autoRenameFolders;
                moveOptions.onFailed = options.onFailed;
                moveOptions.onProgressChanged = (file, position, length) => {
                    if (this.stopJobs)
                        throw new Error();
                    if (options.onProgressChanged != null) {
                        try {
                            options.onProgressChanged(new IVirtualFileTaskProgress(
                                file, position, length, count[0], finalTotal));
                        } catch (ex) {
                        }
                    }
                    if (position == length)
                        count[0]++;
                };
                await fileToCopy.moveRecursively(dir, moveOptions);
            } else {
                let copyOptions: VirtualRecursiveCopyOptions = new VirtualRecursiveCopyOptions();
                copyOptions.autoRename = options.autoRename;
                copyOptions.autoRenameFolders = options.autoRenameFolders;
                copyOptions.onFailed = options.onFailed;
                copyOptions.onProgressChanged = (file, position, length) => {
                    if (this.stopJobs)
                        throw new Error();
                    if (options.onProgressChanged != null) {
                        try {
                            options.onProgressChanged(new IVirtualFileTaskProgress(file, position, length, count[0], finalTotal));
                        } catch (ignored) {
                        }
                    }
                    if (position == length)
                        count[0]++;
                };
                await fileToCopy.copyRecursively(dir, copyOptions);
            }
        }
    }

    /**
     * Cancel all jobs.
     */
    public cancel(): void {
        this.stopJobs = true;
        this.fileImporter.stop();
        this.fileExporter.stop();
        this.fileSearcher.stop();
    }

    /**
     * Check if the file search is currently running.
     *
     * @return {boolean} True if the file search is currently running.
     */
    public isFileSearcherRunning(): boolean {
        return this.fileSearcher.isRunning();
    }

    /**
     * Check if jobs are currently running.
     *
     * @return {boolean} True if jobs are currently running.
     */
    public isRunning(): boolean {
        return this.fileSearcher.isRunning() || this.fileImporter.isRunning() || this.fileExporter.isRunning();
    }

    /**
     * Check if file search stopped.
     *
     * @return {boolean} True if file search stopped.
     */
    public isFileSearcherStopped(): boolean {
        return this.fileSearcher.isStopped();
    }

    /**
     * Stop file search.
     */
    public stopFileSearch() {
        this.fileSearcher.stop();
    }

    /**
     * Search
     *
     * @param {IVirtualFile} dir           The directory to start the search.
     * @param {string} terms         The terms to search for.
     * @param {SearchOptions | null} options The options
     * @return {Promise<IVirtualFile[]>} An array with all the results found.
     */
    public async search(dir: IVirtualFile, terms: string, options: SearchOptions | null = null): Promise<IVirtualFile[]> {
        return await this.fileSearcher.search(dir, terms, options);
    }

    /**
     * Check if all jobs are stopped.
     *
     * @return {boolean} True if jobs are stopped
     */
    public areJobsStopped(): boolean {
        return this.stopJobs;
    }

    /**
     * Get number of files recursively for the files provided.
     *
     * @param {IVirtualFile[]} files The files and directories.
     * @return {Promise<number>} Total number of files and files under subdirectories.
     */
    public async getFiles(files: IVirtualFile[]): Promise<number> {
        let total = 0;
        for (let i = 0; i < files.length; i++) {
            if (this.stopJobs)
                break;
            let file: IVirtualFile = files[i];
            total++;
            if (await file.isDirectory()) {
                total += await this.getFiles(await file.listFiles());
            }
        }
        return total;
    }

    /**
     * Close the commander and associated resources.
     */
    public close(): void {
        this.fileImporter.close();
        this.fileExporter.close();
    }

    /**
     * Rename an encrypted file
     * @param {IVirtualFile} ifile The file
     * @param {string} newFilename The new filename
     */
    public async renameFile(ifile: IVirtualFile, newFilename: string): Promise<void> {
        await ifile.rename(newFilename);
    }

    /**
     * Handle the error.
     * @param {Error | unknown | null} ex The error
     * @returns {boolean} True if handled
     */
    onError(ex: Error | unknown | null): boolean {
        return false;
    }
}


/**
 * File task progress.
 */
export class FileTaskProgress {
    private readonly processedBytes: number;
    private readonly totalBytes: number;
    private readonly processedFiles: number;
    private readonly totalFiles: number;

    /**
     * Get the total bytes.
     * @returns The total bytes
     */
    public getTotalBytes(): number {
        return this.totalBytes;
    }

    /**
     * Get the processed bytes
     * @returns {number} The processed bytes
     */
    public getProcessedBytes(): number {
        return this.processedBytes;
    }

    /**
     * Get the processed files
     * @returns {number} The processed files
     */
    public getProcessedFiles(): number {
        return this.processedFiles;
    }

    /**
     * Get the total files
     * @returns {number} The total files
     */
    public getTotalFiles(): number {
        return this.totalFiles;
    }

    /**
     * Construct a file progress
     * @param {number} processedBytes The processed bytes
     * @param {number} totalBytes The total bytes
     * @param {number} processedFiles The processed files
     * @param {number} totalFiles The total files
     */
    public constructor(processedBytes: number, totalBytes: number, processedFiles: number, totalFiles: number) {
        this.processedBytes = processedBytes;
        this.totalBytes = totalBytes;
        this.processedFiles = processedFiles;
        this.totalFiles = totalFiles;
    }
}

/**
 * Virtual file task progress.
 */
export class IVirtualFileTaskProgress extends FileTaskProgress {
    private readonly file: IVirtualFile;

    /**
     * Get the file
     * @returns {IVirtualFile} The virtual file
     */
    public getFile(): IVirtualFile {
        return this.file;
    }

    /**
     * Construct a task progress.
     * @param {IVirtualFile} file 
     * @param {number} processedBytes 
     * @param {number} totalBytes 
     * @param {number} processedFiles 
     * @param {number} totalFiles 
     */
    public constructor(file: IVirtualFile, processedBytes: number, totalBytes: number,
        processedFiles: number, totalFiles: number) {
        super(processedBytes, totalBytes, processedFiles, totalFiles);
        this.file = file;
    }
}

/**
 * Real file task progress.
 */
export class RealFileTaskProgress extends FileTaskProgress {
    /**
     * 
     * @returns {IFile} The real file
     */
    public getFile(): IFile {
        return this.file;
    }

    private readonly file: IFile;

    /**
     * 
     * @param {IFile} file The file
     * @param {number} processedBytes The processed bytes
     * @param {number} totalBytes The total bytes
     * @param {number} processedFiles The processed files
     * @param {number} totalFiles The total files
     */
    public constructor(file: IFile, processedBytes: number, totalBytes: number,
        processedFiles: number, totalFiles: number) {
        super(processedBytes, totalBytes, processedFiles, totalFiles);
        this.file = file;
    }
}

export class FileDeleteOptions {
    /**
     * Callback when delete fails
     */
    onFailed: ((file: IVirtualFile, error: Error | unknown) => void) | null = null;

    /**
     * Callback when progress changes
     */
    onProgressChanged: ((progress: IVirtualFileTaskProgress) => void) | null = null;
}


/**
 * Batch import options
 */
export class BatchImportOptions {
    /**
     * Delete the source file when complete.
     */
    deleteSource: boolean = false;

    /**
     * True to enable integrity
     */
    integrity: boolean = false;

    /**
     * Callback when a file with the same name exists
     */
    autoRename: ((file: IFile) => Promise<string>) | null = null;

    /**
     * Callback when import fails
     */
    onFailed: ((file: IFile, error: Error | unknown) => void) | null = null;

    /**
     * Callback when progress changes
     */
    onProgressChanged: ((progress: RealFileTaskProgress) => void) | null = null;
}

/**
 * Batch export options
 */
export class BatchExportOptions {
    /**
     * Delete the source file when complete.
     */
    deleteSource: boolean = false;

    /**
     * True to enable integrity
     */
    integrity: boolean = false;

    /**
     * Callback when a file with the same name exists
     */
    autoRename: ((file: IFile) => Promise<string>) | null = null;

    /**
     * Callback when import fails
     */
    onFailed: ((file: IVirtualFile, error: Error | unknown) => void) | null = null;

    /**
     * Callback when progress changes
     */
    onProgressChanged: ((progress: IVirtualFileTaskProgress) => void) | null = null;
}

/**
 * Batch copy options
 */
export class BatchCopyOptions {
    /**
     * True to move, false to copy
     */
    move: boolean = false;

    /**
     * Callback when another file with the same name exists.
     */
    autoRename: ((file: IVirtualFile) => Promise<string>) | null = null;

    /**
     * True to autorename folders
     */
    autoRenameFolders: boolean = false;

    /**
     * Callback when copy fails
     */
    onFailed: ((file: IVirtualFile, error: Error | unknown) => void) | null = null;

    /**
     * Callback when progress changes.
     */
    onProgressChanged: ((progress: IVirtualFileTaskProgress) => void) | null = null;
}