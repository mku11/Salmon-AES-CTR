# use Linux or WSL or cygwin to run

# Java and Android
find ../../libs/src -type f -name "*.java" | xargs javadoc -d ../../output/docs/java/html --ignore-source-errors

# JavaScript
npx jsdoc ../../libs/projects/SalmonLibs.vscode/lib -r -d ../../output/docs/javascript/html

# TypeScript
CURR_DIR=$(pwd)
cd ../../libs/projects/SalmonLibs.vscode
npm run tsdocs
cd $CURR_DIR

# .NET and Android .NET
doxygen Doxyfile.Net

# Python
doxygen DoxyfilePython
# alternative for python
# pdoc ../../libs/src/python/salmon_core ../../libs/src/python/salmon_fs -o ../../output/docs/python/ --html --force

# C
doxygen DoxyfileC