package com.example.bilibiliaudio.exception;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(String taskId) {
        super("任务不存在: " + taskId);
    }
}
