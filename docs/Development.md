You should be able to build the libraries on Windows 10/11, Linux, and macOS  
If you're working on Windows make sure you ignore the unix file permission by setting filemode in your .git/conf:  
```
filemode = false
```

If you have unix shell scripts update the index with the correct permissions before committing:
```
git update-index --chmod=+x
```