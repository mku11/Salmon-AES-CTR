# use Linux or WSL or cygwin to run

# java
find ../../libs/src -type f -name "*.java" | xargs javadoc -d ../../output/docs/java/html --ignore-source-errors

# typescript
npx jsdoc ../../libs/projects/SalmonLibs.vscode/lib -r -d ../../output/docs/javascript/html

# csharp
doxygen DoxyfileCSharp

# Python
doxygen DoxyfilePython

# C
doxygen DoxyfileC