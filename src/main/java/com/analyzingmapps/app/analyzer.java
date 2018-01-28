package com.analyzingmapps.app;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.*;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.concurrent.TimeUnit;


/**
 * Created by Colton Dennis on 10/4/16.
 * Refactored by Virginia Pujols 01/20/2018
 */
public class analyzer {
    private static sqliteDBController databaseController;
    private static String MANIFEST_FILE = "AndroidManifest.xml";
    private String currentAppName;
    private String currentCommitGUID;
    private Path currentCommitDirectory;

    private class MyFileVisitor extends SimpleFileVisitor<Path> {
        private PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.java");
        private PathMatcher matcherByOneFile;
        private List<Path> foundManifests = new ArrayList<>();
        private String fileName;

        Path foundPath;

        MyFileVisitor() { }

        MyFileVisitor(String fileName) {
            this.fileName = fileName;
        }

        private boolean shouldSkip(Path path) {
            return path.getFileName().toString().matches("^.*?(bin|androidTest|test|lib|debug|fdroid|build|gradle|res).*$");
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (shouldSkip(dir)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            //System.out.println("DIR -> " + dir.toAbsolutePath());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
            if (fileName != null && fileName.equals(filePath.getFileName())) {
                //System.out.println("ONE FILE -> " + filePath.getFileName());
                foundPath = filePath;
                return FileVisitResult.TERMINATE;

            } else if (matcher.matches(filePath.getFileName())) { //Analyze all *.java

                //System.out.println("JAVA FILE -> " + filePath.getFileName());
                analyzer.this.analyzeSourceCode(filePath);

            } else if (filePath.getFileName().toString().equals(MANIFEST_FILE)) {

                //System.out.println("XML FILE -> " + filePath.getFileName());
                foundManifests.add(filePath);

            }
            return FileVisitResult.CONTINUE;
        }

        List<Path> getFoundManifests() {
            return foundManifests;
        }

    }

    private class MyFileParserVisitor extends VoidVisitorAdapter {
        CompilationUnit compilationUnit;
        HashMap<String, String> shouldRequestPermissionCalls = new HashMap<>();
        HashMap<String, String> checkSelfPermissionCalls = new HashMap<>();
        HashMap<String, String> requestPermissionsCalls = new HashMap<>();

        @Override
        public void visit(MethodCallExpr n, Object arg) {
            super.visit(n, arg);

            String methodName = getMethodName(compilationUnit, n);
            String beginLine = String.valueOf(n.getBegin().get().line);
            if(n.getNameAsString().compareTo("shouldShowRequestPermissionRationale") == 0){
                shouldRequestPermissionCalls.put(beginLine, methodName);
            }
            if(n.getNameAsString().compareTo("checkSelfPermission") == 0){
                checkSelfPermissionCalls.put(beginLine, methodName);
            }
            if(n.getNameAsString().compareTo("requestPermissions") == 0){
                requestPermissionsCalls.put(String.valueOf(beginLine), methodName);
            }

        }

        private String getMethodName(CompilationUnit compilationUnit, final MethodCallExpr callExpr) {
            final String[] methodName = {""}; // Necessary in order to have the final keyword  >_<
            // Evaluate the CompilationUnit, now searching for the methods' definitions to identify the parent
            (new VoidVisitorAdapter<Object>() {
                @Override
                public void visit(MethodDeclaration declExpr, Object arg) {
                    super.visit(declExpr, arg);

                    //System.out.println("[L:"+n.getBegin().line+"] " + n.getName());
                    if (declExpr.getBegin().get().line < callExpr.getBegin().get().line) {
                        methodName[0] = declExpr.getName() + "," + declExpr.getBegin().get().line;
                    }
                }
            }).visit(compilationUnit, null);
            return methodName[0];
        }

    }

    private void traverseApps(Path directory) {
        ArrayList<String> apps = databaseController.getAppList();
        for (String appName : apps) {
            currentAppName = appName;

            if (!Files.exists(Paths.get(directory.toString(), appName))) {
                continue; // Move to the next app to analyze
            }

            //TODO: Find a way to optimize commits select if possible.
            ArrayList<String> apkList = databaseController.getApkList(appName); // List of commits per App
            for (String commitGUID : apkList) {
                Path projectPath = Paths.get(directory.toString(), appName, commitGUID);
                currentCommitGUID = commitGUID;
                currentCommitDirectory = projectPath;

                if (Files.exists(projectPath)) {
                    System.out.println("Analyzing " + projectPath);
                    try {
                        MyFileVisitor fileVisitor = new MyFileVisitor();
                        Files.walkFileTree(projectPath, fileVisitor);
                        // Treat Manifests
                        List<Path> manifestList = fileVisitor.getFoundManifests();
                        analyzeManifest(commitGUID, manifestList);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void analyzeManifest(String commitGUID, List<Path> manifestList) {
        Path tempPath = null;
        int commitID = databaseController.getApkID(commitGUID);

        //If there is more than one, search for src/main folder
        if (manifestList.size() == 1) {
            tempPath = manifestList.get(0);
        } else if (manifestList.size() > 1) {
            //TODO: validate if this whole for can be eliminated
            for (Path itemPath : manifestList) {
                if (itemPath.toAbsolutePath().toString().contains("src/main")) {
                    tempPath = itemPath;
                    break;
                }
            }
        }

        if (tempPath == null) {
            databaseController.insertReport(commitID, 17, MANIFEST_FILE, "",-1);
        } else {
            /* *************** ANALYZE MANIFEST FILE ****************** */
            File manifest = tempPath.toFile();
            int LINE = -1;

            try {
                Document manifestXML = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest);

                NodeList sdkVers = manifestXML.getElementsByTagName("uses-sdk");
                NodeList androidPerms = manifestXML.getElementsByTagName("uses-permission");
                NodeList customPerms = manifestXML.getElementsByTagName("permission");

                for(int z=0; z<sdkVers.getLength(); z++) {
                    Node versionInfo = sdkVers.item(z);
                    Node sdkMin = versionInfo.getAttributes().getNamedItem("android:minSdkVersion");
                    Node sdkMax = versionInfo.getAttributes().getNamedItem("android:maxSdkVersion");

                    if(sdkMin != null && sdkMax != null) {
                        int minVersion = Integer.parseInt(sdkMin.getNodeValue());
                        int maxVersion = Integer.parseInt(sdkMax.getNodeValue());

                        if(minVersion < 23 && maxVersion >= 23) {
                            databaseController.insertReport(commitID, 14, MANIFEST_FILE, "", LINE);
                        } else if(maxVersion < 23){
                            databaseController.insertReport(commitID, 15, MANIFEST_FILE, "", LINE);
                        } else {
                            databaseController.insertReport(commitID, 16, MANIFEST_FILE, "", LINE);
                        }
                    } else if(sdkMax != null) {
                        int maxVersion = Integer.parseInt(sdkMax.getNodeValue());
                        if(maxVersion < 23){
                            databaseController.insertReport(commitID, 15, MANIFEST_FILE, "", LINE);
                        }
                    }
                }
                for(int x=0; x<androidPerms.getLength(); x++) {
                    Node perm = androidPerms.item(x);
                    String permName = perm.getAttributes().getNamedItem("android:name").getNodeValue();

                    if(permName.compareTo("android.permission.CAMERA")==0){
                        databaseController.insertReport(commitID, 2, MANIFEST_FILE, "", LINE);
                    }
                    else if(permName.compareTo("android.permission.SEND_SMS")==0){
                        databaseController.insertReport(commitID, 3, MANIFEST_FILE, "", LINE);
                    }
                    else if(permName.compareTo("android.permission.CALL_PHONE")==0){
                        databaseController.insertReport(commitID, 4, MANIFEST_FILE, "", LINE);
                    }


                    Node sdkMinVersion = perm.getAttributes().getNamedItem("android:minSdkVersion");
                    Node sdkMaxVersion = perm.getAttributes().getNamedItem("android:maxSdkVersion");
                    if(sdkMinVersion != null){
                        int minVersion = Integer.parseInt(sdkMinVersion.getNodeValue());

                        if(minVersion >= 23){
                            databaseController.insertReport(commitID, 11, MANIFEST_FILE, "", LINE);
                        } else if(sdkMaxVersion != null){
                            int maxVersion = Integer.parseInt(sdkMaxVersion.getNodeValue());
                            if(maxVersion >= 23){
                                databaseController.insertReport(commitID, 10, MANIFEST_FILE, "", LINE);
                            } else { //maxVersion < 23
                                databaseController.insertReport(commitID, 12, MANIFEST_FILE, "", LINE);
                            }
                        } else { //minVersion < 23
                            databaseController.insertReport(commitID, 9, MANIFEST_FILE, "", LINE);
                        }
                    } else if(sdkMaxVersion != null) {
                        int maxVersion = Integer.parseInt(sdkMaxVersion.getNodeValue());
                        if(maxVersion < 23){
                            databaseController.insertReport(commitID, 12, MANIFEST_FILE, "", LINE);
                        } else { // maxVersion >=23
                            databaseController.insertReport(commitID, 13, MANIFEST_FILE, "", LINE);
                        }
                    }

                }

                for(int y=0; y<customPerms.getLength(); y++) {
                    databaseController.insertReport(commitID, 8, MANIFEST_FILE, "",LINE);
                }

            } catch(Exception e) {
                System.out.println("Something went wrong parsing the Android Manifest XML: " + e);
                logError(manifest.getPath(), e.getMessage(), "analyzeManifest");
            }
        }
    }

    private void analyzeSourceCode(Path filePath) {
        int commitID = databaseController.getApkID(currentCommitGUID);
        String fileName = filePath.getFileName().toString();
        File file = filePath.toFile();

        try {
            FileInputStream in = new FileInputStream(file);
            CompilationUnit cu;
            cu = JavaParser.parse(in);
            in.close();

            MyFileParserVisitor currentFileVisitor = new MyFileParserVisitor();
            currentFileVisitor.compilationUnit = cu;
            currentFileVisitor.visit(cu, fileName);

            for(String lineNum : currentFileVisitor.shouldRequestPermissionCalls.keySet()){
                String methodName = currentFileVisitor.shouldRequestPermissionCalls.get(lineNum).split(",")[0];
                databaseController.insertReport(commitID, 1, fileName, methodName, Integer.parseInt(lineNum));
            }

            ArrayList<Integer> requestCallLineNumbers = new ArrayList<>();
            for(String lineNum: currentFileVisitor.requestPermissionsCalls.keySet()) {
                int requestPermissionCallLine = Integer.parseInt(lineNum);
                requestCallLineNumbers.add(requestPermissionCallLine);

                String methodCombination = currentFileVisitor.requestPermissionsCalls.get(lineNum);
                String methodName = methodCombination.split(",")[0];
                int methodNameLine = Integer.parseInt(methodCombination.split(",")[1]);

                if (currentFileVisitor.checkSelfPermissionCalls.keySet().isEmpty()) {
                    // If checkSelfPermission was not defined in current class, search for the call in the other project files
                    HashMap<String, String> outerFileCheckPermCalls = getOuterSelfPermsCalls(file, requestPermissionCallLine, methodNameLine);
                    if (outerFileCheckPermCalls.isEmpty()) {
                        databaseController.insertReport(commitID, 5, fileName, methodName, requestPermissionCallLine);
                    } else {
                        databaseController.insertReport(commitID, 6, fileName, methodName, requestPermissionCallLine);
                    }
                } else {
                    databaseController.insertReport(commitID, 6, fileName, methodName, requestPermissionCallLine);
                }
            }

            for(int calls: requestCallLineNumbers){
                if(requestCallLineNumbers.contains(calls+1)
                        || requestCallLineNumbers.contains(calls-1)
                        || requestCallLineNumbers.contains(calls+2)
                        || requestCallLineNumbers.contains(calls-2)
                        || requestCallLineNumbers.contains(calls+3)
                        || requestCallLineNumbers.contains(calls-3)){

                    String methodName = currentFileVisitor.requestPermissionsCalls.get(String.valueOf(calls)).split(",")[0];
                    databaseController.insertReport(commitID, 7, fileName, methodName, calls);
                }
            }

        } catch (Exception e) {
            System.out.println("Something went wrong parsing " + file.getPath());
            logError(file.getPath(), e.getMessage(), "analyzeSourceCode");
        }

    }

    /**
     *
     * Checks when requestPermission is called within another source code
     * @return the combination of [line, method_name] of a found occurrence
     */
    private HashMap<String, String> getOuterSelfPermsCalls(final File currentFile, final int permissionCallLine, final int methodDefinitionLine) {
        final HashMap<String, String> fileCheckSelfMap = new HashMap<>();
        final int searchLimitLine = methodDefinitionLine;
        FileInputStream in;

        try {
            in = new FileInputStream(currentFile);
            final CompilationUnit cu = JavaParser.parse(in);
            final List<ImportDeclaration> cuImports = cu.getImports();
            in.close();

            (new VoidVisitorAdapter<Object>() {
                @Override
                public void visit(final MethodCallExpr methodCallExpr, Object arg) {
                    // First, evaluate if the statement is between the line of in which the "permissionRequest" was called
                    // and the line where the method definition begins.
                    int currentSearchLine = methodCallExpr.getBegin().get().line;
                    if (currentSearchLine < permissionCallLine && currentSearchLine > searchLimitLine) {
                        if (methodCallExpr.getScope().isPresent()) {
                            for (ImportDeclaration itemImport : cuImports) {
                                //Second, If the instance calling the method (methodCallExpr.getScope) is found in the Import, continue
                                if (itemImport.getName().getIdentifier().equals(methodCallExpr.getScope().get().toString())) {
                                    String fileName = itemImport.getName().getIdentifier()  + ".java";

                                    try {
                                        // Third, search for the specific file
                                        MyFileVisitor fileVisitor = new MyFileVisitor(fileName);
                                        Files.walkFileTree(currentCommitDirectory, fileVisitor);

                                        if (fileVisitor.foundPath != null) {
                                            File innerFile = fileVisitor.foundPath.toFile();
                                            final CompilationUnit innerCU = JavaParser.parse(innerFile);

                                            // Forth, evaluate found file to search for checkSelfPermission calls
                                            (new VoidVisitorAdapter<Object>() {
                                                @Override
                                                public void visit(MethodDeclaration mDeclaration, Object arg) {
                                                    if (methodCallExpr.getName() != null && mDeclaration.getName() != null) {
                                                        if (methodCallExpr.getName().equals(mDeclaration.getName())) {
                                                            for (Statement stmt : mDeclaration.getBody().get().getStatements()) {
                                                                if (stmt.toString().contains("checkSelfPermission")) {
                                                                    fileCheckSelfMap.put(String.valueOf(mDeclaration.getBegin().get().line), mDeclaration.getNameAsString());
                                                                    return; // I only need the first occurrence match
                                                                }
                                                            }
                                                        }
                                                    }

                                                    super.visit(mDeclaration, arg);
                                                }
                                            }).visit(innerCU, null);
                                        }

                                    } catch (Exception e) {
                                        System.out.println("Something went wrong parsing getOuterSelfPermsCalls " + currentFile.getPath());
                                        logError(currentFile.getPath(), e.getMessage(), "getOuterSelfPermsCalls");
                                    }
                                }
                            }
                        }
                    }

                    super.visit(methodCallExpr, arg);
                }
            }).visit(cu, null);
        }
        catch(Exception e) {
            System.out.println("Something went wrong parsing getOuterSelfPermsCalls " + currentFile.getPath()
            );
            logError(currentFile.getPath(), e.getMessage(), "getOuterSelfPermsCalls");
        }

        return fileCheckSelfMap;
    }

    private void logError(String path, String errorMessage, String pLintMethod) {
        databaseController.insertLog(currentAppName, currentCommitGUID, path, errorMessage, pLintMethod);
    }

    public static void main(String[] args) throws IOException {
        long startTime = System.nanoTime();

        //String path = "/Users/virginia/Documents/RITPermSmells/tempo";
        //String path = "/Users/virginia/Documents/RITPermSmells/VirginiaProjects";
        String path = args[0];
        Path startingPath = Paths.get(path);

        databaseController = new sqliteDBController();
        try {
            databaseController.connectToDB();

            analyzer newAnalyzer = new analyzer();
            newAnalyzer.traverseApps(startingPath);

        } catch (Exception e) {
            System.out.print("Issues connecting to results database: \n" + e);
            System.exit(0);
        } finally {
            try {
                databaseController.dbPLintResultConnection.close();
                databaseController.dbAppsDatasetConnection.close();
                databaseController = null;
            } catch (SQLException e) {
                System.out.print("Issues closing database: \n" + e);
            }
        }

        // Print elapsed time
        long endTime = System.nanoTime();
        long difference = endTime - startTime;
        long seconds = TimeUnit.NANOSECONDS.toSeconds(difference);
        System.out.println("Total execution time: " + seconds + " seconds");
    }
}