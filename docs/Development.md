You should be able to build the libraries on Windows 10/11, Linux, and macOS  
If you're working on Windows make sure you ignore the unix file permission by setting filemode in your .git/conf:  
```
filemode = false
```

If you have unix shell scripts update the index with the correct permissions before committing.
You can use either a linux distro or WSL, cygwin will not work.
```
git update-index --chmod=+x
```

To do this for all scripts under the repo:
```
find . -name "*.sh" -exec git update-index --chmod=+x {}
```

Also change to LF for all unix scripts:
```
find . -name "*.sh" -exec dos2unix {} \;
```

To refresh a branch from the remote repo:
```
git pull origin wip-3.0.0
```