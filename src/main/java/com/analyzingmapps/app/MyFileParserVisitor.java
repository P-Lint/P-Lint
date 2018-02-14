package com.analyzingmapps.app;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.util.HashMap;

public class MyFileParserVisitor extends VoidVisitorAdapter {
    CompilationUnit compilationUnit;
    File file;
    HashMap<String, String> shouldRequestPermissionCalls = new HashMap<>();
    HashMap<String, String> checkSelfPermissionCalls = new HashMap<>();
    HashMap<String, String> requestPermissionsCalls = new HashMap<>();

    @Override
    public void visit(MethodCallExpr n, Object arg) {
        super.visit(n, arg);
        String beginLine = String.valueOf(n.getBegin().get().line);
        if(n.getNameAsString().compareTo("shouldShowRequestPermissionRationale") == 0){
            shouldRequestPermissionCalls.put(beginLine, getMethodName(compilationUnit, n));
        }
        if(n.getNameAsString().compareTo("checkSelfPermission") == 0){
            checkSelfPermissionCalls.put(beginLine, getMethodName(compilationUnit, n));
        }
        if(n.getNameAsString().compareTo("requestPermissions") == 0){
            requestPermissionsCalls.put(String.valueOf(beginLine), getMethodName(compilationUnit, n));
        }

    }

    public boolean isFileUsingPermissions() {
        return !shouldRequestPermissionCalls.isEmpty()
                || !checkSelfPermissionCalls.isEmpty()
                || !requestPermissionsCalls.isEmpty();
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
                    //System.out.println("El actual: " + declExpr.getNameAsString());
                    methodName[0] = declExpr.getNameAsString() + "," + declExpr.getBegin().get().line;
                }
            }
        }).visit(compilationUnit, null);
        return methodName[0];
    }

}