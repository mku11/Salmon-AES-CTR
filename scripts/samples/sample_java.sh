CURRDIR=$(pwd)

cd ../../samples/JavaSamples
./gradlew clean
./gradlew --refresh-dependencies
./gradlew build -x test --rerun-tasks
./gradlew run
cd $CURRDIR