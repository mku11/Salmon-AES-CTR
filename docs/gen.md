Java
Use javadoc from JDK
```
find ../libs/src -type f -name "*.java" | xargs javadoc -d ../output/docs/java/html --ignore-source-errors
```

JavaScript
Use JSDoc
```
npx jsdoc ..\libs\projects\SalmonLibsTypeScript.VS2022\SalmonLibsTypeScript.VS2022\lib -r -d ..\output\docs\javascript\html
```


C#
Use Doxygen, see config file DoxygenCSharp in the same directory

C
Use Doxygen, see config file DoxygenC in the same directory

Python
Use Doxygen, see config file DoxygenPython in the same directory