#!/usr/bin/env python3
import platform

from salmon_core.salmon.bridge.native_proxy import NativeProxy


class Common:
    @staticmethod
    def set_native_library():
        # set native library path
        platform_os: str = platform.system().upper()
        arch: str = platform.machine()

        print("platform:", platform_os)
        print("arch:", arch)

        library_path: str = "../libs/salmon/"
        if "WINDOWS" in platform_os:
            if arch == "x86_64" or arch == "AMD64":
                library_path += "salmon-msvc-win-x86_64/Salmon.Native.3.0.2/runtimes/win-x64/native/SalmonNative.dll"
        elif "MAC" in platform_os or "DARWIN" in platform_os:
            if arch == "x86_64":
                library_path += "salmon-gcc-macos-x86_64/salmon-gcc-macos-x86_64.3.0.2/lib/libsalmon.dylib"
            elif arch == "aarch64":
                library_path += "salmon-gcc-macos-aarch64/salmon-gcc-macos-aarch64.3.0.2/lib/libsalmon.dylib"
        elif "LINUX" in platform_os:
            if arch == "x86_64" or arch == "AMD64":
                library_path += "salmon-gcc-linux-x86_64/salmon-gcc-linux-x86_64.3.0.2/lib/libsalmon.so"
            elif arch == "aarch64":
                library_path += "salmon-gcc-linux-x86_64/salmon-gcc-linux-x86_64.3.0.2/lib/libsalmon.so"
        print("library path: " + library_path)
        NativeProxy.set_library_path(library_path)
