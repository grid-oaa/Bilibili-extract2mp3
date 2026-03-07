package com.example.bilibiliaudio.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class AudioTask {

    private final String taskId;
    private final List<String> links;
    private final List<LinkTaskResult> results;
    private final Path taskDir;
    private final Path audioDir;
    private final Path bundleDir;
    private final Path zipPath;
    private final Instant createdAt;

    private volatile TaskStatus status;
    private volatile TaskProgressStage progressStage;
    private volatile boolean downloadReady;
    private volatile String errorSummary;
    private volatile Instant startedAt;
    private volatile Instant completedAt;

    public AudioTask(String taskId, List<String> links, List<LinkTaskResult> results, Path taskDir, Path audioDir,
                     Path bundleDir, Path zipPath) {
        this.taskId = taskId;
        this.links = Collections.unmodifiableList(links);
        this.results = results;
        this.taskDir = taskDir;
        this.audioDir = audioDir;
        this.bundleDir = bundleDir;
        this.zipPath = zipPath;
        this.createdAt = Instant.now();
        this.status = TaskStatus.PENDING;
        this.progressStage = TaskProgressStage.QUEUED;
    }

    public String getTaskId() {
        return taskId;
    }

    public List<String> getLinks() {
        return links;
    }

    public List<LinkTaskResult> getResults() {
        return results;
    }

    public Path getTaskDir() {
        return taskDir;
    }

    public Path getAudioDir() {
        return audioDir;
    }

    public Path getBundleDir() {
        return bundleDir;
    }

    public Path getZipPath() {
        return zipPath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskProgressStage getProgressStage() {
        return progressStage;
    }

    public void setProgressStage(TaskProgressStage progressStage) {
        this.progressStage = progressStage;
    }

    public boolean isDownloadReady() {
        return downloadReady;
    }

    public void setDownloadReady(boolean downloadReady) {
        this.downloadReady = downloadReady;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}