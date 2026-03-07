package com.example.bilibiliaudio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "taskCoordinatorExecutor")
    public Executor taskCoordinatorExecutor(TaskProperties taskProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, taskProperties.getCoordinatorCorePoolSize()));
        executor.setMaxPoolSize(Math.max(executor.getCorePoolSize(), taskProperties.getCoordinatorMaxPoolSize()));
        executor.setQueueCapacity(Math.max(50, taskProperties.getQueueCapacity() / 2));
        executor.setThreadNamePrefix("task-coordinator-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "mediaWorkerExecutor")
    public Executor mediaWorkerExecutor(TaskProperties taskProperties) {
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int defaultWorkerCore = Math.max(2, processors * 2);
        int defaultWorkerMax = Math.max(defaultWorkerCore, processors * 4);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int workerCore = taskProperties.getWorkerCorePoolSize() > 0
                ? taskProperties.getWorkerCorePoolSize()
                : defaultWorkerCore;
        int workerMax = taskProperties.getWorkerMaxPoolSize() > 0
                ? taskProperties.getWorkerMaxPoolSize()
                : defaultWorkerMax;

        executor.setCorePoolSize(workerCore);
        executor.setMaxPoolSize(Math.max(workerCore, workerMax));
        executor.setQueueCapacity(Math.max(100, taskProperties.getQueueCapacity()));
        executor.setThreadNamePrefix("media-worker-");
        executor.initialize();
        return executor;
    }
}