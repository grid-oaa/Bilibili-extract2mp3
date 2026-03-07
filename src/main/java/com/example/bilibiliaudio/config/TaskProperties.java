package com.example.bilibiliaudio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.task")
public class TaskProperties {

    private int maxConcurrentTasks = 2;
    private long pollIntervalMs = 2000L;
    private int coordinatorCorePoolSize = 1;
    private int coordinatorMaxPoolSize = 2;
    private int workerCorePoolSize = 0;
    private int workerMaxPoolSize = 0;
    private int queueCapacity = 200;
    private long cleanupDelayMinutes = 10L;

    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    public void setMaxConcurrentTasks(int maxConcurrentTasks) {
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getCoordinatorCorePoolSize() {
        return coordinatorCorePoolSize;
    }

    public void setCoordinatorCorePoolSize(int coordinatorCorePoolSize) {
        this.coordinatorCorePoolSize = coordinatorCorePoolSize;
    }

    public int getCoordinatorMaxPoolSize() {
        return coordinatorMaxPoolSize;
    }

    public void setCoordinatorMaxPoolSize(int coordinatorMaxPoolSize) {
        this.coordinatorMaxPoolSize = coordinatorMaxPoolSize;
    }

    public int getWorkerCorePoolSize() {
        return workerCorePoolSize;
    }

    public void setWorkerCorePoolSize(int workerCorePoolSize) {
        this.workerCorePoolSize = workerCorePoolSize;
    }

    public int getWorkerMaxPoolSize() {
        return workerMaxPoolSize;
    }

    public void setWorkerMaxPoolSize(int workerMaxPoolSize) {
        this.workerMaxPoolSize = workerMaxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public long getCleanupDelayMinutes() {
        return cleanupDelayMinutes;
    }

    public void setCleanupDelayMinutes(long cleanupDelayMinutes) {
        this.cleanupDelayMinutes = cleanupDelayMinutes;
    }
}
