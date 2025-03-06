The sample scripts will test the samples with the right dependencies

For Windows you can run the bat scripts and some of the sh scripts using WSL or Cygwin.
For Linux and macOS you can run the sh scripts.

Before you start
Make sure you sync the project properties (version numbers) by running ../misc/sync_settings.sh

Also make sure if you're running python tests that you have not installed any other versions of Salmon. If so use a different venv.

For CSharp make sure you build the libraries, if you test the GPU components make sure you build the DebugGPU configuration

For Java make sure you build the libraries.
