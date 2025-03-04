You should be able to build the libraries on Windows 10/11, Linux, and macOS  
If you're working on Windows make sure you ignore the unix file permission by setting filemode in your .git/conf:  
```
filemode = false
```