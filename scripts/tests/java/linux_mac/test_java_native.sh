CURRDIR=$(pwd)

cd ../../../../libs/projects/salmon-libs-gradle

./gradlew :salmon-core:test --tests "com.mku.salmon.test.SalmonNativeTests" -DAES_PROVIDER_TYPE=Aes -i --rerun-tasks
if [ $? -ne 0 ]; then exit 1; fi

./gradlew :salmon-core:test --tests "com.mku.salmon.test.SalmonNativeTests" -DAES_PROVIDER_TYPE=AesIntrinsics -i --rerun-tasks
if [ $? -ne 0 ]; then exit 1; fi

cd $CURRDIR