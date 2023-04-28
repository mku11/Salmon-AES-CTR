To build the Xamarin Android app you will need:  
1. Microsoft Visual Studio 2022  
2. Xamarin for Android   
Note: Use "Fast Deployment" under Android project settings if you're debugging
  
Note:  
The AES Intrinsics and Tiny AES variant are not supported for Android Xamarin so make sure that the below line in salmon.h is commented:  
// #define USE_TINY_AES 1  
  
Limitations:  
AES intrinsics is not supported, if you still want to use it you have two options:
1) Use the java Android flavor under ROOT/android folder
2) or follow these steps to include the library: https://learn.microsoft.com/en-us/xamarin/android/platform/java-integration/working-with-jni


