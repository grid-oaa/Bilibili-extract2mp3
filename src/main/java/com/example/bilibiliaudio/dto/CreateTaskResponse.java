package com.example.bilibiliaudio.dto;

import com.example.bilibiliaudio.model.TaskStatus;

public class CreateTaskResponse {

    private final String taskId;
    private final TaskStatus status;
    private final long pollIntervalMs;

    public CreateTaskResponse(String taskId, TaskStatus status, long pollIntervalMs) {
        this.taskId = taskId;
        this.status = status;
        this.pollIntervalMs = pollIntervalMs;
    }

    public String getTaskId() {
        return taskId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }
}
