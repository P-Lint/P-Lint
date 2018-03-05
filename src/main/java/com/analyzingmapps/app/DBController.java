package com.analyzingmapps.app;

import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;


/**
 * Created by Colton Dennis on 10/4/16.
 */
public class DBController {
    //The db connection that allows for reads/updates
    public Connection dbPLintResultConnection = null;
    public Connection dbAppsDatasetConnection = null;
    String timestamp;

    //Establishes connection to database that exists in same directory
    public void connectToDB() throws ClassNotFoundException{
        Class.forName("org.sqlite.JDBC");

        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date lastRun = new Date();
        timestamp = df.format(lastRun);

        try {
            dbAppsDatasetConnection = DriverManager.getConnection("jdbc:sqlite:android-dataset.sqlite");
            dbPLintResultConnection = DriverManager.getConnection("jdbc:sqlite:results.sqlite");//"jdbc:sqlite:../results" "jdbc:sqlite:results");

            dbPLintResultConnection.setAutoCommit(false);
            dbAppsDatasetConnection.setAutoCommit(false);
        }
        catch(SQLException se){
            System.out.println("[Error connecting to DB]: " + se);
        }
    }

    public ArrayList<GitCommit> getAppCommitLogs(){
        ArrayList<GitCommit> data = new ArrayList<>();
//
//        Object[] apps = getInsertedAppList().toArray();
//        StringBuilder builder = new StringBuilder();
//
//        for( int i = 0 ; i < apps.length; i++ ) {
//            builder.append("?,");
//        }
//        if (builder.length() > 0)
//            builder.deleteCharAt(builder.length() -1);

        try {
            String sql = "SELECT APP, COMMIT_SHA, AUTHOR_EMAIL, AUTHOR_DATE_TICKS  " +
                    "FROM git_commit_log " +
                    //"WHERE APP NOT IN ("+builder.toString()+") " +
                    "ORDER BY APP, AUTHOR_DATE_TICKS ";
            PreparedStatement pstmt = dbAppsDatasetConnection.prepareStatement(sql);
//            int index = 1;
//            for( Object item : apps ) {
//                pstmt.setObject( index++, item );
//            }

            ResultSet rs = pstmt.executeQuery();

            while(rs.next()) {
                String app = rs.getString("APP");
                String commitId = rs.getString("COMMIT_SHA");
                String author = rs.getString("AUTHOR_EMAIL");

                GitCommit gitCommit = new GitCommit(app, commitId, author);
                data.add(gitCommit);
                //insertAppCommit(gitCommit);
            }

            rs.close();
            pstmt.close();
        } catch ( Exception e ) {
            e.printStackTrace();
            System.err.println("[Error getting getAppCommitLogs]: " + e.getMessage() );
        }

        return data;
    }

    public void insertAppCommit(GitCommit info){
        try{
            Statement stmt = dbPLintResultConnection.createStatement();
            String sql = "INSERT INTO commit_log (app_name,commit_id,author) " +
                    "VALUES (\""+info.getApp()+"\",\""+info.getCommitSha1()+"\",\""+info.getAuthor()+"\")";

            stmt.executeUpdate(sql);

            dbPLintResultConnection.commit();
            stmt.close();
        }
        catch(SQLException se){
            System.out.println("[error insertAppCommit]: "+se);
        }
    }

    // Gets the files that exists in a given commit and app name
    public ArrayList<GitCommitFile> getCommitFileList(String appName, String commitGUI){
        ArrayList<GitCommitFile> data = new ArrayList<>();
        try {
            Statement stmt = dbAppsDatasetConnection.createStatement();
            String sql = "SELECT APP, COMMIT_SHA, PATH, OLD_PATH, STATUS " +
                    "FROM git_commit_file_OnlyJavaAndManifestFiles " +
                    "WHERE STATUS != 'Deleted' AND APP = '" + appName + "' AND COMMIT_SHA = '" + commitGUI + "' ";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next()) {
                GitCommitFile file = new GitCommitFile(
                        rs.getString("APP"),
                        rs.getString("COMMIT_SHA"),
                        rs.getString("PATH"),
                        rs.getString("OLD_PATH"),
                        rs.getString("STATUS"));
                data.add(file);
            }
            rs.close();
            stmt.close();
            //System.out.println("Finished executing getCommitFileList: "+data.size()+" files found.");

        } catch ( Exception e ) {
            System.err.println("[Error getting getCommitFileList]: " + e.getMessage() );
        }

        return data;
    }

    /*
     * Queries related to App Commits
     */

    public ArrayList<String> getAppList(){
        ArrayList<String> data = new ArrayList<>();

        try {
            Statement stmt = dbAppsDatasetConnection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT name FROM APP");
            int count = 0;
            while(rs.next()) {
                //int dataSetAppId = rs.getInt("id");
                String appName = rs.getString("name");
                addAppToList(appName);

                //data.put(String.valueOf(appName),appName);
                data.add(appName);
                count++;
            }
            System.out.println(count + " Apps to analyze");

            rs.close();
            stmt.close();
        } catch ( Exception e ) {
            System.err.println("[Error getting getDatasetList]: " + e.getMessage() );
        }

        return data;
    }

    // Gets the commits associated with the App
    public ArrayList<String> getApkList(String appName){
        ArrayList<String> data = new ArrayList<>();
        try {

            Statement stmt = dbAppsDatasetConnection.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT APP, SHA1, AuthorEmail FROM commit_log WHERE (APP = \"" + appName + "\") ORDER BY CommitTime ");

            int count = 0;
            while(rs.next()) {
                String apkGUID = rs.getString("SHA1");
                String apkAuthor = rs.getString("AuthorEmail");
                data.add(apkGUID);

                addApkToList(apkGUID, appName, apkAuthor);
                count++;
            }
            System.out.println(count + " commits in "+ appName);
            rs.close();
            stmt.close();


        } catch ( Exception e ) {
            System.err.println("[Error getting getDatasetList]: " + e.getMessage() );
        }

        return data;
    }

    /*
     * Queries related to App source
     */

    public void addApkToList(String apkName,String appUniqueName, String author){
        try{
            int apkID = getApkID(apkName);
            if(apkID == -1){
                Statement stmt = dbPLintResultConnection.createStatement();
                String sql = "INSERT INTO apks (apk_name,app_name,apk_author_name) VALUES (\""+apkName+"\",\""+appUniqueName+"\",\""+author+"\")";

                stmt.executeUpdate(sql);

                dbPLintResultConnection.commit();
                stmt.close();
            }
        }
        catch(SQLException se){
            System.out.println("[Adding apk to db]: "+se);
        }
    }

    public void addAppToList(String appName){
        try{
            String foundApp = getAppName(appName);
            if(foundApp == null) {
                Statement stmt = dbPLintResultConnection.createStatement();
                String sql = "INSERT INTO apps (app_name) VALUES (\""+appName+"\")";

                stmt.executeUpdate(sql);
                dbPLintResultConnection.commit();
                stmt.close();
            }
        }
        catch(SQLException se){
            System.out.println("[Adding app to db]: "+se);
        }
    }

    public String getAppName(String appName){
        String foundApp = null;
        try{
            Statement stmt = dbPLintResultConnection.createStatement();
            String sql = "SELECT app_name FROM apps WHERE (app_name = \"" + appName + "\")";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next()) {
                foundApp = rs.getString("app_name");
            }
            rs.close();
            stmt.close();
        }
        catch(SQLException e){
            System.out.println("[Error getting APP ID for " + appName +"]: "+e);
        }
        return foundApp;
    }

    public int getApkID(String apkName){
        int apkID= -1;
        try{
            Statement stmt = dbPLintResultConnection.createStatement();
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
            Statement stmt = dbPLintResultConnection.createStatement();
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
            Statement stmt = dbPLintResultConnection.createStatement();
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

/*    public void insertReport(int apkID, int usecaseID, String fileName, String methodName, int lineFound) {
        insertReport(apkID, usecaseID, fileName, methodName, lineFound, timestamp);
    }

    public void insertReport(int apkID, int usecaseID, String fileName, String methodName, int lineFound, String time) {

        try {
            Statement stmt = dbPLintResultConnection.createStatement();
            String sql = "INSERT INTO reports (apk_id, last_run, usecase_id, file_name, method_name, line_found ) " +
                    "VALUES (" + apkID + ", \"" + time + "\", " + usecaseID + ", \"" + fileName + "\", \"" + methodName + "\", " +lineFound+")";
            stmt.executeUpdate(sql);

            stmt.close();

            updateLatestResults(apkID, time);
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
        }
    }
*/

    public void insertNewReportLog(String appName, String commitGUI, String path, int useCaseId, String fileName, boolean status) {
        insertNewReportLog(appName, commitGUI, path, useCaseId, fileName, "", -1, status);
    }

    public void insertNewReportLog(String appName, String commitGUI, String path, int useCaseId, String fileName, String methodName, int lineFound, boolean status) {

        try {
            String statusKey = status ? "X" : "O";
            Statement stmt = dbPLintResultConnection.createStatement();
            String sql = "INSERT INTO report_log " +
                    "(app_name, commit_id, absolute_path, file_path,method_name,line_found,usecase_id,status,run_time) " +
                    "VALUES ( '"+appName+"', '"+commitGUI+"', '"+path+"', '"+fileName+"','"+methodName+"', "
                    +lineFound+", "+useCaseId+", '"+ statusKey  +"', "+System.currentTimeMillis()+")";

            stmt.executeUpdate(sql);
            dbPLintResultConnection.commit();
            stmt.close();

        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
        }
    }


    public boolean existsUCOccurrence(String appName, String commitId, int useCaseId, String fileName, String methodName) {
        return existsUCOccurrence(appName, commitId, useCaseId, fileName, methodName, "X");
    }

    public boolean existsUCOccurrence(String appName, String commitId, int useCaseId, String fileName, String methodName, String status) {
        long lastRunTimeWithFix = getLastFixedReported(appName, commitId, useCaseId, fileName); // last runtime of reported use_case with 'O'
        String sql = null;
        try {
            Statement stmt = dbPLintResultConnection.createStatement();
            sql = "SELECT status FROM report_log "
                    + " WHERE app_name = '"+appName+"'"
                    + " AND usecase_id = " + useCaseId
                    + " AND file_path = '" + fileName + "'"
                    + " AND method_name = '" + methodName + "'"
                    + " AND commit_id != '"+commitId+"'"
                    + " AND status = '"+status+"'"
                    + " AND run_time > " + lastRunTimeWithFix // AND RUNTIME > the last run time reported with case 'O'
                    + " ORDER BY run_time DESC LIMIT 1 " ;

            ResultSet rs = stmt.executeQuery(sql);
            return rs.next();

        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() + "\n" + sql);
        }
        return false;
    }

    public ArrayList<ReportLog> findOccurrenceFor(String appName, String commitId, int useCaseId, String fileName) {
        long lastRunTimeWithFix = getLastFixedReported(appName, commitId, useCaseId, fileName); // last runtime of reported use_case with 'O'
        String sql = null;

        ArrayList<ReportLog> list = new ArrayList<>();
        try {
            Statement stmt = dbPLintResultConnection.createStatement();
            sql = "SELECT commit_id, file_path, method_name, line_found FROM report_log "
                    + " WHERE app_name = '"+appName+"'"
                    + " AND usecase_id = " + useCaseId
                    + " AND file_path = '" + fileName + "'"
                    + " AND commit_id != '"+commitId+"'"
                    + " AND status = 'X'"
                    + " AND run_time > " + lastRunTimeWithFix // AND RUNTIME > the last run time reported with case 'O'
                    + " ORDER BY run_time DESC ";

            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                list.add(new ReportLog(
                        rs.getString("commit_id"),
                        rs.getString("file_path"),
                        rs.getString("method_name"),
                        rs.getString("line_found")));
            }
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() + "\n" + sql);
        }

        return list;
    }


    /* Gets last runtime reported with 'O' for the given file|method|uc */
    private long getLastFixedReported(String appName, String commitId, int useCaseId, String fileName) {
        String sql = null;
        try {
            Statement stmt = dbPLintResultConnection.createStatement();
            sql = "SELECT run_time FROM report_log "
                    + " WHERE app_name = '"+appName+"'"
                    + " AND usecase_id = " + useCaseId
                    + " AND file_path = '" + fileName + "'"
                    //+ " AND method_name = '" + methodName + "'"
                    + " AND commit_id != '"+commitId+"'"
                    + " AND status = 'O'"
                    + " ORDER BY run_time DESC LIMIT 1 " ;

            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getLong("run_time");
            }

        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() + "\n" + sql);
        }
        return -1;
    }

    public ArrayList<String> getReport(int apkID, String lastRun){
        ArrayList<String> report = new ArrayList<String>();
        HashMap<String, Integer> foundUseCases = new HashMap<String, Integer>();

        try{
            Statement stmt = dbPLintResultConnection.createStatement();
            String sql = "SELECT usecase_id, file_name, line_found FROM reports " +
                    "WHERE (apk_id = " + apkID + " AND last_run = \"" + lastRun + "\")";

            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next()){
                int usecaseId = rs.getInt("usecase_id");
                HashMap<String, String> usecaseInfo = getUseCase(usecaseId);

                String fileName = rs.getString("file_name");
                int lineFound = rs.getInt("line_found");

                String key = usecaseInfo.keySet().toArray()[0].toString();

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
            Statement chkStmt = dbPLintResultConnection.createStatement();
            String sql = "SELECT * FROM latest_results WHERE (apk_id = " + apkID + ")";

            ResultSet rs = chkStmt.executeQuery(sql);
            if(!rs.next()){
                chkStmt.close();
                Statement stmt = dbPLintResultConnection.createStatement();

                sql = "INSERT INTO latest_results (apk_id, last_run) VALUES (" +apkID +", \""+ lastRun+"\")";
                stmt.executeUpdate(sql);
                stmt.close();
            }
            else{
                chkStmt.close();
                Statement stmt = dbPLintResultConnection.createStatement();

                sql = "UPDATE latest_results SET last_run = \""+ lastRun +"\" WHERE apk_id = " + apkID ;
                stmt.executeUpdate(sql);
            }

            dbPLintResultConnection.commit();
            rs.close();
        }
        catch(SQLException se){
            System.out.println("[Error updating latest results table]: "+se);
        }
    }

    public HashMap<String, ArrayList<String>> getLatestResults(){
        HashMap<String, ArrayList<String>> results = new HashMap<String, ArrayList<String>>();

        try{
            Statement stmt = dbPLintResultConnection.createStatement();
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


    public List<LogItem> alreadyExistAppCommits() {
        String sql = null;
        List<LogItem> logItemList = new ArrayList<>();
        LogItem logItem;

        try {
            Statement stmt = dbPLintResultConnection.createStatement();
            sql = "SELECT * FROM analyzed_apps_log ";

            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                logItem = new LogItem(rs.getString("app_name"),rs.getString("commit_name"));
                logItemList.add(logItem);
            }
            rs.close();
            stmt.close();

        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() + "\n" + sql);
        }
        return logItemList;
    }

    public boolean alreadyExistAppCommit(String appName, String commitId) {
        String sql = null;
        try {
            Statement stmt = dbPLintResultConnection.createStatement();
            sql = "SELECT app_name FROM analyzed_apps_log "
                    + " WHERE app_name = '"+appName+"'"
                    + " AND commit_name  = '"+commitId+"'";

            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return true;
            }

        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() + "\n" + sql);
        }
        return false;
    }

    public void insertErrorLog(String appName, String commit, String path, String errorMessage, String pLintMethod) {

        try {
            Statement stmt = dbPLintResultConnection.createStatement();
            String sql = "INSERT INTO error_log (app_name, apk_id, file_path, error_message, plint_method_name) " +
                    "VALUES (\"" + appName + "\", \"" + commit + "\", \"" + path + "\", '" + errorMessage + "', \"" + pLintMethod + "\")";
            stmt.executeUpdate(sql);
            dbPLintResultConnection.commit();
            stmt.close();
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
        }
    }

    public void insertCompletionLog(String appName, String commit) {
        String sql = "";
        try {
            Statement stmt = dbPLintResultConnection.createStatement();
            sql = "INSERT INTO analyzed_apps_log (app_name, commit_name, use_cases)  " +
                    "VALUES ( '"+appName+"', '"+commit+"',  " +
                    "(SELECT count(*) FROM report_log WHERE app_name = '"+appName+"' AND commit_id = '"+commit+"') )";
            stmt.executeUpdate(sql);
            dbPLintResultConnection.commit();
            stmt.close();
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() + "\n" + sql);
        }
    }

    public void insertFilesPatch(String appName, String commit, String path){
        try{
            Statement stmt = dbAppsDatasetConnection.createStatement();
            String sql = "INSERT INTO git_patch (APP, COMMIT_SHA, PATH) " +
                    "VALUES ('"+appName+"', '"+commit+"', '"+path+"')";

            stmt.executeUpdate(sql);
            dbAppsDatasetConnection.commit();
            stmt.close();
        }
        catch(SQLException se){
            System.out.println("[insertFilesPatch]: "+se);
        }
    }

    public void updateReportFileNames(String appName, String oldName, String newName){
        try{
            Statement stmt = dbPLintResultConnection.createStatement();
            String sql = "UPDATE report_log SET file_path = '"+newName+"' " +
                    "WHERE file_path = '"+oldName+"' AND app_name = '"+appName+"' ";

            stmt.executeUpdate(sql);
            dbPLintResultConnection.commit();
            stmt.close();
        }
        catch(SQLException se){
            System.out.println("[updateReportFileNames]: "+se);
        }
    }

    // Returns a list of apps that have been inserted so far.
    public ArrayList<String> getInsertedAppList() {
        ArrayList<String> data = new ArrayList<>();

        try {
            Statement stmt = dbPLintResultConnection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT app_name FROM analyzed_apps_log GROUP BY app_name");
            while(rs.next()) {
                String appName = rs.getString("app_name");
                data.add(appName);
            }
            rs.close();
            stmt.close();
        } catch ( Exception e ) {
            System.err.println("[Error getting getInsertedAppList]: " + e.getMessage() );
        }

        return data;
    }
}

class GitCommit {
    private String app;
    private String commitSha1;
    private String author;

    public GitCommit(String app, String commitSha1, String author) {
        this.app = app;
        this.commitSha1 = commitSha1;
        this.author = author;
    }

    public String getApp() {
        return app;
    }

    public String getCommitSha1() {
        return commitSha1;
    }

    public String getAuthor() {
        return author;
    }
}

class GitCommitFile {
    private String app;
    private String commit_sha1;
    private String path;
    private String oldPath;
    private String status;

    public GitCommitFile(String app, String commit_sha1, String path, String oldPath, String status) {
        this.app = app;
        this.commit_sha1 = commit_sha1;
        this.path = path;
        this.oldPath = oldPath;
        this.status = status;
    }

    public String getPath() {
        return path.replace("\\", Analyzer.PATH_SEPARATOR);
    }

    public String getOldPath() {
        return oldPath.replace("\\", Analyzer.PATH_SEPARATOR);
    }

    public String getStatus() {
        return status;
    }

}

class ReportLog {
    private String commitId;
    private String file;
    private String method;
    private String line;

    public ReportLog(String commitId, String file, String method, String line) {
        this.commitId = commitId;
        this.file = file;
        this.method = method;
        this.line = line;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getFile() {
        return file;
    }

    public String getMethod() {
        return method;
    }

    public String getLine() {
        return line;
    }
}