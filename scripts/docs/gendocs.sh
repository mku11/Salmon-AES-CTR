# use Linux or WSL or cygwin to run

# java
find ../../libs/src/java -type f -name "*.java" | xargs javadoc --no-module-directories -d ../../output/docs/java/html --ignore-source-errors

find ../../libs/src/android -type f -name "*.java" | xargs javadoc --no-module-directories -d ../../output/docs/android/html --ignore-source-errors

# javascript
npx jsdoc ../../libs/projects/SalmonLibs.vscode/lib -r -d ../../output/docs/javascript/html

# typescript
CURR_DIR=$(pwd)
cd ../../libs/projects/SalmonLibs.vscode
npm run tsdocs
cd $CURR_DIR

# .NET
doxygen Doxyfile.Net

# .NET android
doxygen DoxyfileAndroid.Net

# Python
doxygen DoxyfilePython

# C
doxygen DoxyfileC