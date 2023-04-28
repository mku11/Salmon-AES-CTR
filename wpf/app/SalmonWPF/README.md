To build the WPF app you will need:  
1. Microsoft Visual Studio  
2. If you want to use the AES intrinsics for Intel x86 (optional) you will need:  
	a. Microsoft Visual Studio C++ installed  
	b. Make sure the SalmonAES.dll and SalmonAES.pdb (Debug symbols) from the $root/x64 folder as links.  
	c. Then set their properties as Content and Copy if newer.  
  
Note:  
The Tiny AES variant is not supported for wpf so make sure that the below line in salmon.h is commented:  
// #define USE_TINY_AES 1  
  
Optional:  
If you want to use the AES intrinsics for ARM64 (optional) you will need:  
1. Microsoft Visual Studio C++ installed  
2. If you want to compile for ARM you need to download tiny-aes: https://github.com/kokke/tiny-AES-c into folder c/src/tiny-aes  
3. Make sure that SalmonAES.dll and SalmonAES.pdb (Debug symbols) are linked from the root x64 folder.  
4. Modify aes.h and set: #define AES256 1  

To debug the native code check the option under Project Properties/Debug/Debug Launch UI profiles/Enable native code debugging
Note that debugging the native code will probably disable the Edit and Continue for .NET code.

Video Playback (Optional):
If you want to play encrypted media you will need to download ffmpeg from here:
https://github.com/BtbN/FFmpeg-Builds/releases
Make sure you download version 4.4: ffmpeg-n4.4-latest-win64-gpl-shared-4.4.zip
unzip the contents of the bin/ folder from inside the zip file into folder: c:\ffmpeg\x64