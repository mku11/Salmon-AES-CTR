CURRDIR=$(pwd)

cd ../../../../libs/projects/salmon-libs-gradle

./gradlew :salmon-core:test --tests "com.mku.salmon.test.SalmonCoreTests" -DAES_PROVIDER_TYPE=AesGPU -i --rerun-tasks
if [ $? -ne 0 ]; then exit 1; fi

cd $CURRDIR
