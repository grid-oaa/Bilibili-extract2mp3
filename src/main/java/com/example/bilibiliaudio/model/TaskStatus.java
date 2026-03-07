package com.example.bilibiliaudio.model;

public enum TaskStatus {
    PENDING,
    RUNNING,
    PARTIAL_SUCCESS,
    SUCCESS,
    FAILED;

    public boolean isTerminal() {
        return this == PARTIAL_SUCCESS || this == SUCCESS || this == FAILED;
    }
}
