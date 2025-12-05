
### Java Gradle  

To integrate the Salmon libraries to your gradle project:  
Download the salmon multi-arch package from:
https://github.com/mku11/Salmon-AES-CTR/releases
Unzip the contents of the downloaded archive  
Create a local maven repository definition in your build.gradle.  
For more info you can see the samples provided in the source code:  
```
repositories {
    // local repos, make sure you run samples/get_salmon_libs.bat
	maven {
		url uri("../salmon/salmon-java")
	}
	// add the repository for the native library (optional)
	maven {
		url uri("../salmon/salmon-java-win-x86_64")
	}
    ...
}
```

Now you can add the java libraries to your project:  
```
dependencies {
    implementation 'com.mku.salmon:salmon-core:2.0.0'
    implementation 'com.mku.salmon:salmon-fs:2.0.0'
	
    // for android
    implementation 'com.mku.salmon:salmon-fs-android:2.0.0'
    
    // optional fast AES intrinsics and Tiny AES
    // make sure you use SalmonStream.setProviderType() to set the provider in java
    implementation 'com.mku.salmon:salmon-native-android:2.0.0'
}
```

### Windows native library in Gradle
To add the native library for windows to your gradle project add the task below:  
```
// unzipping the native library
configurations {
    nativeImplementation
}
task unzipNative(type: Sync) {
    from {
        configurations.nativeImplementation.collect { zipTree(it) }
    }
    into file(project.nativeLibsDir)
}
build.dependsOn unzipNative
dependencies {
    implementation 'com.mku.salmon:salmon-core:2.0.0'
    implementation 'com.mku.salmon:salmon-fs:2.0.0'
	
    // use SalmonStream.setProviderType() within your code
    // and make sure you add the dll libary to the java.library.path  
    unzipNative 'com.mku.salmon:salmon-native:2.0.0'
}
```


### C# Visual Studio Nuget

To integrate the Salmon libraries to your Visual Studio project:
Download the Nuget packages from [Salmon nuget repo](https://github.com/mku11/Repo/tree/main/nuget/releases)  
Within Visual Studio go to Tools / Optons / Nuget Package Manager / Package Sources  
Create a new source with a name like "Salmon Repo" and add the local dir that has the downloaded packages.  
Then bring up the Nuget Package Manager and change the Package Source to "Salmon Repo".  
Install the salmon packages like you usually do.  
Alternatively, you can add a nuget.config file in your .NET project:
```
<?xml version="1.0" encoding="utf-8"?>
<configuration>
    <packageSources>
        <add key="SalmonPackages" value="..\..\libs\salmon\salmon-dotnet" />
    </packageSources>
</configuration>
```
  
### C/C++ Visual Studio
The libraries provided are within the multi-arch packages see above. You can follow the same process as the .NET and include a nuget.config file pointing to the native library:
```
<?xml version="1.0" encoding="utf-8"?>
<configuration>
    <packageSources>
        <add key="SalmonPackages" value="..\..\libs\salmon\salmon-msvc-win-x86_64" />
    </packageSources>
</configuration>
```
  
### C/C++ Linux/MacOS
Extract the multi-arch package as above and add the include and lib directories in your makefile.  
See the Samples folder for a working example.  
  
### Python
To integrate the Salmon libraries to your Python project:  
Install the tar.gz packages included in the salmon multi-arch package:
```
python -m pip install simple_io_py.tar.gz
python -m pip install simple_fs_py.tar.gz
python -m pip install salmon_core_py.tar.gz
python -m pip install salmon_fs_py.tar.gz
```
  
### Typescript/Javascript
For TS/JS find the typescript and javascript files in the multi-arch package and import them as ESM modules in your code.

For working examples see the samples directory in the source code.