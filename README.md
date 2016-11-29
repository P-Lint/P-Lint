# Best Practices Analyzer

## How to Build
* Run "mvn clean compile package" in project directory
* Creates a jar called "app-analyzer-VERSION_NUM.jar" and its dependency libraries in the "target" directory.

## How to Run
* Make sure you have a directory of already uncompressed APK's to analyze
* Navigate to project, run "java -jar ./target/app-analyzer-VERSION_NUM.jar /path/to/uncompressedAPKs"
* Results are put into HTML file labeled "results.html" at top level of the project directory
