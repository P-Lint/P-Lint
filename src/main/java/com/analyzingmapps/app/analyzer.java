package com.analyzingmapps.app;

import java.io.*;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.*;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import org.w3c.dom.Document;

import javax.activation.UnsupportedDataTypeException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.text.SimpleDateFormat;


/**
 * Created by Colton Dennis on 10/4/16.
 */
public class analyzer {
    File parentDirectory;
    String currentAppName;
    String currentCommitGUID;
    static sqliteDBController databaseController;

    /*
    Goes through folder of decompiled APK's
     */
    public void traverseApks(File apkDir, String timestamp, sqliteDBController dbCont){
        ArrayList<String> apps = dbCont.getAppList();
        for (String appName: apps) {
            ArrayList<String> apkList = dbCont.getApkList(appName); // List of commits per App
            for (String commitGUID: apkList) {
                File appDir = new File(apkDir, appName + "/"+commitGUID);
                if (appDir.exists()) {
                    parentDirectory = appDir;
                    currentAppName = appName;
                    currentCommitGUID = commitGUID;
                    System.out.println("Analyzing "+ appDir + " ...");
                    traverseManifests(appDir, commitGUID, dbCont, timestamp);
                    traverseSrcCode(commitGUID, appDir, timestamp, dbCont);
                }
            }
        }

        HashMap<String, ArrayList<String>> latestResults = dbCont.getLatestResults();

        try{
            File resultFile = new File("results.html");
            BufferedWriter bw = new BufferedWriter(new FileWriter(resultFile));

            bw.write("<html>");
            bw.write("<body>");
            bw.write("<h1>Results</h1>");

            for(String apkResults : latestResults.keySet()){
                bw.write("<h3> " + apkResults + " </h3>");
                bw.write("<textarea>");
                for(String result: latestResults.get(apkResults)){
                    bw.write(result );
                }
                bw.write("</textarea>");
            }
            bw.write("</body>");
            bw.write("</html>");

            bw.close();

        }
        catch (IOException ioe){
            System.out.println("IO issue: " + ioe +"\nResults printed to console instead of HTML file");
            for(String apkResults : latestResults.keySet()){
                System.out.println("\nAPK Name: " + apkResults);
                for(String result: latestResults.get(apkResults)){
                    System.out.println(result);
                }
            }
        }

        try{
            DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            df.setTimeZone(TimeZone.getTimeZone("UTC"));

            Date finishTime = new Date();
            Date startTime = df.parse(timestamp);

            long timeElapsed = finishTime.getTime() - startTime.getTime();


            System.out.println("\n[Finished in " + timeElapsed + "ms]");
        }
        catch(java.text.ParseException pe){
            System.out.println("[Error getting elapsed time: " + pe + "]");
        }


    }

    /*
    Goes through common locations for source code in Android apps
     */
    private void traverseSrcCode(String apkName, File currentDir, String timestamp, sqliteDBController dbCont){
        int apkID = dbCont.getApkID(apkName);

        if (currentDir.isDirectory()) {
            String[] children = currentDir.list();
            for (int i = 0; children != null && i < children.length; i++) {
                traverseSrcCode(apkName, new File(currentDir, children[i]), timestamp, dbCont);
            }
        }
        else if (currentDir.isFile()) {
            if (currentDir.getName().endsWith(".java")){
                String fileName = currentDir.getName();
                HashMap<String, String> fileRationaleCalls = getRationaleCalls(currentDir, currentDir.getName());
                if(fileRationaleCalls != null){
                    for(String lineNum : fileRationaleCalls.keySet()){
                        String methodName = fileRationaleCalls.get(lineNum);
                        dbCont.insertReport(apkID, 1, fileName, methodName, Integer.parseInt(lineNum), timestamp);
                    }
                }

                HashMap<String, String> fileRequestCalls = getRequestPermsCalls(currentDir, currentDir.getName());
                HashMap<String, String> fileCheckPermCalls = getSelfPermsCalls(currentDir, currentDir.getName());

                if(fileRequestCalls != null){
                    ArrayList<Integer> reqsCalled = new ArrayList<Integer>();

                    for(String lineNum: fileRequestCalls.keySet()){

                        int line = Integer.parseInt(lineNum);
                        reqsCalled.add(line);

                        String methodCombination=fileRequestCalls.get(lineNum);

                        String methodName = methodCombination.split(",")[0];

                        if (fileCheckPermCalls == null) {
                            HashMap<String, String> outerFileCheckPermCalls = getOuterSelfPermsCalls(currentDir, apkName, line,methodCombination.split(",")[1]);
                            if (outerFileCheckPermCalls.isEmpty()) {
                                dbCont.insertReport(apkID, 5, fileName, methodName, line, timestamp);
                            } else {
                                dbCont.insertReport(apkID, 6, fileName, methodName, line, timestamp);
                            }
                        } else {
                            dbCont.insertReport(apkID, 6, fileName, methodName, line, timestamp);
                        }
                    }

                    for(int calls: reqsCalled){
                        if(reqsCalled.contains(calls+1)
                                || reqsCalled.contains(calls-1)
                                || reqsCalled.contains(calls+2)
                                || reqsCalled.contains(calls-2)
                                || reqsCalled.contains(calls+3)
                                || reqsCalled.contains(calls-3)){
                            String methodName = fileRequestCalls.get(String.valueOf(calls)).split(",")[0];
                            dbCont.insertReport(apkID, 7, fileName, methodName, calls, timestamp);
                        }
                    }
                }
            }
        }

    }


    private void traverseManifests(File projectDir, String apkName, sqliteDBController dbCont, String timestamp) {
        searchPathsFound = null;
        searchPathsFound = new ArrayList<>();
        if (projectDir.isDirectory()) {
            search(projectDir, "AndroidManifest.xml");
        }

        // Analyze manifest if there is only one found
        //System.out.println("BASE PATH " + projectDir);
        //System.out.println("Found AndroidManifest \n " + searchPathsFound);
        if (searchPathsFound.size() == 1) {
            //System.out.println("Found AndroidManifest first attempt \n " + searchPathsFound.get(0));
            analyzeManifest(searchPathsFound.get(0), apkName, dbCont, timestamp);
        } else if (searchPathsFound.size() > 1) {
            // If there is more, cleanup the list
            List<File> unwantedFiles = new ArrayList<>();
            for (File item: searchPathsFound) {
                if (item.getPath().contains("/bin/") ||
                        item.getPath().contains("/lib/") ||
                        item.getPath().contains("/test/") ||
                        item.getPath().contains("/debug/") ||
                        item.getPath().contains("fdroid/") ||
                        item.getPath().contains("/build/")) {
                    unwantedFiles.add(item);
                }
            }
            searchPathsFound.removeAll(unwantedFiles);
            //If there is more than one, search for src/main folder
            if (searchPathsFound.size() > 1) {
                //System.out.println("Found AndroidManifest cleaned list \n " + searchPathsFound);
                boolean foundIt = false;
                for (File item: searchPathsFound) {
                    if (item.getPath().contains("/src/main/")) {
                        analyzeManifest(item, apkName, dbCont, timestamp);
                        foundIt = true;
                        break;
                    }
                }
                if (!foundIt) {
                    int apkID = dbCont.getApkID(apkName);
                    //17: Manifest not identified, manually verification needed
                    dbCont.insertReport(apkID, 17, "AndroidManifest.xml", "",-1, timestamp);
                }
            } else {
                // Analyze manifest if there is only one found
                //System.out.println("Found AndroidManifest after clean \n " + searchPathsFound.get(0));
                analyzeManifest(searchPathsFound.get(0), apkName, dbCont, timestamp);
            }
        }
    }

    /*
    Searches AndroidManifest.xml files
    */
    private void analyzeManifest(File manifest, String apkName, sqliteDBController dbCont, String timestamp){
        //File manifest = new File(projectDir, "/AndroidManifest.xml");
        int apkID = dbCont.getApkID(apkName);

        try {
            DocumentBuilderFactory fctr = DocumentBuilderFactory.newInstance();
            DocumentBuilder bldr = fctr.newDocumentBuilder();
            Document manifestXML = bldr.parse(manifest);

            NodeList sdkVers = manifestXML.getElementsByTagName("uses-sdk");
            NodeList androidPerms = manifestXML.getElementsByTagName("uses-permission");
            NodeList customPerms = manifestXML.getElementsByTagName("permission");

            for(int z=0; z<sdkVers.getLength(); z++){
                Node versInfo = sdkVers.item(z);
                Node sdkMin = versInfo.getAttributes().getNamedItem("android:minSdkVersion");
                Node sdkMax = versInfo.getAttributes().getNamedItem("android:maxSdkVersion");

                if(sdkMin != null){
                    int minVersion = Integer.parseInt(sdkMin.getNodeValue());
                    if(sdkMax != null){
                        int maxVersion = Integer.parseInt(sdkMax.getNodeValue());
                        if(minVersion < 23 && maxVersion >=23){
                            dbCont.insertReport(apkID, 14, "AndroidManifest.xml", "", -1, timestamp);
                        }
                        else if(maxVersion < 23){
                            dbCont.insertReport(apkID, 15, "AndroidManifest.xml", "", -1, timestamp);
                        }
                        else if(minVersion >= 23){
                            dbCont.insertReport(apkID, 16, "AndroidManifest.xml", "", -1, timestamp);
                        }
                    }
                }
                if(sdkMax != null){
                    int maxVersion = Integer.parseInt(sdkMax.getNodeValue());
                    if(maxVersion < 23){
                        dbCont.insertReport(apkID, 15, "AndroidManifest.xml", "", -1, timestamp);
                    }
                }
            }
            for(int x=0; x<androidPerms.getLength(); x++) {
                Node perm = androidPerms.item(x);
                String permName = perm.getAttributes().getNamedItem("android:name").getNodeValue();

                if(permName.compareTo("android.permission.CAMERA")==0){
                    dbCont.insertReport(apkID, 2, "AndroidManifest.xml", "", -1, timestamp);
                }
                else if(permName.compareTo("android.permission.SEND_SMS")==0){
                    dbCont.insertReport(apkID, 3, "AndroidManifest.xml", "", -1, timestamp);
                }
                else if(permName.compareTo("android.permission.CALL_PHONE")==0){
                    dbCont.insertReport(apkID, 4, "AndroidManifest.xml", "", -1, timestamp);
                }


                Node sdkMinVersion = perm.getAttributes().getNamedItem("android:minSdkVersion");
                Node sdkMaxVersion = perm.getAttributes().getNamedItem("android:maxSdkVersion");
                if(sdkMinVersion != null){
                    int minVersion = Integer.parseInt(sdkMinVersion.getNodeValue());

                    if(minVersion >= 23){
                        dbCont.insertReport(apkID, 11, "AndroidManifest.xml", "", -1, timestamp);
                    }
                    else if(sdkMaxVersion != null){
                        int maxVersion = Integer.parseInt(sdkMaxVersion.getNodeValue());
                        if(minVersion < 23 && maxVersion >= 23){
                            dbCont.insertReport(apkID, 10, "AndroidManifest.xml", "", -1, timestamp);
                        }
                        else if(maxVersion < 23){
                            dbCont.insertReport(apkID, 12, "AndroidManifest.xml", "", -1, timestamp);
                        }
                    }
                    else{
                        if(minVersion < 23){
                            dbCont.insertReport(apkID, 9, "AndroidManifest.xml", "", -1, timestamp);
                        }
                    }
                }

                else if(sdkMaxVersion != null){
                    int maxVersion = Integer.parseInt(sdkMaxVersion.getNodeValue());

                    if(maxVersion < 23){
                        dbCont.insertReport(apkID, 12, "AndroidManifest.xml", "", -1, timestamp);
                    }
                    else if(maxVersion >=23){
                        dbCont.insertReport(apkID, 13, "AndroidManifest.xml", "", -1, timestamp);
                    }
                }

            }

            for(int y=0; y<customPerms.getLength(); y++){
                Node customPerm = customPerms.item(y);
                String customName = customPerm.getAttributes().getNamedItem("android:name").getNodeValue();
                dbCont.insertReport(apkID, 8, "AndroidManifest.xml", "",-1, timestamp);
            }

        }
        catch(IOException ioe){
            System.out.println("Could not read AndroidManifest.xml for " + manifest.getPath());
            logError(manifest.getPath(), ioe.getMessage(), "analyzeManifest");
        }
        catch(Exception e){
            System.out.println("Something went wrong parsing the Android Manifest XML: " + e);
            logError(manifest.getPath(), e.getMessage(), "analyzeManifest");
        }
    }


    /*
    Finds references to shouldShowRequestPermissionRationale calls in source code
     */
    public HashMap<String, String> getRationaleCalls(File projectDir, String fileName){
        FileInputStream in;
        try{
            in = new FileInputStream(projectDir);
            CompilationUnit cu;
            cu=JavaParser.parse(in);
            in.close();

            getRationaleCallsVisitor grc = new getRationaleCallsVisitor();
            grc.compilationUnit = cu;
            grc.visit(cu, fileName);

            if(grc.foundInstance){
                return grc.fileRationaleMap;
            }

        }
        catch(FileNotFoundException fnfe){
            System.out.println(fnfe);
            logError(projectDir.getPath(), fnfe.getMessage(), "getRationaleCalls");
        }
        catch (UnsupportedOperationException e) {
            System.out.println("getRationaleCalls syntax error");
            logError(projectDir.getPath(), e.getMessage(), "getRationaleCalls");
        } //ignore, since it's the app developer's fault
        catch(ParseException pe){
            System.out.println("getRationaleCalls syntax error " + projectDir);
            logError(projectDir.getPath(), pe.getMessage(), "getRationaleCalls");
        }//ignore, since it's the app developer's fault
        catch(IOException ioe){
            System.out.println(ioe);
            logError(projectDir.getPath(), ioe.getMessage(), "getRationaleCalls");
        }
        catch(Exception e) {
            System.out.println("Something went wrong parsing getRationaleCalls " + projectDir);
            logError(projectDir.getPath(), e.getMessage(), "getRationaleCalls");
        }
        return null;
    }


    /*
    JavaParser visitor for finding specific method calls
     */
    public class getRationaleCallsVisitor extends VoidVisitorAdapter{
        public CompilationUnit compilationUnit;
        private HashMap<String, String> fileRationaleMap = new HashMap<String, String>();
        private boolean foundInstance = false;


        @Override
        public void visit(MethodCallExpr n, Object arg){

            if(n.getName().compareTo("shouldShowRequestPermissionRationale")==0){
                if(!foundInstance){
                    foundInstance = true;
                }
                String methodName = getMethodName(compilationUnit, n);
                fileRationaleMap.put(String.valueOf(n.getBegin().line), methodName);
            }
            super.visit(n, arg);
        }
    }

    /*
    Checks when requestPermission is called within source code
     */
    public HashMap<String, String> getRequestPermsCalls(File projectDir, String fileName){
        FileInputStream in;
        try{
            in = new FileInputStream(projectDir);
            CompilationUnit cu;
            cu=JavaParser.parse(in);
            in.close();

            getRequestPermsVisitor grp = new getRequestPermsVisitor();
            grp.compilationUnit = cu;
            grp.visit(cu, fileName);
            if(grp.foundInstance){
                return grp.fileRequestMap;
            }
        }
        catch(FileNotFoundException fnfe){
            System.out.println(fnfe);
            logError(projectDir.getPath(), fnfe.getMessage(), "getRequestPermsCalls");
        }
        catch (UnsupportedOperationException e) {
            System.out.println("getRequestPermsCalls syntax error");
            logError(projectDir.getPath(), e.getMessage(), "getRequestPermsCalls");
        } //ignore, since it's the app developer's fault
        catch(ParseException pe){
            System.out.println("getRequestPermsCalls syntax error " + projectDir);
            logError(projectDir.getPath(), pe.getMessage(), "getRequestPermsCalls");
        }//ignore, since it's the app developer's fault
        catch(IOException ioe){
            System.out.println(ioe);
            logError(projectDir.getPath(), ioe.getMessage(), "getRequestPermsCalls");
        }
        catch(Exception e) {
            System.out.println("Something went wrong parsing getRequestPermsCalls " + projectDir);
            logError(projectDir.getPath(), e.getMessage(), "getRequestPermsCalls");
        }

        return null;
    }

    /*
    JavaParser visitor that searches source code for specific method calls
     */
    public class getRequestPermsVisitor extends VoidVisitorAdapter{
        public CompilationUnit compilationUnit;
        private HashMap<String, String> fileRequestMap = new HashMap<String, String>();
        private boolean foundInstance = false;

        @Override
        public void visit(final MethodCallExpr callExpr, Object arg){
            if(callExpr.getName().compareTo("requestPermissions")==0){
                if(!foundInstance){
                    foundInstance = true;
                }

                String methodName = getMethodName(compilationUnit, callExpr);
                fileRequestMap.put(String.valueOf(callExpr.getBegin().line), methodName);
            }

            super.visit(callExpr, arg);
        }
    }

    /*
    Checks when requestPermission is called within another source code
    */
    public HashMap<String, String> getOuterSelfPermsCalls(final File projectDir, String apkName, final int permReqLineNum, String methodLine) {
//        System.out.println("Analyzing  Outer SelfPermsCalls *************************************** ");

        final HashMap<String, String> fileCheckSelfMap = new HashMap<String, String>();
        final int searchLimitLine = permReqLineNum - Integer.parseInt(methodLine);
        FileInputStream in;

        try {
            in = new FileInputStream(projectDir);
            final CompilationUnit cu = JavaParser.parse(in);
            final List<ImportDeclaration> cuImports = cu.getImports();
            in.close();

            (new VoidVisitorAdapter<Object>() {
                @Override
                public void visit(final MethodCallExpr methodCallExpr, Object arg) {
                    if (methodCallExpr.getBegin().line < permReqLineNum && methodCallExpr.getBegin().line > searchLimitLine) {
                        if (methodCallExpr.getScope() != null) {
                            for (ImportDeclaration itemImport : cuImports) {
                                if (itemImport.getName() != null && methodCallExpr.getScope() != null) {
                                    if (itemImport.getName().getName().equals(methodCallExpr.getScope().toString())) {
                                        String fileName = itemImport.getName().getName().toString()  + ".java";
                                        //System.out.println(fileName);
                                        searchPathsFound = null;
                                        searchPathsFound = new ArrayList<>();
                                        search(parentDirectory, fileName);

                                        if (searchPathsFound.size() > 0) {
                                            File innerFile = searchPathsFound.get(0);

                                            if (innerFile.exists()) {
                                                //System.out.println("Found Class == " + innerFile.getAbsolutePath());
                                                try {
                                                    final CompilationUnit innerCU = JavaParser.parse(innerFile);
                                                    (new VoidVisitorAdapter<Object>() {
                                                        @Override
                                                        public void visit(MethodDeclaration mDeclaration, Object arg) {
                                                            if (methodCallExpr.getName() != null && mDeclaration.getName() != null) {
                                                                if (methodCallExpr.getName().equals(mDeclaration.getName())) {
                                                                    for (Statement stmt : mDeclaration.getBody().getStmts()) {
                                                                        if (stmt.toString().contains("checkSelfPermission")) {
                                                                            fileCheckSelfMap.put(String.valueOf(mDeclaration.getBegin().line), mDeclaration.getName());
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            super.visit(mDeclaration, arg);
                                                        }
                                                    }).visit(innerCU, null);
                                                }
                                                catch(Exception e) {
                                                    System.out.println("Something went wrong parsing getOuterSelfPermsCalls " + projectDir);
                                                    logError(projectDir.getPath(), e.getMessage(), "getOuterSelfPermsCalls");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    super.visit(methodCallExpr, arg);
                }
            }).visit(cu, null);
        }
        catch(FileNotFoundException fnfe){
            System.out.println(fnfe);
            logError(projectDir.getPath(), fnfe.getMessage(), "getOuterSelfPermsCalls");
        }
        catch (UnsupportedOperationException e) {
            System.out.println("getOuterSelfPermsCalls syntax error");
            logError(projectDir.getPath(), e.getMessage(), "getOuterSelfPermsCalls");
        } //ignore, since it's the app developer's fault
        catch(ParseException pe){
            System.out.println("getOuterSelfPermsCalls syntax error " + projectDir);
            logError(projectDir.getPath(), pe.getMessage(), "getOuterSelfPermsCalls");
        }//ignore, since it's the app developer's fault
        catch(IOException ioe){
            System.out.println(ioe);
            logError(projectDir.getPath(), ioe.getMessage(), "getOuterSelfPermsCalls");
        }
        catch(Exception e) {
            System.out.println("Something went wrong parsing getOuterSelfPermsCalls " + projectDir);
            logError(projectDir.getPath(), e.getMessage(), "getOuterSelfPermsCalls");
        }

//        System.out.println("END Analyzing  Outer SelfPermsCalls ***************************************");
//        System.out.println("Found " + fileCheckSelfMap);
//        System.out.println("END Analyzing  Outer SelfPermsCalls ***************************************");
        return fileCheckSelfMap;
    }

    /*
    Checks source code for checkSelfPermission calls
     */
    public HashMap<String, String> getSelfPermsCalls(File projectDir, String fileName){
        FileInputStream in;
        try{
            in = new FileInputStream(projectDir);
            CompilationUnit cu;
            cu=JavaParser.parse(in);
            in.close();

            getSelfPermsVisitor gsp = new getSelfPermsVisitor();
            gsp.compilationUnit = cu;
            gsp.visit(cu, fileName);
            if(gsp.foundInstance){
                return gsp.fileCheckSelfMap;
            }

        }
        catch(FileNotFoundException fnfe){
            System.out.println(fnfe);
            logError(projectDir.getPath(), fnfe.getMessage(), "getSelfPermsCalls");
        }
        catch (UnsupportedOperationException e) {
            System.out.println("getSelfPermsCalls syntax error");
            logError(projectDir.getPath(), e.getMessage(), "getSelfPermsCalls");
        } //ignore, since it's the app developer's fault
        catch(ParseException pe){
            System.out.println("getSelfPermsCalls syntax error " + projectDir);
            logError(projectDir.getPath(), pe.getMessage(), "getSelfPermsCalls");
        }//ignore, since it's the app developer's fault
        catch(IOException ioe){
            System.out.println(ioe);
            logError(projectDir.getPath(), ioe.getMessage(), "getSelfPermsCalls");
        }
        catch(Exception e) {
            System.out.println("Something went wrong parsing getSelfPermsCalls " + projectDir);
            logError(projectDir.getPath(), e.getMessage(), "getSelfPermsCalls");
        }

        return null;
    }

    /*
    JavaParser visitor for finding method declarations
     */
    public class getSelfPermsVisitor extends VoidVisitorAdapter{
        public CompilationUnit compilationUnit;
        private HashMap<String, String> fileCheckSelfMap = new HashMap<String, String>();
        private boolean foundInstance = false;

        @Override
        public void visit(MethodCallExpr n, Object arg){
            if(n.getName().compareTo("checkSelfPermission")==0){
                if(!foundInstance){
                    foundInstance = true;
                }
                String methodName = getMethodName(compilationUnit, n);
                fileCheckSelfMap.put(String.valueOf(n.getBegin().line), methodName);
            }

            super.visit(n, arg);
        }
    }

    private String getMethodName(CompilationUnit compilationUnit, final MethodCallExpr callExpr) {
        final String[] methodName = {""}; // Necesary in order to have the final attribute >_<
        // Evaluate the CompilationUnit, now searching for the methods' definitions to identify the parent
        (new VoidVisitorAdapter<Object>() {
            @Override
            public void visit(MethodDeclaration declExpr, Object arg) {
                super.visit(declExpr, arg);

                //System.out.println("[L:"+n.getBegin().line+"] " + n.getName());
                if (declExpr.getBegin().line < callExpr.getBegin().line)
                    methodName[0] = declExpr.getName()+","+declExpr.getBegin().line;
                else
                    return;
            }
        }).visit(compilationUnit, null);
        return methodName[0];
    }

    /*
    Search particular file recursively
     */
    private List<File> searchPathsFound = new ArrayList();
    private void search(File file, String name) {
        if (file.isDirectory()) {
            if (file.canRead()) {
                for (File temp : file.listFiles()) {
                    if (temp.isDirectory()) {
                        search(temp, name);
                    } else {
                        if (name.equals(temp.getName())) {
                            searchPathsFound.add(temp);
                        }

                    }
                }

            }
        }

    }

    private void logError(String path, String errorMessage, String pLintMethod) {
        databaseController.insertLog(currentAppName, currentCommitGUID, path, errorMessage, pLintMethod);
    }

    // args[0] points to relative decompiled apk path: "./apk-decompiler/uncompressed-apks"
    public static void main(String[] args){
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));

        sqliteDBController dbCont = new sqliteDBController();
        try{
            dbCont.connectToDB();
            databaseController = dbCont;
        }
        catch(Exception e){
            System.out.print("Issues connecting to results database: \n" + e);
            System.exit(0);
        }

        System.out.println("Analyzing apks...");
        try{
            Date lastRun = new Date();
            String timestamp = df.format(lastRun);

            analyzer newAnalyzer = new analyzer();

            newAnalyzer.traverseApks(new File( args[0] ), timestamp, dbCont);
        }
        finally{
            try{
                dbCont.dbPLintResultConnection.close();
                dbCont.dbAppsDatasetConnection.close();
                databaseController = null;
            }
            catch(SQLException se) {
                System.out.print(se);
            }
        }
    }
}
