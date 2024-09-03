Java
Use javadoc from JDK
```
find ../libs/src -type f -name "*.java" | xargs ../output/docs/javadoc -d java --ignore-source-errors
```

JavaScript
Use JSDoc
```
npx jsdoc lib -r -d ..\..\..\..\output\docs\javascript
```


C#
Use Doxygen, see config file DoxygenCSharp in the same directory

C
Use Doxygen, see config file DoxygenC in the same directory

Python
Use Doxygen, see config file DoxygenPython in the same directory