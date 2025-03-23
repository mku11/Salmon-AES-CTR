CURRDIR=$(pwd)

cd ../../../../libs/projects/salmon-libs-gradle

./gradlew :salmon-core:test --tests "com.mku.salmon.test.SalmonCoreTests" -DAES_PROVIDER_TYPE=AesGPU -i --rerun-tasks

cd $CURRDIR
