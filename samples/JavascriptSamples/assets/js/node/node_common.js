import { Platform, PlatformType, OSType } from '../lib/simple-io/platform/platform.js';
import { NativeProxy } from '../lib/salmon-core/salmon/bridge/native_proxy.js';

export class Common {
    static async setNativeLibrary() {
		// set native library path
		let platformOS = Platform.getOS();
		let arch = "";
        let currDir = "";
        if(Platform.getPlatform() == PlatformType.NodeJs) {
            arch = process.arch;
            // for Linux we need the absolute path
            const { fileURLToPath } = await import('node:url');
            const __filename = fileURLToPath(import.meta.url);
            let idx = __filename.lastIndexOf("\\");
            if(idx < 0)
                idx = __filename.lastIndexOf("/");
            if(idx >= 0)
                currDir = __filename.substring(0,idx);
        }
		
		console.log("currDir:", currDir);
		console.log("platform:", OSType[platformOS]);
		console.log("arch:", arch);
		
		let libraryPath = "../../../../libs/salmon/";
		switch(platformOS) {
			case OSType.Linux:
				if(arch == 'x64')
					libraryPath += "salmon-gcc-linux-x86_64/salmon-gcc-linux-x86_64.3.0.2/lib/libsalmon.so";
				else if(arch == 'arm64')
					libraryPath += "salmon-gcc-linux-x86_64/salmon-gcc-linux-aarch64.3.0.2/lib/libsalmon.so";
				break;
			case OSType.Windows:
				if(arch == 'x64')
					libraryPath += "salmon-msvc-win-x86_64/Salmon.Native.3.0.2/runtimes/win-x64/native/SalmonNative.dll";
				break;
			case OSType.Darwin:
				if(arch == 'x64')
					libraryPath += "salmon-gcc-macos-x86_64/salmon-gcc-macos-x86_64.3.0.2/lib/libsalmon.so";
				else if(arch == 'arm64')
					libraryPath += "salmon-gcc-macos-aarch64/salmon-gcc-macos-aarch64.3.0.2/lib/libsalmon.dylib";
				break;
		}
		libraryPath = await Platform.getAbsolutePath(libraryPath, import.meta.url);
		console.log("library path: " + libraryPath);
        NativeProxy.setLibraryPath(libraryPath);
	}
}

export function printReset(msg) {}

export function print(msg) {
	if(msg !== undefined)
		console.log(msg);
	else
		console.log("");
}

global.print = print;
global.printReset = printReset;