package com.analyzingmapps.app;

import java.io.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.concurrent.TimeUnit;

/**
 * Created by Colton Dennis on 10/4/16.
 * Updated by Virginia Pujols 01/20/2018
 */
public class Analyzer {
    static String PATH_SEPARATOR = "/";
    static DBController db;
    static String MANIFEST_FILE = "AndroidManifest.xml";
    String currentAppName;
    String currentCommitGUID;
    Path currentProjectPath;
    static List<LogItem> logData ;


    private void traverseApps(Path directory) {
        System.out.println("Executing getAppCommitLogs...");
        ArrayList<GitCommit> commitLogs = db.getAppCommitLogs();

        for (GitCommit commitInfo : commitLogs) {
            currentAppName = commitInfo.getApp();
            currentCommitGUID = commitInfo.getCommitSha1();

            Path projectPath = Paths.get(directory.toString(), currentAppName, currentCommitGUID);
            currentProjectPath = projectPath;

            if (!Files.exists(projectPath) || alreadyExists(currentAppName, currentCommitGUID)) {
            //if (!Files.exists(projectPath) || db.alreadyExistAppCommit(currentAppName, currentCommitGUID)) {
                System.out.println("Skipped " + projectPath);
                continue; // Folder not exists, Move to the next commit to analyze
            }

            System.out.println("Analyzing " + projectPath);
            readFilesFromDB(projectPath);

            // Insert log
            db.insertCompletionLog(currentAppName, currentCommitGUID);
        }
    }

    private boolean alreadyExists(String app,String commit){
        long count = logData.stream().filter(a -> a.getApp().equals(app) ).filter(c ->c.getCommit().equals(commit)).count();
        if(count>=1)
            return true;
        else
            return false;
    }


    private void traverseAppsOld(Path directory) {
            ArrayList<String> apps = db.getAppList();
            for (String appName : apps) { // Iterate through the App
                currentAppName = appName;

                if (!Files.exists(Paths.get(directory.toString(), appName))) {
                    continue; // File not exists, Move to the next app to analyze
                }

                ArrayList<String> apkList = db.getApkList(appName); // List of commits per App
                for (String commitGUID : apkList) { // Iterate through the app's commits
                    currentCommitGUID = commitGUID;
                    Path projectPath = Paths.get(directory.toString(), appName, commitGUID);
                    if (!Files.exists(projectPath)) {
                        continue; // File not exists, Move to the next commit to analyze
                    }
                    System.out.println("Analyzing " + projectPath);

                    //readFilesFromFolders(projectPath, commitGUID);
                    readFilesFromDB(projectPath);
                }
            }
        }


    private void readFilesFromFolders(Path projectPath, String commitGUID) {
        try {
            MyFileSearcher fileVisitor = new MyFileSearcher(this);
            fileVisitor.parentDir = projectPath;
            Files.walkFileTree(projectPath, fileVisitor);

            // If not even one java file is found, is not a java project
            if (!fileVisitor.isJavaProject) return;

            // Treat Manifests
            List<Path> manifestList = fileVisitor.getFoundManifests();
            analyzeManifest(manifestList);

            // Treat Source Code
            List<MyFileParserVisitor> filesList = fileVisitor.getJavaFiles();
            analyzeSourceCode(filesList);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readFilesFromDB(Path projectPath) {
        // List of files per commit
        ArrayList<GitCommitFile> filesList = db.getCommitFileList(currentAppName, currentCommitGUID);

        MyFileSearcher fileVisitor = new MyFileSearcher(this);
        Set<Path> foundManifests = new HashSet<>();

        for (GitCommitFile commitFile : filesList) {

            Path filePath = Paths.get(projectPath.toString(), commitFile.getPath());

            //Update a renamed (non manifest) file over the previous saved report records
            if (commitFile.getStatus() != null && commitFile.getStatus().equals("Renamed") && !commitFile.getPath().contains(MANIFEST_FILE)) {
                Path oldFilePath = Paths.get(projectPath.toString(), commitFile.getOldPath());
                db.updateReportFileNames(currentAppName, oldFilePath.getFileName().toString(), filePath.getFileName().toString());
            }

            if (commitFile.getPath().contains(MANIFEST_FILE)) {
                foundManifests.add(filePath);
            } else {
                fileVisitor.checkPermissionUsage(filePath.toFile());
            }
        }

        if (!filesList.isEmpty()) {
            // Treat Manifests
            analyzeManifest(new ArrayList<>(foundManifests));
            // Treat Source Code
            analyzeSourceCode(fileVisitor.getJavaFiles());
        }
    }

    private void analyzeManifest(List<Path> manifestList) {
        Path tempPath = null;

        //If there is more than one, search for src/main folder
        if (manifestList.size() == 1) {
            tempPath = manifestList.get(0);
        } else if (manifestList.size() > 1) {
            for (Path itemPath : manifestList) {
                if (itemPath.toAbsolutePath().toString().contains("src/main")) {
                    tempPath = itemPath;
                    break;
                }
            }
        }

        if (tempPath == null) {
            return;
        }

        /* *************** ANALYZE MANIFEST FILE ****************** */
        File manifest = tempPath.toFile();
        String fileName = MANIFEST_FILE; //manifest.toString().replace(currentProjectPath.toString(), "");

        try {
            Document manifestXML = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest);

            NodeList sdkVers = manifestXML.getElementsByTagName("uses-sdk");
            NodeList androidPerms = manifestXML.getElementsByTagName("uses-permission");
            NodeList customPerms = manifestXML.getElementsByTagName("permission");

            boolean inserted = false;
            for(int z=0; z<sdkVers.getLength(); z++) {
                Node versionInfo = sdkVers.item(z);
                Node sdkMin = versionInfo.getAttributes().getNamedItem("android:minSdkVersion");
                Node sdkMax = versionInfo.getAttributes().getNamedItem("android:maxSdkVersion");

                if(sdkMin != null && sdkMax != null) {
                    int minVersion = Integer.parseInt(sdkMin.getNodeValue());
                    int maxVersion = Integer.parseInt(sdkMax.getNodeValue());

                    if(minVersion < 23 && maxVersion >= 23) {
                        inserted = true;
                        db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(), 14, fileName, true);
                    } else if(maxVersion < 23){ //db.insertReport(commitID, 15, manifest.toString(), "", LINE);
                        inserted = true;
                        db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),15, fileName, true);
                    } else if (minVersion >= 23){  //db.insertReport(commitID, 16, manifest.toString(), "", LINE);
                        inserted = true;
                        db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),16, fileName, true);
                    }

                } else if(sdkMax != null) {
                    int maxVersion = Integer.parseInt(sdkMax.getNodeValue());
                    if(maxVersion < 23){ //db.insertReport(commitID, 15, manifest.toString(), "", LINE);
                        inserted = true;
                        db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),15, fileName, true);
                    }
                }
            }
            if (!inserted) {
                ArrayList<ReportLog> previousOccurrencesUC14 = db.findOccurrenceFor(currentAppName, currentCommitGUID, 14, fileName);
                for (ReportLog report: previousOccurrencesUC14) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),14,
                            fileName, report.getMethod(), Integer.parseInt(report.getLine()), false);
                }
                ArrayList<ReportLog> previousOccurrencesUC15 = db.findOccurrenceFor(currentAppName, currentCommitGUID, 15, fileName);
                for (ReportLog report: previousOccurrencesUC15) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),15,
                            fileName, report.getMethod(), Integer.parseInt(report.getLine()), false);
                }
                ArrayList<ReportLog> previousOccurrencesUC16 = db.findOccurrenceFor(currentAppName, currentCommitGUID, 16, fileName);
                for (ReportLog report: previousOccurrencesUC16) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),16,
                            fileName, report.getMethod(), Integer.parseInt(report.getLine()), false);
                }
            }

            boolean insertedCam = false;
            boolean insertedSms = false;
            boolean insertedPho = false;

            for(int x=0; x<androidPerms.getLength(); x++) {
                Node perm = androidPerms.item(x);
                String permName = perm.getAttributes().getNamedItem("android:name").getNodeValue();

                if(permName.compareTo("android.permission.CAMERA") == 0){ //db.insertReport(commitID, 2, manifest.toString(), "", LINE);
                    insertedCam = true;
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),2, fileName, true);
                }
                if(permName.compareTo("android.permission.SEND_SMS") == 0){ //db.insertReport(commitID, 3, manifest.toString(), "", LINE);
                    insertedSms = true;
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),3, fileName, true);
                }
                if(permName.compareTo("android.permission.CALL_PHONE") == 0){ //db.insertReport(commitID, 4, manifest.toString(), "", LINE);
                    insertedPho = true;
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),4, fileName, true);
                }

                Node sdkMinVersion = perm.getAttributes().getNamedItem("android:minSdkVersion");
                Node sdkMaxVersion = perm.getAttributes().getNamedItem("android:maxSdkVersion");

                if (sdkMinVersion != null && sdkMaxVersion == null && Integer.parseInt(sdkMinVersion.getNodeValue()) < 23) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),9, fileName, true);
                } else if (db.existsUCOccurrence(currentAppName, currentCommitGUID,9, fileName, "")) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),9, fileName, false);
                }

                if (sdkMinVersion != null && sdkMaxVersion != null
                        && Integer.parseInt(sdkMinVersion.getNodeValue()) < 23 && Integer.parseInt(sdkMaxVersion.getNodeValue()) >= 23) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),10, fileName, true);
                } else if (db.existsUCOccurrence(currentAppName, currentCommitGUID,10, fileName, "")) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),10, fileName, false);
                }

                if (sdkMinVersion != null && Integer.parseInt(sdkMinVersion.getNodeValue()) >= 23) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),11, fileName, true);
                } else if (db.existsUCOccurrence(currentAppName, currentCommitGUID,11, fileName, "")) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),11, fileName, false);
                }

                if (sdkMaxVersion != null && Integer.parseInt(sdkMaxVersion.getNodeValue()) < 23) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),12, fileName, true);
                } else if (db.existsUCOccurrence(currentAppName, currentCommitGUID,12, fileName, "")) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),12, fileName, false);
                }

                if (sdkMinVersion == null && sdkMaxVersion != null && Integer.parseInt(sdkMaxVersion.getNodeValue()) >= 23) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),13, fileName, true);
                } else if (db.existsUCOccurrence(currentAppName, currentCommitGUID,13, fileName, "")) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),13, fileName, false);
                }

                /*if(sdkMinVersion != null){
                    int minVersion = Integer.parseInt(sdkMinVersion.getNodeValue());
                    inserted = true;

                    if(minVersion >= 23){//databaseController.insertReport(commitID, 11, MANIFEST_FILE, "", LINE);
                        db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),11, fileName, true);
                    } else if(sdkMaxVersion != null){
                        int maxVersion = Integer.parseInt(sdkMaxVersion.getNodeValue());
                        if(maxVersion >= 23){//databaseController.insertReport(commitID, 10, MANIFEST_FILE, "", LINE);
                            db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),10, fileName, true);
                        } else { //maxVersion < 23 //databaseController.insertReport(commitID, 12, MANIFEST_FILE, "", LINE);
                            db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),12, fileName, true);
                        }
                    } else { //minVersion < 23 //databaseController.insertReport(commitID, 9, MANIFEST_FILE, "", LINE);
                        db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),9, fileName, true);
                    }
                } else if(sdkMaxVersion != null) {
                    inserted = true;
                    int maxVersion = Integer.parseInt(sdkMaxVersion.getNodeValue());
                    if(maxVersion < 23){//databaseController.insertReport(commitID, 12, MANIFEST_FILE, "", LINE);
                        db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),12, fileName, true);
                    } else { // maxVersion >=23 //databaseController.insertReport(commitID, 13, MANIFEST_FILE, "", LINE);
                        db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),13, fileName, true);
                    }
                }*/
            }

            if (!insertedCam && db.existsUCOccurrence(currentAppName, currentCommitGUID,2, fileName, "")) {
                db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),2, fileName, false);
            }
            if (!insertedSms && db.existsUCOccurrence(currentAppName, currentCommitGUID,3, fileName, "")) {
                db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),3, fileName, false);
            }
            if (!insertedPho && db.existsUCOccurrence(currentAppName, currentCommitGUID,4, fileName, "")) {
                db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),4, fileName, false);
            }

            if (androidPerms.getLength() == 0) {
                for (ReportLog report: db.findOccurrenceFor(currentAppName, currentCommitGUID, 9, fileName)) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),9,
                            fileName, report.getMethod(), Integer.parseInt(report.getLine()), false);
                }
                for (ReportLog report: db.findOccurrenceFor(currentAppName, currentCommitGUID, 10, fileName)) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),10,
                            fileName, report.getMethod(), Integer.parseInt(report.getLine()), false);
                }
                for (ReportLog report: db.findOccurrenceFor(currentAppName, currentCommitGUID, 11, fileName)) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),11,
                            fileName, report.getMethod(), Integer.parseInt(report.getLine()), false);
                }
                for (ReportLog report: db.findOccurrenceFor(currentAppName, currentCommitGUID, 12, fileName)) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),12,
                            fileName, report.getMethod(), Integer.parseInt(report.getLine()), false);
                }
                for (ReportLog report: db.findOccurrenceFor(currentAppName, currentCommitGUID, 13, fileName)) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),13,
                            fileName, report.getMethod(), Integer.parseInt(report.getLine()), false);
                }
            }

            if (customPerms.getLength() > 0) { //db.insertReport(commitID, 8, fileName, "", LINE);
                db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),8, fileName, true);
            } else if (db.existsUCOccurrence(currentAppName, currentCommitGUID,8, fileName, "")) {
                db.insertNewReportLog(currentAppName, currentCommitGUID, manifest.getPath(),8, fileName, false);
            }

        } catch(Exception e) {
            System.err.println("Something went wrong parsing the Android Manifest XML: " + e);
            logError(manifest.getPath(), e.getMessage(), "analyzeManifest");
        }
    }

    private void analyzeSourceCode(List<MyFileParserVisitor> filesList) {

        for (MyFileParserVisitor currentFileVisitor : filesList) {
            try {
                String fileName = currentFileVisitor.file.getName();

                // ************ Fist validate the calls of shouldRequestPermission  ************
                for(MethodDeclaration methodInfo : currentFileVisitor.shouldRequestPermissionCalls) {
                    db.insertNewReportLog(currentAppName, currentCommitGUID,currentFileVisitor.file.getPath(),1,
                            fileName, methodInfo.getNameAsString(), methodInfo.getBegin().get().line, true);
                }

                if (currentFileVisitor.shouldRequestPermissionCalls.isEmpty()) {
                    ArrayList<ReportLog> previousOccurrences = db.findOccurrenceFor(currentAppName, currentCommitGUID, 1, fileName);
                    for (ReportLog report: previousOccurrences) {
                        db.insertNewReportLog(currentAppName, currentCommitGUID, currentFileVisitor.file.getPath(),1,
                                fileName, report.getMethod(), Integer.parseInt(report.getLine()), false);
                    }
                }

                // ************ Second, validate the calls of requestPermissions ************
                for(MethodDeclaration methodInfo : currentFileVisitor.requestPermissionsCalls) {
                    String methodName = methodInfo.getNameAsString();
                    // If method 'checkSelfPermission' is being called
                    boolean hasCheckSelfPermission = !currentFileVisitor.checkSelfPermissionCalls.isEmpty()
                            || getOuterSelfPermsCalls(currentFileVisitor.compilationUnit, filesList, methodInfo);

                    if (hasCheckSelfPermission) {
                        db.insertNewReportLog(currentAppName, currentCommitGUID, currentFileVisitor.file.getPath(),6,
                                fileName, methodName, methodInfo.getBegin().get().line, true);
                        if (db.existsUCOccurrence(currentAppName, currentCommitGUID,5, fileName, methodName)) {
                            db.insertNewReportLog(currentAppName, currentCommitGUID, currentFileVisitor.file.getPath(),5,
                                    fileName, methodName, methodInfo.getBegin().get().line, false);
                        }
                    } else {
                        db.insertNewReportLog(currentAppName, currentCommitGUID, currentFileVisitor.file.getPath(),5,
                                fileName, methodName, methodInfo.getBegin().get().line, true);
                        if (db.existsUCOccurrence(currentAppName, currentCommitGUID,6, fileName, methodName)) {
                            db.insertNewReportLog(currentAppName, currentCommitGUID, currentFileVisitor.file.getPath(),6,
                                    fileName, methodName, methodInfo.getBegin().get().line, false);
                        }
                    }

                    // ************ Third, validate the proximity of requestPermissions calls between each other ************
                    if (currentFileVisitor.areRequestCallsTooClose()) {
                        db.insertNewReportLog(currentAppName, currentCommitGUID, currentFileVisitor.file.getPath(),7,
                                fileName, methodName, methodInfo.getBegin().get().line, true);
                    } else if (db.existsUCOccurrence(currentAppName, currentCommitGUID,7, fileName, methodName)) {
                        db.insertNewReportLog(currentAppName, currentCommitGUID, currentFileVisitor.file.getPath(),7,
                                fileName, methodName, methodInfo.getBegin().get().line, false);
                    }
                }

                if (currentFileVisitor.requestPermissionsCalls.isEmpty()) {
                    for (ReportLog report: db.findOccurrenceFor(currentAppName, currentCommitGUID, 5, fileName)) {
                        db.insertNewReportLog(currentAppName, currentCommitGUID, currentFileVisitor.file.getPath(),5,
                                fileName, report.getMethod(), Integer.parseInt(report.getLine()), false);
                    }
                    for (ReportLog report: db.findOccurrenceFor(currentAppName, currentCommitGUID, 6, fileName)) {
                        db.insertNewReportLog(currentAppName, currentCommitGUID, currentFileVisitor.file.getPath(),6,
                                fileName, report.getMethod(), Integer.parseInt(report.getLine()), false);
                    }
                    for (ReportLog report: db.findOccurrenceFor(currentAppName, currentCommitGUID, 7, fileName)) {
                        db.insertNewReportLog(currentAppName, currentCommitGUID, currentFileVisitor.file.getPath(),7,
                                fileName, report.getMethod(), Integer.parseInt(report.getLine()), false);
                    }
                }

            } catch (Exception e) {
                System.err.println("Something went wrong parsing " + currentFileVisitor.file.getPath());
                logError(currentFileVisitor.file.getPath(), e.getMessage(), "analyzeSourceCode");
            }
        }
    }

    /**
     *
     * Checks when requestPermission is called within another source code file
     * @return the combination of [line, method_name] of a found occurrence
     */
    private boolean getOuterSelfPermsCalls(final CompilationUnit cu, final List<MyFileParserVisitor> parsedFiles,
                                                           final MethodDeclaration permissionMethodInfo) {
        final boolean[] foundDeclaration = {false};

        (new VoidVisitorAdapter<Object>() {
            @Override
            public void visit(MethodCallExpr methodCallExpr, Object arg) {
                // First, evaluate if the current methodCallExpr is between the line in which the "permissionRequest" was called
                // and the line where the method definition begins.
                int currentSearchLine = methodCallExpr.getBegin().get().line;

                if (currentSearchLine > permissionMethodInfo.getBegin().get().line
                 && currentSearchLine < permissionMethodInfo.getEnd().get().line) {

                    if (methodCallExpr.getScope().isPresent()) { // if the caller identifier is not null
                        String caller = methodCallExpr.getScope().get().toString() + ".java";
                        for (MyFileParserVisitor currentFile : parsedFiles) {
                            boolean foundFile = currentFile.file.getName().equals(caller);
                            boolean hasCheckSelfPermissionCalls = !currentFile.checkSelfPermissionCalls.isEmpty();
                            if (foundFile && hasCheckSelfPermissionCalls) {

                                // Check the declarations to validate if method names matches
                                for(MethodDeclaration mDeclaration : currentFile.checkSelfPermissionCalls) {
                                    //String mDeclaration = currentFile.checkSelfPermissionCalls.get(lineNum).split(",")[0];
                                    boolean matches = methodCallExpr.getNameAsString().equals(mDeclaration.getNameAsString());
                                    if (matches) {
                                        foundDeclaration[0] = true;
                                        return; // I only need the first occurrence match
                                    }
                                }
                            }
                        }
                    }
                }
                super.visit(methodCallExpr, arg);
            }

        }).visit(cu, null);
        return foundDeclaration[0];
    }

    void logError(String path, String errorMessage, String pLintMethod) {
        db.insertErrorLog(currentAppName, currentCommitGUID, path, errorMessage, pLintMethod);
    }

    public static void main(String[] args) throws IOException {
        long startTime = System.nanoTime();

        // Test Paths
        //String path = "/Users/virginia/Documents/RITPermSmells/tempo";
        //String path = "/Users/virginia/Documents/RITPermSmells/VirginiaProjects";
        //String path = "/Users/virginia/Documents/RITPermSmells/VirginiaProjectsDiffs";
        //String path = "/Volumes/GCCIS-CASCI/osara/Dataset/P/";

        String path = args[0];
        Path startingPath = Paths.get(path);

        db = new DBController();
        try {
            db.connectToDB();
            logData = db.alreadyExistAppCommits();
            Analyzer newAnalyzer = new Analyzer();
            newAnalyzer.traverseApps(startingPath);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.print("Issues connecting to results database: \n" + e);
            System.exit(0);
        } finally {
            try {
                db.dbPLintResultConnection.close();
                db.dbAppsDatasetConnection.close();
                db = null;
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