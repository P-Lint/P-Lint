package com.analyzingmapps.app;

import java.io.File;

/**
 * Created by Colton Dennis on 10/4/16.
 */
public class dirTraverser {
    public interface FileHandler{
        void handle(int level, String path, File file);
    }
    public interface Filter{
        boolean interested(int level, String path, File file);
    }

    private FileHandler fh;
    private Filter fil;

    public dirTraverser(Filter fil, FileHandler fh){
        this.fil = fil;
        this.fh = fh;
    }

    public void explore(File root){
        explore(0, "", root);
    }

    private void explore(int level, String path, File file){
        if(file.isDirectory()){
            for(File child: file.listFiles()){
                int nextLvl = level+1;
                String childName = path+"/"+child.getName();
                explore(nextLvl, childName, child);
            }
        }
        else{
            if(fil.interested(level, path, file)){
                fh.handle(level, path, file);
            }
        }
    }
}
