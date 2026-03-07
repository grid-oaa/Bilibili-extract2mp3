package com.example.bilibiliaudio.dto;

import com.example.bilibiliaudio.model.TaskProgressStage;
import com.example.bilibiliaudio.model.TaskStatus;

import java.time.Instant;
import java.util.List;

public class TaskDetailResponse {

    private final String taskId;
    private final TaskStatus status;
    private final TaskProgressStage progressStage;
    private final int totalCount;
    private final int completedCount;
    private final int successCount;
    private final int failedCount;
    private final int runningCount;
    private final int progressPercent;
    private final boolean downloadReady;
    private final String errorSummary;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String downloadUrl;
    private final List<LinkTaskResultResponse> results;

    public TaskDetailResponse(String taskId, TaskStatus status, TaskProgressStage progressStage,
                              int totalCount, int completedCount, int successCount, int failedCount,
                              int runningCount, int progressPercent, boolean downloadReady, String errorSummary,
                              Instant createdAt, Instant startedAt, Instant completedAt, String downloadUrl,
                              List<LinkTaskResultResponse> results) {
        this.taskId = taskId;
        this.status = status;
        this.progressStage = progressStage;
        this.totalCount = totalCount;
        this.completedCount = completedCount;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.runningCount = runningCount;
        this.progressPercent = progressPercent;
        this.downloadReady = downloadReady;
        this.errorSummary = errorSummary;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.downloadUrl = downloadUrl;
        this.results = results;
    }

    public String getTaskId() {
        return taskId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TaskProgressStage getProgressStage() {
        return progressStage;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getRunningCount() {
        return runningCount;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public boolean isDownloadReady() {
        return downloadReady;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public List<LinkTaskResultResponse> getResults() {
        return results;
    }
}