package com.example.bilibiliaudio.service;

import com.example.bilibiliaudio.config.MediaProperties;
import com.example.bilibiliaudio.config.TaskProperties;
import com.example.bilibiliaudio.dto.CreateTaskResponse;
import com.example.bilibiliaudio.dto.LinkTaskResultResponse;
import com.example.bilibiliaudio.dto.TaskDetailResponse;
import com.example.bilibiliaudio.exception.BadRequestException;
import com.example.bilibiliaudio.exception.DownloadNotReadyException;
import com.example.bilibiliaudio.exception.TaskNotFoundException;
import com.example.bilibiliaudio.model.AudioTask;
import com.example.bilibiliaudio.model.LinkTaskResult;
import com.example.bilibiliaudio.model.LinkTaskStatus;
import com.example.bilibiliaudio.model.TaskProgressStage;
import com.example.bilibiliaudio.model.TaskStatus;
import com.example.bilibiliaudio.util.BilibiliLinkValidator;
import com.example.bilibiliaudio.util.FilenameSanitizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Service
public class TaskService {

    private final TaskProperties taskProperties;
    private final BilibiliLinkValidator linkValidator;
    private final FilenameSanitizer filenameSanitizer;
    private final AudioExtractionService audioExtractionService;
    private final ZipBundleService zipBundleService;
    private final Executor coordinatorExecutor;
    private final Executor mediaWorkerExecutor;
    private final ConcurrentMap<String, AudioTask> taskStore = new ConcurrentHashMap<String, AudioTask>();
    private final ConcurrentMap<String, ScheduledFuture<?>> cleanupFutures = new ConcurrentHashMap<String, ScheduledFuture<?>>();
    private final Path workRoot;
    private final ScheduledExecutorService cleanupExecutor;

    public TaskService(MediaProperties mediaProperties, TaskProperties taskProperties,
                       BilibiliLinkValidator linkValidator, FilenameSanitizer filenameSanitizer,
                       AudioExtractionService audioExtractionService, ZipBundleService zipBundleService,
                       @Qualifier("taskCoordinatorExecutor") Executor coordinatorExecutor,
                       @Qualifier("mediaWorkerExecutor") Executor mediaWorkerExecutor) {
        this.taskProperties = taskProperties;
        this.linkValidator = linkValidator;
        this.filenameSanitizer = filenameSanitizer;
        this.audioExtractionService = audioExtractionService;
        this.zipBundleService = zipBundleService;
        this.coordinatorExecutor = coordinatorExecutor;
        this.mediaWorkerExecutor = mediaWorkerExecutor;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "task-cleanup-");
                thread.setDaemon(true);
                return thread;
            }
        });
        this.workRoot = Paths.get(mediaProperties.getWorkDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.workRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("初始化工作目录失败: " + ex.getMessage(), ex);
        }
    }

    public CreateTaskResponse createTask(List<String> rawLinks) {
        List<String> links = normalizeLinks(rawLinks);
        String taskId = UUID.randomUUID().toString();
        Path taskDir = workRoot.resolve(taskId);
        Path audioDir = taskDir.resolve("audio");
        Path bundleDir = taskDir.resolve("bundle");
        Path zipPath = bundleDir.resolve("bilibili-audio-" + taskId + ".zip");
        try {
            Files.createDirectories(audioDir);
            Files.createDirectories(bundleDir);
        } catch (IOException ex) {
            throw new IllegalStateException("初始化任务目录失败: " + ex.getMessage(), ex);
        }
        List<LinkTaskResult> results = new ArrayList<LinkTaskResult>();
        for (int i = 0; i < links.size(); i++) {
            results.add(new LinkTaskResult(i, links.get(i)));
        }
        AudioTask task = new AudioTask(taskId, links, results, taskDir, audioDir, bundleDir, zipPath);
        taskStore.put(taskId, task);
        coordinatorExecutor.execute(new Runnable() {
            @Override
            public void run() {
                processTask(task);
            }
        });
        return new CreateTaskResponse(taskId, task.getStatus(), taskProperties.getPollIntervalMs());
    }

    public TaskDetailResponse getTaskDetail(String taskId) {
        AudioTask task = getTask(taskId);
        List<LinkTaskResultResponse> responses = new ArrayList<LinkTaskResultResponse>();
        int totalCount = task.getResults().size();
        int successCount = 0;
        int failedCount = 0;
        int runningCount = 0;
        int completedCount = 0;
        for (LinkTaskResult result : task.getResults()) {
            responses.add(new LinkTaskResultResponse(
                    result.getIndex(),
                    result.getLink(),
                    result.getTitle(),
                    result.getFileName(),
                    result.getStatus(),
                    result.getErrorMessage()
            ));
            if (result.getStatus() == LinkTaskStatus.SUCCESS) {
                successCount++;
                completedCount++;
            } else if (result.getStatus() == LinkTaskStatus.FAILED) {
                failedCount++;
                completedCount++;
            } else if (result.getStatus() == LinkTaskStatus.RUNNING) {
                runningCount++;
            }
        }
        int progressPercent = calculateProgressPercent(task.getProgressStage(), totalCount, completedCount);
        return new TaskDetailResponse(
                task.getTaskId(),
                task.getStatus(),
                task.getProgressStage(),
                totalCount,
                completedCount,
                successCount,
                failedCount,
                runningCount,
                progressPercent,
                task.isDownloadReady(),
                task.getErrorSummary(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.isDownloadReady() ? "/api/tasks/" + task.getTaskId() + "/download" : null,
                responses
        );
    }

    public Path getDownloadPath(String taskId) {
        AudioTask task = getTask(taskId);
        if (!task.getStatus().isTerminal()) {
            throw new DownloadNotReadyException("任务尚未完成，请稍后重试");
        }
        if (!task.isDownloadReady() || !Files.exists(task.getZipPath())) {
            throw new DownloadNotReadyException("当前任务没有可下载的音频文件");
        }
        scheduleCleanup(task);
        return task.getZipPath();
    }

    private void processTask(AudioTask task) {
        task.setStatus(TaskStatus.RUNNING);
        task.setProgressStage(TaskProgressStage.VERIFYING_DEPENDENCIES);
        task.setStartedAt(Instant.now());
        try {
            audioExtractionService.verifyDependencies();
        } catch (RuntimeException ex) {
            markAllFailed(task, compact(ex.getMessage()));
            return;
        }

        task.setProgressStage(TaskProgressStage.PROCESSING_MEDIA);
        final Object fileNameLock = new Object();
        final Set<String> usedNames = Collections.synchronizedSet(new HashSet<String>());
        List<CompletableFuture<Void>> futures = new ArrayList<CompletableFuture<Void>>();
        for (final LinkTaskResult result : task.getResults()) {
            futures.add(CompletableFuture.runAsync(new Runnable() {
                @Override
                public void run() {
                    processSingleResult(task, result, usedNames, fileNameLock);
                }
            }, mediaWorkerExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        finalizeTask(task);
    }

    private void processSingleResult(AudioTask task, LinkTaskResult result, Set<String> usedNames, Object fileNameLock) {
        result.setStatus(LinkTaskStatus.RUNNING);
        try {
            String title = audioExtractionService.resolveTitle(result.getLink());
            result.setTitle(title);
            String baseName;
            synchronized (fileNameLock) {
                baseName = filenameSanitizer.uniqueBaseName(title, usedNames);
            }
            Path audioFile = audioExtractionService.downloadAsMp3(result.getLink(), baseName, task.getAudioDir());
            result.setFileName(audioFile.getFileName().toString());
            result.setStatus(LinkTaskStatus.SUCCESS);
        } catch (RuntimeException ex) {
            result.setStatus(LinkTaskStatus.FAILED);
            result.setErrorMessage(compact(ex.getMessage()));
        }
    }

    private void markAllFailed(AudioTask task, String message) {
        for (LinkTaskResult result : task.getResults()) {
            result.setStatus(LinkTaskStatus.FAILED);
            result.setErrorMessage(message);
        }
        task.setStatus(TaskStatus.FAILED);
        task.setProgressStage(TaskProgressStage.FAILED);
        task.setErrorSummary(message);
        task.setCompletedAt(Instant.now());
        task.setDownloadReady(false);
    }

    private void finalizeTask(AudioTask task) {
        List<String> successfulFiles = new ArrayList<String>();
        List<String> failedMessages = new ArrayList<String>();
        for (LinkTaskResult result : task.getResults()) {
            if (result.getStatus() == LinkTaskStatus.SUCCESS && result.getFileName() != null) {
                successfulFiles.add(result.getFileName());
            }
            if (result.getStatus() == LinkTaskStatus.FAILED && result.getErrorMessage() != null) {
                failedMessages.add(result.getErrorMessage());
            }
        }
        if (successfulFiles.isEmpty()) {
            task.setStatus(TaskStatus.FAILED);
            task.setProgressStage(TaskProgressStage.FAILED);
            task.setDownloadReady(false);
        } else {
            try {
                task.setProgressStage(TaskProgressStage.PACKAGING);
                zipBundleService.createZip(task.getAudioDir(), task.getZipPath(), successfulFiles);
                task.setDownloadReady(true);
                task.setStatus(failedMessages.isEmpty() ? TaskStatus.SUCCESS : TaskStatus.PARTIAL_SUCCESS);
                task.setProgressStage(TaskProgressStage.COMPLETED);
            } catch (RuntimeException ex) {
                task.setStatus(TaskStatus.FAILED);
                task.setProgressStage(TaskProgressStage.FAILED);
                task.setDownloadReady(false);
                failedMessages.add(compact(ex.getMessage()));
            }
        }
        task.setErrorSummary(joinMessages(failedMessages));
        task.setCompletedAt(Instant.now());
    }

    private void scheduleCleanup(final AudioTask task) {
        long delayMinutes = Math.max(0L, taskProperties.getCleanupDelayMinutes());
        ScheduledFuture<?> existing = cleanupFutures.remove(task.getTaskId());
        if (existing != null) {
            existing.cancel(false);
        }
        ScheduledFuture<?> future = cleanupExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                cleanupTaskArtifacts(task);
            }
        }, delayMinutes, TimeUnit.MINUTES);
        cleanupFutures.put(task.getTaskId(), future);
    }

    private void cleanupTaskArtifacts(AudioTask task) {
        cleanupFutures.remove(task.getTaskId());
        taskStore.remove(task.getTaskId());
        try {
            if (Files.notExists(task.getTaskDir())) {
                return;
            }
            Files.walk(task.getTaskDir())
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private int calculateProgressPercent(TaskProgressStage stage, int totalCount, int completedCount) {
        if (stage == TaskProgressStage.QUEUED) {
            return 5;
        }
        if (stage == TaskProgressStage.VERIFYING_DEPENDENCIES) {
            return 10;
        }
        if (stage == TaskProgressStage.PROCESSING_MEDIA) {
            if (totalCount <= 0) {
                return 15;
            }
            return Math.min(90, 10 + (completedCount * 80 / totalCount));
        }
        if (stage == TaskProgressStage.PACKAGING) {
            return 95;
        }
        return 100;
    }

    private AudioTask getTask(String taskId) {
        AudioTask task = taskStore.get(taskId);
        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }
        return task;
    }

    private List<String> normalizeLinks(List<String> rawLinks) {
        if (rawLinks == null || rawLinks.isEmpty()) {
            throw new BadRequestException("请至少输入一个 Bilibili 视频链接");
        }
        List<String> links = new ArrayList<String>();
        for (String rawLink : rawLinks) {
            if (rawLink == null || rawLink.trim().isEmpty()) {
                throw new BadRequestException("链接列表包含空行，请删除后重试");
            }
            String link = rawLink.trim();
            if (!linkValidator.isValid(link)) {
                throw new BadRequestException("仅支持公开可访问的 Bilibili 视频链接");
            }
            links.add(link);
        }
        return links;
    }

    private String joinMessages(List<String> messages) {
        if (messages.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(messages.get(i));
        }
        return builder.toString();
    }

    private String compact(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "处理失败";
        }
        String compacted = message.replaceAll("\\s+", " ").trim();
        if (compacted.length() > 280) {
            return compacted.substring(0, 280) + "...";
        }
        return compacted;
    }
}
