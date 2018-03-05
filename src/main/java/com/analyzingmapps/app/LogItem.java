package com.analyzingmapps.app;

public class LogItem{
    private String app, commit;

    public LogItem(String app, String commit) {
        this.app = app;
        this.commit = commit;
    }

    public String getCommit() {
        return commit;
    }

    public void setCommit(String commit) {
        this.commit = commit;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }
}
