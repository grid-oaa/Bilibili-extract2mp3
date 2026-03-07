package com.example.bilibiliaudio.model;

public enum TaskProgressStage {
    QUEUED,
    VERIFYING_DEPENDENCIES,
    PROCESSING_MEDIA,
    PACKAGING,
    COMPLETED,
    FAILED
}