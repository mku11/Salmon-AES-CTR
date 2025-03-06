CURRDIR=$(pwd)

# export ENABLE_GPU=false

cd ../../libs/projects/salmon-libs-gradle
./gradlew :salmon-core:test --tests "com.mku.salmon.test.SalmonCoreTests" -DAES_PROVIDER_TYPE=Default -i --rerun-tasks
./gradlew :salmon-core:test --tests "com.mku.salmon.test.SalmonCoreTests" -DAES_PROVIDER_TYPE=Aes -i --rerun-tasks
./gradlew :salmon-core:test --tests "com.mku.salmon.test.SalmonCoreTests" -DAES_PROVIDER_TYPE=AesIntrinsics -i --rerun-tasks
cd $CURRDIR