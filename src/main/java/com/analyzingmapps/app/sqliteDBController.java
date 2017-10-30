package com.analyzingmapps.app;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * Created by Colton Dennis on 10/4/16.
 */
public class sqliteDBController {
    //The db connection that allows for reads/updates
    public Connection dbConnection = null;

    //Establishes connection to database that exists in same directory
    public void connectToDB() throws ClassNotFoundException{
        Class.forName("org.sqlite.JDBC");

        try{
            dbConnection = DriverManager.getConnection("jdbc:sqlite:../results");//"jdbc:sqlite:results");
            dbConnection.setAutoCommit(false);
        }
        catch(SQLException se){
            System.out.println("[Error connecting to DB]: " + se);
        }
    }

    public void addApkToList(String apkName){
        try{
            int apkID = getApkID(apkName);
            if(apkID == -1){
                Statement stmt = dbConnection.createStatement();
                String sql = "INSERT INTO apks (apk_name) VALUES (\""+apkName+"\")";

                stmt.executeUpdate(sql);

                stmt.close();
                dbConnection.commit();
            }
        }
        catch(SQLException se){
            System.out.println("[Adding apk to db]: "+se);
        }
    }

    public int getApkID(String apkName){
        int apkID= -1;
        try{
            Statement stmt = dbConnection.createStatement();
            String sql = "SELECT apk_id FROM apks WHERE (apk_name = \"" + apkName + "\")";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next()){
                apkID = rs.getInt("apk_id");
            }
            rs.close();
            stmt.close();
        }
        catch(SQLException e){
            System.out.println("[Error getting APK ID for " + apkName +"]: "+e);
        }
        return apkID;
    }

    public String getApkName(int apkID){
        String apkName = "";
        try{
            Statement stmt = dbConnection.createStatement();
            String sql = "SELECT apk_name FROM apks WHERE (apk_id = " + apkID + ")";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next()){
                apkName = rs.getString("apk_name");
            }
            rs.close();
            stmt.close();
        }
        catch(SQLException se){
            System.out.println("[Error getting APK name]: " + se);
        }
        return apkName;
    }

    //Gets use case info from provided use case ID
    public HashMap<String, String> getUseCase(int useCaseId){
        HashMap<String, String> useCaseInfo = new HashMap<String, String>();

        try {
            Statement stmt = dbConnection.createStatement();
            String sql = "SELECT usecase_desc, usecase_type FROM use_cases WHERE (usecase_id = "+useCaseId+")";

            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String usecase_desc = rs.getString("usecase_desc");
                String usecase_type = rs.getString("usecase_type");

                useCaseInfo.put(usecase_desc, usecase_type);
            }
            rs.close();
            stmt.close();
        }
        catch (SQLException se){
            System.out.println("[Error getting Use Cases]: " + se);
        }
        return useCaseInfo;
    }

    public void insertReport(int apkID, int usecaseID, String fileName, int lineFound, String time){

        try {
            Statement stmt = dbConnection.createStatement();
            String sql = "INSERT INTO reports (apk_id, last_run, usecase_id, file_name, line_found ) " +
                    "VALUES (" + apkID + ", \"" + time + "\", " + usecaseID + ", \"" + fileName+"\", "+lineFound+")";
            stmt.executeUpdate(sql);

            stmt.close();

            updateLatestResults(apkID, time);
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
        }
    }

    public ArrayList<String> getReport(int apkID, String lastRun){
        ArrayList<String> report = new ArrayList<String>();
        HashMap<String, Integer> foundUseCases = new HashMap<String, Integer>();

        try{
            Statement stmt = dbConnection.createStatement();
            String sql = "SELECT usecase_id, file_name, line_found FROM reports " +
                    "WHERE (apk_id = " + apkID + " AND last_run = \"" + lastRun + "\")";

            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next()){
                int usecaseId = rs.getInt("usecase_id");
                HashMap<String, String> usecaseInfo = getUseCase(usecaseId);

                String fileName = rs.getString("file_name");
                int lineFound = rs.getInt("line_found");

                String key = usecaseInfo.keySet().toArray()[0].toString();
//                String reportStr =
//                        "Usecase: " + key +
//                        "\nType: " + usecaseInfo.get(key);
//                if(lineFound > 0){
//                    reportStr += "\nFilename: " + fileName +"\nLine found: " + lineFound + "\n";
//                }
//                else{
//                    reportStr += "\nFilename: AndroidManifest.xml\n";
//                    if(fileName.compareTo("AndroidManifest.xml")!=0){
//                        reportStr += "Custom Perm: " + fileName + "\n";
//                    }
//                }
//                report.add(reportStr);

                if(!foundUseCases.containsKey(key)){
                    foundUseCases.put(key, 1);
                }
                else{
                    int currCount = foundUseCases.get(key) + 1;
                    foundUseCases.put(key, currCount);
                }
            }
            rs.close();
            stmt.close();
            for(String useCase: foundUseCases.keySet()){
                String reportStr = "Usecase: \n" + useCase + "\nOccurrences: \n" + foundUseCases.get(useCase) + "\n\n";
                report.add(reportStr);
            }
        }
        catch ( Exception e ) {
            System.err.println("[Error getting report]: " + e.getMessage() );
        }

        return report;
    }

    public void updateLatestResults(int apkID, String lastRun){
        try{
            Statement chkStmt = dbConnection.createStatement();
            String sql = "SELECT * FROM latest_results WHERE (apk_id = " + apkID + ")";

            ResultSet rs = chkStmt.executeQuery(sql);
            if(!rs.next()){
                chkStmt.close();
                Statement stmt = dbConnection.createStatement();

                sql = "INSERT INTO latest_results (apk_id, last_run) VALUES (" +apkID +", \""+ lastRun+"\")";
                stmt.executeUpdate(sql);
                stmt.close();
            }
            else{
                chkStmt.close();
                Statement stmt = dbConnection.createStatement();

                sql = "UPDATE latest_results SET last_run = \""+ lastRun +"\" WHERE apk_id = " + apkID ;
                stmt.executeUpdate(sql);
            }

            dbConnection.commit();
            rs.close();
        }
        catch(SQLException se){
            System.out.println("[Error updating latest results table]: "+se);
        }
    }

    public HashMap<String, ArrayList<String>> getLatestResults(){
        HashMap<String, ArrayList<String>> results = new HashMap<String, ArrayList<String>>();

        try{
            Statement stmt = dbConnection.createStatement();
            String sql = "SELECT apk_id, last_run FROM latest_results";

            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                int apkID = rs.getInt("apk_id");
                String lastRun = rs.getString("last_run");

                String apkName = getApkName(apkID);
                ArrayList<String> report = getReport(apkID, lastRun);

                results.put(apkName, report);
            }
            rs.close();
            stmt.close();
        }
        catch ( SQLException e ) {
            System.err.println("[Error getting latest results]: " + e.getMessage() );
        }
        return results;
    }

}
