Java
Use javadoc from JDK
```
find ../libs/src -type f -name "*.java" | xargs html/javadoc -d java --ignore-source-errors
```

C#
Use Doxygen, see config file DoxygenCSharp in the same directory

C
Use Doxygen, see config file DoxygenC in the same directory

TypeScript
Use TypeDoc
```
find ../libs/src/typescript/salmon_* -type f -name "*.ts" | xargs npx typedoc -out html/typescript
```

Python
Use Doxygen, see config file DoxygenPython in the same directory