package com.analyzingmapps.app;

import java.io.*;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.text.SimpleDateFormat;
import java.util.TimeZone;


/**
 * Created by Colton Dennis on 10/4/16.
 */
public class analyzer {
//    private HashMap<String, ArrayList<HashMap<String, Object>>> finalResults = new HashMap<String, ArrayList<HashMap<String, Object>>>();
    /*
    Goes through folder of decompiled APK's
     */
    public void traverseApks(File apkDir, String timestamp, sqliteDBController dbCont){
        String[] apks = apkDir.list();

        for(String apkName : apks){
            dbCont.addApkToList(apkName);

            File appDir = new File(apkDir, apkName + "/app");
            analyzeManifest(appDir, apkName, dbCont, timestamp);

            File srcCodeDir = new File(appDir, "src/android/support/v4");
            traverseSrcCode(apkName, srcCodeDir, timestamp, dbCont);
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
                        dbCont.insertReport(apkID, 1, fileName, Integer.parseInt(lineNum), timestamp);
                    }
                }

                HashMap<String, String> fileRequestCalls = getRequestPermsCalls(currentDir, currentDir.getName());
                HashMap<String, String> fileCheckPermCalls = getSelfPermsCalls(currentDir, currentDir.getName());

                if(fileRequestCalls != null){
                    ArrayList<Integer> reqsCalled = new ArrayList<Integer>();

                    for(String lineNum: fileRequestCalls.keySet()){
                        int line = Integer.parseInt(lineNum);
                        reqsCalled.add(line);

                        if(fileCheckPermCalls == null){
                            dbCont.insertReport(apkID, 5, fileName, line, timestamp);
                        }
                        else{
                            dbCont.insertReport(apkID, 6, fileName, line, timestamp);
                        }

                    }

                    for(int calls: reqsCalled){
                        if(reqsCalled.contains(calls+1)
                                || reqsCalled.contains(calls-1)
                                || reqsCalled.contains(calls+2)
                                || reqsCalled.contains(calls-2)
                                || reqsCalled.contains(calls+3)
                                || reqsCalled.contains(calls-3)){
                            dbCont.insertReport(apkID, 7, fileName, calls, timestamp);
                        }
                    }
                }
            }
        }

    }

    /*
    Searches AndroidManifest.xml files
    */
    public void analyzeManifest(File projectDir, String apkName, sqliteDBController dbCont, String timestamp){
        File manifest = new File(projectDir, "AndroidManifest.xml");
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
                            dbCont.insertReport(apkID, 14, "AndroidManifest.xml", -1, timestamp);
                        }
                        else if(maxVersion < 23){
                            dbCont.insertReport(apkID, 15, "AndroidManifest.xml", -1, timestamp);
                        }
                        else if(minVersion >= 23){
                            dbCont.insertReport(apkID, 16, "AndroidManifest.xml", -1, timestamp);
                        }
                    }
                }
                if(sdkMax != null){
                    int maxVersion = Integer.parseInt(sdkMax.getNodeValue());
                    if(maxVersion < 23){
                        dbCont.insertReport(apkID, 15, "AndroidManifest.xml", -1, timestamp);
                    }
                }
            }
            for(int x=0; x<androidPerms.getLength(); x++) {
                Node perm = androidPerms.item(x);
                String permName = perm.getAttributes().getNamedItem("android:name").getNodeValue();

                if(permName.compareTo("android.permission.CAMERA")==0){
                    dbCont.insertReport(apkID, 2, "AndroidManifest.xml", -1, timestamp);
                }
                else if(permName.compareTo("android.permission.SEND_SMS")==0){
                    dbCont.insertReport(apkID, 3, "AndroidManifest.xml", -1, timestamp);
                }
                else if(permName.compareTo("android.permission.CALL_PHONE")==0){
                    dbCont.insertReport(apkID, 4, "AndroidManifest.xml", -1, timestamp);
                }


                Node sdkMinVersion = perm.getAttributes().getNamedItem("android:minSdkVersion");
                Node sdkMaxVersion = perm.getAttributes().getNamedItem("android:maxSdkVersion");
                if(sdkMinVersion != null){
                    int minVersion = Integer.parseInt(sdkMinVersion.getNodeValue());

                    if(minVersion >= 23){
                        dbCont.insertReport(apkID, 11, "AndroidManifest.xml", -1, timestamp);
                    }
                    else if(sdkMaxVersion != null){
                        int maxVersion = Integer.parseInt(sdkMaxVersion.getNodeValue());
                        if(minVersion < 23 && maxVersion >= 23){
                            dbCont.insertReport(apkID, 10, "AndroidManifest.xml", -1, timestamp);
                        }
                        else if(maxVersion < 23){
                            dbCont.insertReport(apkID, 12, "AndroidManifest.xml", -1, timestamp);
                        }
                    }
                    else{
                        if(minVersion < 23){
                            dbCont.insertReport(apkID, 9, "AndroidManifest.xml", -1, timestamp);
                        }
                    }
                }

                else if(sdkMaxVersion != null){
                    int maxVersion = Integer.parseInt(sdkMaxVersion.getNodeValue());
                    if(maxVersion < 23){
                        dbCont.insertReport(apkID, 12, "AndroidManifest.xml", -1, timestamp);
                    }
                    else if(maxVersion >=23){
                        dbCont.insertReport(apkID, 13, "AndroidManifest.xml", -1, timestamp);
                    }
                }

            }

            for(int y=0; y<customPerms.getLength(); y++){
                Node customPerm = customPerms.item(y);
                String customName = customPerm.getAttributes().getNamedItem("android:name").getNodeValue();
                dbCont.insertReport(apkID, 8, customName, -1, timestamp);
            }

        }
        catch(IOException ioe){System.out.println("Could not find AndroidManifest.xml for " + apkName);}
        catch(Exception e){System.out.println("Something went wrong parsing the Android Manifest XML: " + e);}
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
            grc.visit(cu, fileName);

            if(grc.foundInstance){
                return grc.fileRationaleMap;
            }

        }
        catch(FileNotFoundException fnfe){System.out.println(fnfe);}
        catch(ParseException pe){}//ignore, since it's the app developer's fault
        catch(IOException ioe){System.out.println(ioe);
        }
        return null;
    }


    /*
    JavaParser visitor for finding specific method calls
     */
    public class getRationaleCallsVisitor extends VoidVisitorAdapter{
        private HashMap<String, String> fileRationaleMap = new HashMap<String, String>();
        private boolean foundInstance = false;

        @Override
        public void visit(MethodCallExpr n, Object arg){

            if(n.getName().compareTo("shouldShowRequestPermissionRationale")==0){
                if(!foundInstance){
                    foundInstance = true;
                }

                fileRationaleMap.put(String.valueOf(n.getBegin().line), n.getName());
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
            grp.visit(cu, fileName);
            if(grp.foundInstance){
                return grp.fileRequestMap;
            }
        }
        catch(FileNotFoundException fnfe){System.out.println(fnfe);}
        catch(ParseException pe){}//ignore, since it's the app developer's fault
        catch(IOException ioe){System.out.println(ioe);}

        return null;
    }

    /*
    JavaParser visitor that searches source code for specific method calls
     */
    public class getRequestPermsVisitor extends VoidVisitorAdapter{
        private HashMap<String, String> fileRequestMap = new HashMap<String, String>();
        private boolean foundInstance = false;

        @Override
        public void visit(MethodCallExpr n, Object arg){
            if(n.getName().compareTo("requestPermissions")==0){
                if(!foundInstance){
                    foundInstance = true;
                }

                fileRequestMap.put(String.valueOf(n.getBegin().line), n.getName());
            }

            super.visit(n, arg);
        }
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
            gsp.visit(cu, fileName);
            if(gsp.foundInstance){
                return gsp.fileCheckSelfMap;
            }

        }
        catch(FileNotFoundException fnfe){System.out.println(fnfe);}
        catch(ParseException pe){}//ignore, since it's the app developer's fault
        catch(IOException ioe){System.out.println(ioe);}

        return null;
    }

    /*
    JavaParser visitor for finding method declarations
     */
    public class getSelfPermsVisitor extends VoidVisitorAdapter{
        private HashMap<String, String> fileCheckSelfMap = new HashMap<String, String>();
        private boolean foundInstance = false;

        @Override
        public void visit(MethodDeclaration n, Object arg){
            if(n.getName().compareTo("checkSelfPermission")==0){
                if(!foundInstance){
                    foundInstance = true;
                }
                fileCheckSelfMap.put(String.valueOf(n.getBegin().line), n.getName());
            }

            super.visit(n, arg);
        }
    }



    // args[0] points to relative decompiled apk path: "./apk-decompiler/uncompressed-apks"
    public static void main(String[] args){
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));

        sqliteDBController dbCont = new sqliteDBController();
        try{
            dbCont.connectToDB();
        }
        catch(Exception e){
            System.out.print("Issues connecting to results database: \n" + e);
            System.exit(0);
        }

        System.out.println("Analyzing apks...");
        File apkDir = new File( args[0] );
        try{
            Date lastRun = new Date();
            String timestamp = df.format(lastRun);

            analyzer newAnalyzer = new analyzer();
            newAnalyzer.traverseApks(apkDir, timestamp, dbCont);
        }
        finally{
            try{
                dbCont.dbConnection.close();
            }
            catch(SQLException se){
                System.out.print(se);
            }
        }
    }
}
