
### Gradle  

To integrate the Salmon libraries to your gradle project add the salmon repository:  

```
repositories {
    maven {
        url 'https://github.com/mku11/Repo/raw/main/maven/releases'
    }
    ...
}
```

Add the java libraries to your project:
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

### Maven

To integrate the Salmon libraries to your maven project add the salmon repository:
```
<repositories>
    <repository>
        <id>mku-salmon-repo</id>
        <url>https://github.com/mku11/Repo/raw/main/maven/releases</url>
    </repository>     
    ...
</repositories>
```

Now add the dependencies:  
```
<dependencies>
    <dependency>
        <groupId>com.mku.salmon</groupId>
        <artifactId>salmon-core</artifactId>
        <version>2.0.0</version>
    </dependency>
	<dependency>
        <groupId>com.mku.salmon</groupId>
        <artifactId>salmon-fs</artifactId>
        <version>2.0.0</version>
    </dependency>
    ...
</dependencies>
```

### Nuget

To integrate the Salmon libraries to your Visual Studio project:
Download the Nuget packages from [Salmon nuget repo](https://github.com/mku11/Repo/tree/main/nuget/releases)  
Within Visual Studio go to Tools / Optons / Nuget Package Manager / Package Sources  
Create a new source with a name like "Salmon Repo" and add the local dir that has the downloaded packages.  
Then bring up the Nuget Package Manager and change the Package Source to "Salmon Repo".  
Install the salmon packages like you usually do.  
  
### C/C++ Visual Studio
Same as the NuGet process. Download the native Salmon NuGet package. When installed under the packages folder the include and lib folders will contain everything you need.  
  
### C/C++ Linux
Download the tar.gz package from [Releases](https://github.com/mku11/Salmon-AES-CTR/releases)  
Extract the packages and link in your makefile.  
See the Samples folder for an example.  

### C/C++ MacOS
Download the dmg package from [Releases](https://github.com/mku11/Salmon-AES-CTR/releases)  
Extract the packages and link to your xcode project.  
See the Samples folder for an example.  
  
### Python
To integrate the Salmon libraries to your Python project:  
Download the python packages from [Releases](https://github.com/mku11/Salmon-AES-CTR/releases)  
  
Then run:  
pip install packages/salmon_core.tar.gz  
pip install packages/salmon_fs.tar.gz  

### Typescript/Javascript
To integrate the Salmon libraries to your javascript/typescript project:  
Download the packages from [Releases](https://github.com/mku11/Salmon-AES-CTR/releases)  
  
Then extract the zip files:  
salmon-core.js.2.0.0.zip  
salmon-fs.js.2.0.0.zip  
  
or typescript:  
salmon-core.ts.2.0.0.zip  
salmon-fs.ts.2.0.0.zip  
