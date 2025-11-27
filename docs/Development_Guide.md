# Dependencies
You will need first to initialize the gitmodules and download code dependencies
This includes SimpleIO, SimpleFS (code dependencies) and TinyAES (testing dependencies)
Go to directory scripts/misc and run:  
For Linux/MacOS:  
```
./init_gitmodules.sh
```
For Windows:  
```
init_gitmodules.bat
```

If you're building the GPU native library you need to get the OpenCL:
```
get_opencl.bat
```

Now you should be able to build the libraries on Windows 10/11, Linux, and macOS.
Make sure you read the README files in each folder as they are specific to the language, platform, and operating system you're building for.

# Directory structure:
scripts/misc: contains scripts to initialize the build and test environment  
scripts/deploy: command line batch build scripts  
scripts/test: command line unit batch test scripts  
scripts/docs: documentation generation scripts  
scripts/samples: scripts for running code samples  
libs/projects: contains project files for the most common IDEs, keep in mind most of the code is located outside the projects so it can be reused.  
libs/src: contains most of the reusable code  
libs/test: contains unit test code except for typescript unit test which are located inside the project folders.  
services: contains services  
output: the output directory will contain all the built packages.

# Git
If you're working on Windows make sure you ignore the unix file permission by setting filemode in your .git/conf:  
```
filemode = false
```

If you create new unix shell scripts update the index with the correct permissions before committing.
You can use either a linux distro or WSL, cygwin will not work.
```
git update-index --chmod=+x
```

To do this for all scripts under the repo:
```
find . -name "*.sh" -exec git update-index --chmod=+x {} \;
find . -name "gradlew" -exec git update-index --chmod=+x {} \;
```

Also change to LF for all unix scripts:
```
find . -name "*.sh" -not -path "*/node_modules/*" -exec dos2unix {} \;
find . -name "gradlew" -not -path "*/node_modules/*" -exec dos2unix {} \;
```

To refresh a branch from the remote repo:
```
git pull origin wip-3.0.2
```

To upgrade the git submodule go under submodule under project libs/deps  
and pull the latest changes following with a commit and initialize the module again to copy the changes:
```
git pull origin main
git commit
$ cd ../../../scripts/misc/
./init_gitmodules.sh
```