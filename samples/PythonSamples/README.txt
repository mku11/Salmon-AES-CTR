Python Samples

Requirements:
Python 3.11+  
Salmon libs:
pip install salmon_core.tar.gz
pip install salmon_fs.tar.gz

If you want to use the native library download the archive for your respective architecture: 
For windows: salmon-gradle-win-x86_64.zip
For linux: salmon-gradle-linux-x86_64.zip
For mac: salmon-macos-x86_64.dmg

Extract the zip file
Within your script set the path to the library:
For windows:
NativeProxy.set_library_path("/path/to/lib/salmon.dll")
For mac:
NativeProxy.set_library_path("/path/to/lib/libsalmon.dylib")
For linux:
NativeProxy.set_library_path("/path/to/lib/libsalmon.so")
Then set the provider to the native implementation:
AesStream.set_aes_provider_type(ProviderType.AesIntrinsics)

when running make sure you disable type checks with -O option for better performance
python -O data.py