# Best Practices Analyzer

## Installing Decompiler
This is a wholesome all in one solution to decompress an APK file to
readable xml and decompiled code.
* Naviage to decompiler directory
* Run the Python3 `setupDependencies.py` script
```
./setupDependencies.py
```
This will get the appropriate libraries under the `lib/` directory.

## How to Build P-Lint
* Navigate to P-Lint root directory
* Run "mvn clean compile package" in project directory
* Creates a jar called "app-analyzer-VERSION_NUM.jar" and its dependency libraries in the "target" directory.

## Decompiling apps

* Navigate to decompiler directory to decompile and uncompress APKs then run
```
./apk_decompiler.sh path/to/app.apk
```

The result will be found in the decompiler directory under a `app.apk.uncompressed` directory.

## How to Run
* Make sure you have a directory of already uncompressed APKs to analyze
* Navigate to project, run "java -jar ./target/app-analyzer-VERSION_NUM.jar /path/to/uncompressedAPKs"
* Results are put into HTML file labeled "results.html" at top level of the project directory
