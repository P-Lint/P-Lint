package com.analyzingmapps.app;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class MyFileParserVisitor extends VoidVisitorAdapter {
    CompilationUnit compilationUnit;
    File file;
    Set<MethodDeclaration> shouldRequestPermissionCalls = new HashSet<>();
    Set<MethodDeclaration> checkSelfPermissionCalls = new HashSet<>();
    Set<MethodDeclaration> requestPermissionsCalls = new HashSet<>();
    HashSet<Integer> requestPermissionsCallLine = new HashSet<>();

    @Override
    public void visit(MethodCallExpr n, Object arg) {
        super.visit(n, arg);
        String beginLine = String.valueOf(n.getBegin().get().line);
        if(n.getNameAsString().compareTo("shouldShowRequestPermissionRationale") == 0){
            //shouldRequestPermissionCalls.put(beginLine, getMethodName(compilationUnit, n));
            shouldRequestPermissionCalls.add(getMethodName(compilationUnit, n));
        }
        if(n.getNameAsString().compareTo("checkSelfPermission") == 0
                || n.getNameAsString().compareTo("checkCallingOrSelfPermission") == 0
                || n.getNameAsString().compareTo("checkCallingPermission") == 0
                || n.getNameAsString().compareTo("checkPermission") == 0){
            checkSelfPermissionCalls.add(getMethodName(compilationUnit, n));
            //checkSelfPermissionCalls.put(beginLine, getMethodName(compilationUnit, n));
        }
        if(n.getNameAsString().compareTo("requestPermissions") == 0){
            requestPermissionsCalls.add(getMethodName(compilationUnit, n));
            requestPermissionsCallLine.add(n.getBegin().get().line);
        }
    }

    boolean isFileUsingPermissions() {
        return !shouldRequestPermissionCalls.isEmpty()
                || !checkSelfPermissionCalls.isEmpty()
                || !requestPermissionsCalls.isEmpty();
    }

    boolean areRequestCallsTooClose() {
        for (Integer line : requestPermissionsCallLine) {
            if (requestPermissionsCallLine.contains(line+1) || requestPermissionsCallLine.contains(line-1) ||
                    requestPermissionsCallLine.contains(line+2) || requestPermissionsCallLine.contains(line-2) ||
                    requestPermissionsCallLine.contains(line+3) || requestPermissionsCallLine.contains(line-3) ) {
                return true;
            }
        }
        return false;
    }

    private MethodDeclaration getMethodName(CompilationUnit compilationUnit, final MethodCallExpr callExpr) {
        final MethodDeclaration[] methodName = {null}; // Necessary in order to have the final keyword  >_<
        // Evaluate the CompilationUnit, now searching for the methods' definitions to identify the parent
        (new VoidVisitorAdapter<Object>() {
            @Override
            public void visit(MethodDeclaration declExpr, Object arg) {
                super.visit(declExpr, arg);
                //System.out.println("[L:"+n.getBegin().line+"] " + n.getName()); //declExpr.getNameAsString()
                if (declExpr.getBegin().get().line < callExpr.getBegin().get().line) {
                    methodName[0] = declExpr;
                }
            }
        }).visit(compilationUnit, null);
        return methodName[0];
    }
}
