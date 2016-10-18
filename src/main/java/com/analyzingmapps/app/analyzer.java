package com.analyzingmapps.app;

import java.io.*;
import java.util.HashMap;
import java.util.ArrayList;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;



/**
 * Created by Colton Dennis on 10/4/16.
 */
public class analyzer {
    private HashMap<String, ArrayList<HashMap<String, Object>>> finalResults = new HashMap<String, ArrayList<HashMap<String, Object>>>();
    /*
    Goes through folder of decompiled APK's
     */
    public void traverseApks(File apkDir){
        String[] apks = apkDir.list();

        for(String apkName : apks){
            File appDir = new File(apkDir, apkName + "/app");
            getIntents(appDir, apkName);

            File srcCodeDir = new File(appDir, "src/android/support/v4");
            traverseSrcCode(apkName, srcCodeDir);
        }

        for(String apkKey: finalResults.keySet()){
            System.out.println("APK Name: " + apkKey);
            boolean calledRequestPerm = false;
            boolean calledCheckPerm = false;

            for(HashMap<String, Object> fileResults: finalResults.get(apkKey)){
                for(String fileKey: fileResults.keySet()){
                    boolean calledReqThisFile = false;
                    boolean reqUseCaseTriggered = false;
                    ArrayList<Integer> reqsCalled = new ArrayList<Integer>();

                    if(fileKey.compareTo("AndroidManifest.xml")==0){
                        System.out.println("\tFile Name:"+ fileKey+ "\n\t\tUse Case: Intent usage possible \n\t\t\tPermission: " +fileResults.get(fileKey));
                    }
                    else{
                        HashMap<String, String> linesFound = (HashMap<String, String>)fileResults.get(fileKey);
                        for(String lineNum: linesFound.keySet()){
                            if(linesFound.get(lineNum).compareTo("requestPermissions")==0){
                                if(!calledRequestPerm){
                                    calledRequestPerm = true;
                                }

                                if(!calledReqThisFile){
                                    calledReqThisFile = true;
                                    reqsCalled.add(Integer.parseInt(lineNum));
                                }
                                else if(!reqUseCaseTriggered){
                                    for(Integer lineCalled: reqsCalled){
                                        if(reqsCalled.contains(lineCalled+1) || reqsCalled.contains(lineCalled+2) || reqsCalled.contains(lineCalled+3) ||
                                           reqsCalled.contains(lineCalled-1) || reqsCalled.contains(lineCalled-2) || reqsCalled.contains(lineCalled-3)){
                                            reqUseCaseTriggered = true;

                                            System.out.print("\tFile Name: "+ fileKey +"\n\t\tUse Case: Multiple Permission Requests made in close succession");
                                        }
                                    }
                                }
                            }
                            else if(linesFound.get(lineNum).compareTo("checkSelfPermission")==0 && !calledCheckPerm){
                                calledCheckPerm = true;
                            }
                            else{
                                System.out.println("\tFile Name: " + fileKey+"\n\t\tUse Case: " + linesFound.get(lineNum) + "\n\t\tLine: " + lineNum );
                            }

                        }
                    }

                }
            }
            if(calledRequestPerm && calledCheckPerm){
                System.out.println("\tUse Case: APK requests permission, is set up to check self if permission given");
            }
            else if(calledRequestPerm && !calledCheckPerm){
                System.out.println("\tUse Case: APK requests permission, but doesn't check if permission is given");
            }
        }

    }

    /*
    Goes through common locations for source code in Android apps
     */
    private void traverseSrcCode(String apkName, File currentDir){
        ArrayList<HashMap<String, Object>> apkFoundInstances = new ArrayList<HashMap<String, Object>>();

        if (currentDir.isDirectory()) {
            String[] children = currentDir.list();
            for (int i = 0; children != null && i < children.length; i++) {
                traverseSrcCode(apkName, new File(currentDir, children[i]));
            }
        }
        else if (currentDir.isFile()) {
            if (currentDir.getName().endsWith(".java")){
                HashMap<String, Object> apkRationaleCalls = new HashMap<String, Object>();
                HashMap<String, Object> apkRequestCalls = new HashMap<String, Object>();
                HashMap<String, Object> apkCheckPermCalls = new HashMap<String, Object>();

                HashMap<String, String> fileRationaleCalls = getRationaleCalls(currentDir, currentDir.getName());
                if(fileRationaleCalls != null){
                    apkRationaleCalls.put(currentDir.getName(), fileRationaleCalls);
                    if(finalResults.containsKey(apkName)){
                        finalResults.get(apkName).add(apkRationaleCalls);
                    }
                    else{
                        apkFoundInstances.add(apkRationaleCalls);
                        finalResults.put(apkName, apkFoundInstances);
                    }
                }

                HashMap<String, String> fileRequestCalls = getRequestPermsCalls(currentDir, currentDir.getName());
                if(fileRequestCalls != null){
                    apkRequestCalls.put(currentDir.getName(), fileRequestCalls);
                    if(finalResults.containsKey(apkName)){
                        finalResults.get(apkName).add(apkRequestCalls);
                    }
                    else{
                        apkFoundInstances.add(apkRequestCalls);
                        finalResults.put(apkName, apkFoundInstances);
                    }
                }

                HashMap<String, String> fileCheckPermCalls = getSelfPermsCalls(currentDir, currentDir.getName());
                if(fileCheckPermCalls != null){
                    apkCheckPermCalls.put(currentDir.getName(), fileCheckPermCalls);
                    if(finalResults.containsKey(apkName)){
                        finalResults.get(apkName).add(apkCheckPermCalls);
                    }
                    else{
                        apkFoundInstances.add(apkCheckPermCalls);
                        finalResults.put(apkName, apkFoundInstances);
                    }
                }

            }
        }

    }

    /*
    Searches AndroidManifest.xml files for dangerous permission declarations that could possibly be replaced with intents
     */
    public void getIntents(File projectDir, String apkName){
        File manifest = new File(projectDir, "AndroidManifest.xml");
        ArrayList<HashMap<String, Object>> apkFoundInstances = new ArrayList<HashMap<String, Object>>();
        HashMap<String, Object> apkManifestPerms = new HashMap<String, Object>();



        try {
            BufferedReader br = new BufferedReader(new FileReader(manifest));
            String xmlString;
            StringBuilder sb = new StringBuilder();

            while((xmlString=br.readLine())!= null){
                sb.append(xmlString.trim());
            }
            br.close();
            String finalXML = sb.toString();



            if(finalXML.contains("android.permission.CAMERA")){
                if(finalResults.containsKey(apkName)){
                    apkManifestPerms.put("AndroidManifest.xml", "android.permission.CAMERA");
                    finalResults.get(apkName).add(apkManifestPerms);
                }
                else{
                    apkFoundInstances.add(apkManifestPerms);
                    finalResults.put(apkName, apkFoundInstances);
                }
                //System.out.println(apkName + "'s AndroidManifest.xml Contains a CAMERA permission, could possibly be replaced by intent");
            }
            if(finalXML.contains("android.permission.SEND_SMS")){
                if(finalResults.containsKey(apkName)){
                    apkManifestPerms.put("AndroidManifest.xml", "android.permission.SEND_SMS");
                    finalResults.get(apkName).add(apkManifestPerms);
                }
                else{
                    apkFoundInstances.add(apkManifestPerms);
                    finalResults.put(apkName, apkFoundInstances);
                }
                //System.out.println(apkName + "'s AndroidManifest.xml Contains a subset of the SMS permission, could possibly be replaced by intent");
            }
            if(finalXML.contains("android.permission.CALL_PHONE")){
                if(finalResults.containsKey(apkName)){
                    apkManifestPerms.put("AndroidManifest.xml", "android.permission.CALL_PHONE");
                    finalResults.get(apkName).add(apkManifestPerms);
                }
                else{
                    apkFoundInstances.add(apkManifestPerms);
                    finalResults.put(apkName, apkFoundInstances);
                }
                //System.out.println(apkName + "'s AndroidManifest.xml Contains a subset of the PHONE permission, could possibly be replaced by intent");
            }

        }
        catch(IOException ioe){System.out.println("Could not find AndroidManifest.xml for " + apkName);}
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

    public static void main(String[] args){
        System.out.println("Analyzing apks...");
        // args[0] points to relative decompiled apk path: "./apk-decompiler/uncompressed-apks"
        File apkDir = new File( args[0] );
        try{
            PrintStream out = new PrintStream(new FileOutputStream("./target/output.txt"));
            System.setOut(out);

            analyzer newAnalyzer = new analyzer();
            newAnalyzer.traverseApks(apkDir);

            out.close();
        }
        catch(FileNotFoundException fnfe){
            System.out.print("Couldn't find report file :(");
        }
    }
}
