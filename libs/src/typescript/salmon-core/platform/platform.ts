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

/**
 * Utility class for platform specifics.
 */
export class Platform {
    /**
     * Returns the running platform.
     * @returns {PlatformType} The current version
     */
    public static getPlatform(): PlatformType {
        if(typeof process === 'object')
            return PlatformType.NodeJs;
        else
            return PlatformType.Browser;
    }
	
	/**
     * Returns the running operating system.
     * @returns {OSType} The operating system
     */
    public static getOS(): OSType {
        if(typeof process === 'object') {
			switch(process.platform) {
				case "win32":
					return OSType.Windows;
				case "linux":
					return OSType.Linux;
				case "darwin":
					return OSType.Darwin;
			}
        }
        return OSType.Unknown;
    }
	
	/**
     * Imports CommonJS node libraries. Do not use in the browser.
     * @returns {any} The module export
     */
    public static require(moduleName: string): any {
        return requireModule(moduleName);
    }
}

/**
 * The platform the script is running on.
 */
export enum PlatformType {
    /**
     * Browser
     */
    Browser,
    /**
     * NodeJs
     */
    NodeJs
}

/**
 * The operating system the script is running on.
 */
export enum OSType {
    /**
     * Windows
     */
    Windows,
    /**
     * Linux
     */
    Linux,
    /**
     * Darwin (MacOS)
     */
    Darwin,
    /**
     * Unknown
     */
    Unknown
}

let requireModule: any | null;
if(Platform.getPlatform() == PlatformType.NodeJs) {
	const { createRequire } = await import('module');
	requireModule = createRequire(import.meta.url);
}