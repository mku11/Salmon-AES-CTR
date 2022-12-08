Add the SalmonAES.dll and SalmonAES.pdb (Debug symbols) from the $root/x64 folder as links.
Then set their properties as Content and Copy if newer.

To debug the native code check the option under Project Properties/Debug/Debug Launch UI profiles/Enable native code debugging
Note that debugging the native code will probably disable the Edit and Continue for .NET code.