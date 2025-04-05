To build the Salmon Samples for Android you need:  
1. Android Studio  
2. Intellij IDEA.  
Configure the sdk location
Either create a file local.properties with the following:
```
sdk.dir=C\:\\Path\\To\\Android\\android-sdk
```
Or open Android Studio and configured in settings.
Or set env variable:
```
export ANDROID_HOME=/path/to/android/sdk
```

If you're building on Linux or macOS make sure you have the JDK installed:
```
sudo apt install default-jdk
```

If you're in development and the snapshot dependencies have changed make sure you refresh:
```
gradlew.bat --refresh-dependencies
```

To build from the command line run:  
```
gradlew.bat build -x test --rerun-tasks    
```

