/*
MIT License

Copyright (c) 2025 Max Kas

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

import { RandomAccessStream } from '../../../salmon-core/streams/random_access_stream.js';
import { Base64 } from '../../../salmon-core/convert/base64.js';
import { CopyOptions, IFile, MoveOptions } from './ifile.js';
import { WSFileStream } from '../streams/ws_file_stream.js';
import { IOException } from '../../../salmon-core/streams/io_exception.js';

/**
 * Salmon RealFile implementation for Web Service files.
 */
export class WSFile implements IFile {
    static readonly #PATH: string = "path";
    static readonly #DEST_DIR: string = "destDir";
    static readonly #FILENAME: string = "filename";
    /**
     * The directory separator
     */
    public static readonly separator: string = "/";

    #filePath: string;
    #servicePath: string;
	response: any | null;

    public getServicePath(): string | null{
        return this.#servicePath;
    }

    /**
     * Get the web service credentials
     * @returns {Credentials | null } The credentials
     */
    public getCredentials(): Credentials | null {
        return this.#credentials;
    }

    #credentials: Credentials;

    /**
     * Set the web service credentials
     * @param {Credentials} credentials The credentials
     */
    public setCredentials(credentials: Credentials) {
        this.#credentials = credentials;
    }

    /**
     * Instantiate a real file represented by the filepath provided.
     * @param {string} path The filepath.
     */
    public constructor(path: string, servicePath: string, credentials: Credentials) {
        this.#servicePath = servicePath;
        if(!path.startsWith(WSFile.separator))
            path = WSFile.separator + path;
        this.#filePath = path;
        this.#credentials = credentials;
    }

    async #getResponse(): Promise<any> {
		if (this.response == null) {
			let headers = new Headers();
			this.#setDefaultHeaders(headers);
			this.#setServiceAuth(headers);
			let httpResponse: Response | null = null;
			httpResponse = (await fetch(this.#servicePath + "/api/info" 
				+ "?" + WSFile.#PATH + "=" + encodeURIComponent(this.getPath()),
				{method: 'GET', keepalive: true, headers: headers}));
			await this.#checkStatus(httpResponse, 200);
			this.response = await httpResponse.json();
		}
        return this.response;
    }

    /**
     * Create a directory under this directory.
     * @param {string} dirName The name of the new directory.
     * @returns {Promise<IFile>} The newly created directory.
     */
    public async createDirectory(dirName: string): Promise<IFile> {
        let nDirPath: string = this.#getChildPath(dirName);
        let headers: Headers = new Headers();
        this.#setDefaultHeaders(headers);
        this.#setServiceAuth(headers);
        let params: URLSearchParams = new URLSearchParams();
        params.append(WSFile.#PATH, nDirPath);
        let httpResponse: Response | null = null;
        httpResponse = (await fetch(this.#servicePath + "/api/mkdir", 
            { method: 'POST', keepalive: true, body: params, headers: headers }));
        await this.#checkStatus(httpResponse, 200);
        let dir: WSFile = new WSFile(nDirPath, this.#servicePath, this.#credentials);
        return dir;
    }

    /**
     * Create a file under this directory.
     * @param {string} filename The name of the new file.
     * @returns {Promise<IFile>} The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    public async createFile(filename: string): Promise<IFile> {
        let nFilePath: string = this.#getChildPath(filename);
        let headers: Headers = new Headers();
        this.#setDefaultHeaders(headers);
        this.#setServiceAuth(headers);
        let params: URLSearchParams = new URLSearchParams();
        params.append(WSFile.#PATH, nFilePath);
        let httpResponse: Response | null = null;
        httpResponse = (await fetch(this.#servicePath + "/api/create", 
            { method: 'POST', keepalive: true, body: params, headers: headers }));
        await this.#checkStatus(httpResponse, 200);
        let nFile: WSFile = new WSFile(nFilePath, this.#servicePath, this.#credentials);
        return nFile;
    }

    /**
     * Delete this file or directory.
     * @returns {Promise<boolean>} True if deletion is successful.
     */
    public async delete(): Promise<boolean> {
		this.reset();
        if(await this.isDirectory()) {
            let files: IFile[]  = await this.listFiles();
            for (let file of files) {
                let headers: Headers = new Headers();
                this.#setDefaultHeaders(headers);
                this.#setServiceAuth(headers);
                let params: URLSearchParams = new URLSearchParams();
                params.append(WSFile.#PATH, file.getPath());
                let httpResponse: Response | null = null;
                httpResponse = (await fetch(this.#servicePath + "/api/delete", 
                    { method: 'DELETE', keepalive: true, body: params, headers: headers }));
                await this.#checkStatus(httpResponse, 200);
            }
        }

        let headers: Headers = new Headers();
        this.#setDefaultHeaders(headers);
        this.#setServiceAuth(headers);
        let params: URLSearchParams = new URLSearchParams();
        params.append(WSFile.#PATH, this.#filePath);
        let httpResponse: Response | null = null;
        httpResponse = (await fetch(this.#servicePath + "/api/delete", 
            { method: 'DELETE', keepalive: true, body: params, headers: headers }));
        await this.#checkStatus(httpResponse, 200);
		this.reset();
        return true;
    }

    /**
     * True if file or directory exists.
     * @returns {Promise<boolean>} True if exists
     */
    public async exists(): Promise<boolean> {
        return (await this.#getResponse()).present;
    }

    /**
     * Get the path of this file. For javascript this is the same as the absolute filepath.
     * @returns {string} The path
     */
    public getPath(): string {
        return this.#filePath;
    }

    /**
     * Get the display path on the physical disk. For javascript this is the same as the filepath.
     * @returns {string} The display path.
     */
    public getDisplayPath(): string {
        return this.#filePath;
    }

    /**
     * Get the name of this file or directory.
     * @returns {string} The name of this file or directory.
     */
    public getName(): string {
        if (this.#filePath == null)
            throw new Error("Filepath is not assigned");
        let nFilePath = this.#filePath;
        if(nFilePath.endsWith("/"))
            nFilePath = nFilePath.substring(0,nFilePath.length-1);
        let basename: string | undefined = nFilePath.split(WSFile.separator).pop();
        if (basename === undefined)
            throw new Error("Could not get basename");
        if (basename.includes("%")){
            basename = decodeURIComponent(basename);
        }
        return basename;
    }

    /**
     * Get a stream for reading the file.
     * @returns {Promise<RandomAccessStream>} The stream to read from.
     * @throws FileNotFoundException
     */
    public async getInputStream(): Promise<RandomAccessStream> {
		this.reset();
        let fileStream: WSFileStream = new WSFileStream(this, "r");
        return fileStream;
    }

    /**
     * Get a stream for writing to this file.
     * @returns {Promise<RandomAccessStream>} The stream to write to.
     * @throws FileNotFoundException
     */
    public async getOutputStream(): Promise<RandomAccessStream> {
		this.reset();
        let fileStream: WSFileStream = new WSFileStream(this, "rw");
        return fileStream;
    }

    /**
     * Get the parent directory of this file or directory.
     * @returns {Promise<IFile>} The parent directory.
     */
    public async getParent(): Promise<IFile> {
		let path: string = this.#filePath;
		if(path.endsWith(WSFile.separator))
			path = path.slice(0,-1);
        let parentFilePath: string = path.substring(0, path.lastIndexOf(WSFile.separator));
        return new WSFile(parentFilePath, this.#servicePath,this.#credentials);
    }

    /**
     * Check if this is a directory.
     * @returns {Promise<boolean>} True if directory
     */
    public async isDirectory(): Promise<boolean> {
        return (await this.#getResponse()).directory;
    }

    /**
     * Check if this is a file.
     * @returns {Promise<boolean>} True if file
     */
    public async isFile(): Promise<boolean> {
        return (await this.#getResponse()).file;
    }

    /**
     * Get the last modified date on disk.
     * @returns {Promise<number>} The last date modified
     */
    public async getLastDateModified(): Promise<number> {
        return (await this.#getResponse()).lastModified;
    }

    /**
     * Get the size of the file on disk.
     * @returns {Promise<number>} The length
     */
    public async getLength(): Promise<number> {
        return (await this.#getResponse()).length;
    }

    /**
     * Get the count of files and subdirectories
     * @returns {Promise<number>} The number of files and subdirectories
     */
    public async getChildrenCount(): Promise<number> {
        if(await this.isDirectory()) {
            let headers: Headers = new Headers();
            this.#setDefaultHeaders(headers);
            this.#setServiceAuth(headers);
            let httpResponse: Response | null = null;
            httpResponse = (await fetch(this.#servicePath + "/api/list"
                + "?" + WSFile.#PATH + "=" + encodeURIComponent(this.getPath()), 
                { method: 'GET', keepalive: true, headers: headers }));
            await this.#checkStatus(httpResponse, 200);
            let res: number = (await httpResponse.json()).length;
            return res;
        }
        return 0;
    }

    /**
     * List all files under this directory.
     * @returns {Promise<IFile[]>} The list of files and subdirectories
     */
    public async listFiles(): Promise<IFile[]> {
        if(await this.isDirectory()) {
            let headers: Headers = new Headers();
            this.#setDefaultHeaders(headers);
            this.#setServiceAuth(headers);
            let httpResponse: Response | null = null;
            httpResponse = (await fetch(this.#servicePath + "/api/list"
                + "?" + WSFile.#PATH + "=" + encodeURIComponent(this.getPath()),
                { method: 'GET', keepalive: true, headers: headers }));
            await this.#checkStatus(httpResponse, 200);
            let realFiles: WSFile[] = [];
            let realDirs: WSFile[] = [];
            for(let resFile of await httpResponse.json()){
                let file: WSFile = new WSFile(resFile.path, this.#servicePath, this.#credentials);
                if (resFile.directory)
                    realDirs.push(file);
                else
                    realFiles.push(file);
            }
            realDirs.push(...realFiles);
            return realDirs;
        }
        return [];
    }

    /**
     * Move this file or directory under a new directory.
     * @param {IFile} newDir The target directory.
     * @param {MoveOptions} options The options
     * @returns {Promise<IFile>} The moved file. Use this file for subsequent operations instead of the original.
     */
    public async move(newDir: IFile, options?: MoveOptions): Promise<IFile> {
        if(!options)
            options = new MoveOptions();
        let newName = options.newFilename  ? options.newFilename : this.getName();
        if (newDir == null || !newDir.exists())
            throw new IOException("Target directory does not exist");
        let newFile: IFile | null = await newDir.getChild(newName);
        if (newFile  && await newFile.exists())
            throw new IOException("Another file/directory already exists");

        if (await this.isDirectory()) {
            throw new IOException("Could not move directory use IFile moveRecursively() instead");
        } else {
            let headers: Headers = new Headers();
            this.#setDefaultHeaders(headers);
            this.#setServiceAuth(headers);
            let params: URLSearchParams = new URLSearchParams();
            params.append(WSFile.#PATH, this.#filePath);
            params.append(WSFile.#DEST_DIR, newDir.getPath());
            params.append(WSFile.#FILENAME, newName);
            let httpResponse: Response | null = null;
            httpResponse = (await fetch(this.#servicePath + "/api/move", 
                { method: 'PUT', keepalive: true, body: params, headers: headers }));
            await this.#checkStatus(httpResponse, 200);
            newFile = new WSFile((await httpResponse.json()).path, this.#servicePath, this.#credentials);
			this.reset();
            return newFile;
        }
    }

    /**
     * Move this file or directory under a new directory.
     * @param {IFile} newDir    The target directory.
     * @param {CopyOptions} options The options
     * @returns {Promise<IFile>} The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    public async copy(newDir: IFile, options?: CopyOptions): Promise<IFile> {
        if(!options)
            options = new CopyOptions();
        let newName = options.newFilename  ? options.newFilename : this.getName();
        if (newDir == null || !newDir.exists())
            throw new IOException("Target directory does not exists");
        let newFile: IFile | null = await newDir.getChild(newName);
        if (newFile  && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        if (await this.isDirectory()) {
            throw new IOException("Could not copy directory use IFile copyRecursively() instead");
        } else {
            let headers: Headers = new Headers();
            this.#setDefaultHeaders(headers);
            this.#setServiceAuth(headers);
            let params: URLSearchParams = new URLSearchParams();
            params.append(WSFile.#PATH, this.#filePath);
            params.append(WSFile.#DEST_DIR, newDir.getPath());
            params.append(WSFile.#FILENAME, newName);
            let httpResponse: Response | null = null;
            httpResponse = (await fetch(this.#servicePath + "/api/copy", 
                { method: 'POST', keepalive: true, body: params, headers: headers }));
            await this.#checkStatus(httpResponse, 200);
            newFile = new WSFile((await httpResponse.json()).path, this.#servicePath, this.#credentials);
			this.reset();
            return newFile;
        }
    }

    /**
     * Get the file or directory under this directory with the provided name.
     * @param {string} filename The name of the file or directory.
     * @returns {Promise<IFile | null>} The child
     */
    public async getChild(filename: string): Promise<IFile | null> {
        if (await this.isFile())
            return null;
        let nFilepath: string = this.#getChildPath(filename);
        let child: WSFile = new WSFile(nFilepath, this.#servicePath, this.#credentials);
        return child;
    }

    /**
     * Rename the current file or directory.
     * @param {string} newFilename The new name for the file or directory.
     * @returns {Promise<boolean>} True if successfully renamed.
     */
    public async renameTo(newFilename: string): Promise<boolean> {
		this.reset();
        let headers: Headers = new Headers();
        this.#setDefaultHeaders(headers);
        this.#setServiceAuth(headers);
        let params: URLSearchParams = new URLSearchParams();
        params.append(WSFile.#PATH, this.#filePath);
        params.append(WSFile.#FILENAME, newFilename);
        let httpResponse: Response | null = null;
        httpResponse = (await fetch(this.#servicePath + "/api/rename", 
            { method: 'PUT', keepalive: true, body: params, headers: headers }));
        await this.#checkStatus(httpResponse, 200);
        return true;
    }

    /**
     * Create this directory under the current filepath.
     * @returns {Promise<boolean>} True if created.
     */
    public async mkdir(): Promise<boolean> {
		this.reset();
        let headers: Headers = new Headers();
        this.#setDefaultHeaders(headers);
        this.#setServiceAuth(headers);
        let params: URLSearchParams = new URLSearchParams();
        params.append(WSFile.#PATH, this.#filePath);
        let httpResponse: Response | null = null;
        httpResponse = (await fetch(this.#servicePath + "/api/mkdir", 
            { method: 'POST', keepalive: true, body: params, headers: headers }));
        await this.#checkStatus(httpResponse, 200);
        return true;
    }
	
	/**
     * Reset cached properties
     */
    public reset() {
		this.response = null;
	}

    #getChildPath(filename: string) {
        let nFilepath = this.#filePath;
        if(!nFilepath.endsWith(WSFile.separator))
            nFilepath += WSFile.separator;
        nFilepath += filename;
        return nFilepath;
    }
	
    /**
     * Returns a string representation of this object
     * @returns {string} The string
     */
    public toString(): string {
        return this.#filePath;
    }

    #setServiceAuth(headers: Headers) {
        if(!this.#credentials)
            return;
        headers.append('Authorization', 'Basic ' + new Base64().encode(
            new TextEncoder().encode(this.#credentials.getServiceUser() + ":" + this.#credentials.getServicePassword())));
    }
    
    async #checkStatus(httpResponse: Response, status: number) {
        if (httpResponse.status != status)
            throw new IOException(httpResponse.status
                    + " " + httpResponse.statusText + "\n"
                    + await httpResponse.text());
    }

    #setDefaultHeaders(headers: Headers) {
        headers.append("Cache", "no-store");
		headers.append("Connection", "keep-alive");
    }
}

export class Credentials {
    readonly #serviceUser: string;

    /**
     * Get the user name
     * @returns {string} The user name
     */
    public getServiceUser(): string {
        return this.#serviceUser;
    }

    /**
     * Get the password
     * @returns {string} The password
     */
    public getServicePassword(): string {
        return this.#servicePassword;
    }

    readonly #servicePassword: string;

    /**
     * Construct a credentials object.
     * @param {string} serviceUser The user name
     * @param {string} servicePassword The password
     */
    public constructor(serviceUser: string, servicePassword: string) {
        this.#serviceUser = serviceUser;
        this.#servicePassword = servicePassword;
    }
}
