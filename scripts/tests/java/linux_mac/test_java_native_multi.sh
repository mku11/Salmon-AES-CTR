CURRDIR=$(pwd)

ENC_THREADS=2

cd ../../../../libs/projects/salmon-libs-gradle

./gradlew :salmon-core:test --tests "com.mku.salmon.test.SalmonNativeTests" -DAES_PROVIDER_TYPE=Aes -DENC_THREADS=$ENC_THREADS  -i --rerun-tasks

./gradlew :salmon-core:test --tests "com.mku.salmon.test.SalmonNativeTests" -DAES_PROVIDER_TYPE=AesIntrinsics -DENC_THREADS=$ENC_THREADS -i --rerun-tasks

cd $CURRDIR