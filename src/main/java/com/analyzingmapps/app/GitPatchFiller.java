package com.analyzingmapps.app;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class GitPatchFiller {
    static DBController databaseController;
    static String currentAppName;
    static String currentCommitGUID;

    static class MyFileVisitor extends SimpleFileVisitor<Path> {
        Path parentDir;

        // Variables for searching multiple files
        private PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.java");
        private Set<Path> foundManifests = new HashSet<>();
        private Set<MyFileParserVisitor> foundJavaFiles = new HashSet<>();

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
            if (matcher.matches(filePath.getFileName())) { //Analyze all *.java
                String relPath = filePath.toString().replace(parentDir.toString()+Analyzer.PATH_SEPARATOR, "");
                databaseController.insertFilesPatch(currentAppName, currentCommitGUID, relPath);

            } else if (filePath.getFileName().toString().equals("AndroidManifest.xml")) {
                String relPath = filePath.toString().replace(parentDir.toString()+Analyzer.PATH_SEPARATOR, "");
                databaseController.insertFilesPatch(currentAppName, currentCommitGUID, relPath);
            }
            return FileVisitResult.CONTINUE;
        }
    }


    public static void main(String[] args) throws IOException {
        long startTime = System.nanoTime();

        //String path = "/Users/virginia/Documents/RITPermSmells/tempo";
        String path = "/Users/virginia/Documents/RITPermSmells/VirginiaProjects";
        //String path = args[0];
        Path startingPath = Paths.get(path);

        databaseController = new DBController();
        try {
            databaseController.connectToDB();
            ArrayList<String> apps = databaseController.getAppList();
            for (String appName : apps) { // Iterate through the App
                currentAppName = appName;

                if (!Files.exists(Paths.get(startingPath.toString(), appName))) {
                    continue; // File not exists, Move to the next app to analyze
                }
                ArrayList<String> apkList = databaseController.getApkList(appName); // List of commits per App
                for (String commitGUID : apkList) { // Iterate through the app's commits
                    currentCommitGUID = commitGUID;
                    Path projectPath = Paths.get(startingPath.toString(), appName, commitGUID);

                    if (Files.exists(projectPath)) {
                        System.out.println("Analyzing " + projectPath);
                        try {
                            MyFileVisitor fileVisitor = new MyFileVisitor();
                            fileVisitor.parentDir = projectPath;
                            Files.walkFileTree(projectPath, fileVisitor);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }
            }

        } catch (Exception e) {
            System.out.print("Issues connecting to results database: \n" + e);
            System.exit(0);
        } finally {
            try {
                databaseController.dbPLintResultConnection.close();
                databaseController.dbAppsDatasetConnection.close();
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
