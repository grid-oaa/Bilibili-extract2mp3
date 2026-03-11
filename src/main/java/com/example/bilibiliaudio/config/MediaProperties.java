package com.example.bilibiliaudio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media")
public class MediaProperties {

    private String ytDlpPath = "yt-dlp";
    private String ffmpegPath = "ffmpeg";
    private String workDir = "work/tasks";
    private String cookiesPath;

    public String getYtDlpPath() {
        return ytDlpPath;
    }

    public void setYtDlpPath(String ytDlpPath) {
        this.ytDlpPath = ytDlpPath;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public String getCookiesPath() {
        return cookiesPath;
    }

    public void setCookiesPath(String cookiesPath) {
        this.cookiesPath = cookiesPath;
    }
}
