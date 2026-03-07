package com.example.bilibiliaudio.service;

import com.example.bilibiliaudio.config.MediaProperties;
import com.example.bilibiliaudio.config.TaskProperties;
import com.example.bilibiliaudio.dto.CreateTaskResponse;
import com.example.bilibiliaudio.dto.TaskDetailResponse;
import com.example.bilibiliaudio.exception.BadRequestException;
import com.example.bilibiliaudio.exception.DownloadNotReadyException;
import com.example.bilibiliaudio.exception.TaskNotFoundException;
import com.example.bilibiliaudio.model.TaskProgressStage;
import com.example.bilibiliaudio.model.TaskStatus;
import com.example.bilibiliaudio.util.BilibiliLinkValidator;
import com.example.bilibiliaudio.util.FilenameSanitizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRejectBlankOrInvalidLinks() {
        TaskService service = newService(new SuccessfulExtractor());
        assertThrows(BadRequestException.class, () -> service.createTask(Collections.singletonList("   ")));
        assertThrows(BadRequestException.class, () -> service.createTask(Collections.singletonList("https://example.com/video")));
    }

    @Test
    void shouldMarkTaskAsPartialSuccessAndBuildZip() throws Exception {
        TaskService service = newService(new PartialExtractor());
        CreateTaskResponse response = service.createTask(Arrays.asList(
                "https://www.bilibili.com/video/BV1111111111",
                "https://www.bilibili.com/video/BV2222222222"
        ));

        TaskDetailResponse detail = service.getTaskDetail(response.getTaskId());
        assertEquals(TaskStatus.PARTIAL_SUCCESS, detail.getStatus());
        assertEquals(TaskProgressStage.COMPLETED, detail.getProgressStage());
        assertEquals(2, detail.getTotalCount());
        assertEquals(2, detail.getCompletedCount());
        assertEquals(1, detail.getSuccessCount());
        assertEquals(1, detail.getFailedCount());
        assertEquals(0, detail.getRunningCount());
        assertEquals(100, detail.getProgressPercent());
        assertTrue(detail.isDownloadReady());
        assertEquals("SUCCESS", detail.getResults().get(0).getStatus().name());
        assertEquals("FAILED", detail.getResults().get(1).getStatus().name());

        Path zipPath = service.getDownloadPath(response.getTaskId());
        assertTrue(Files.exists(zipPath));
        assertEquals(1, countZipEntries(zipPath));
    }

    @Test
    void shouldCleanupArtifactsAfterDownload() throws Exception {
        TaskService service = newService(new SuccessfulExtractor(), 0L);
        CreateTaskResponse response = service.createTask(Collections.singletonList(
                "https://www.bilibili.com/video/BV1111111111"
        ));

        Path zipPath = service.getDownloadPath(response.getTaskId());
        assertTrue(Files.exists(zipPath));
        waitForDeletion(zipPath.getParent().getParent());
        assertThrows(TaskNotFoundException.class, () -> service.getTaskDetail(response.getTaskId()));
    }

    @Test
    void shouldMarkTaskFailedWhenAllLinksFail() {
        TaskService service = newService(new FailedExtractor());
        CreateTaskResponse response = service.createTask(Collections.singletonList(
                "https://www.bilibili.com/video/BV3333333333"
        ));

        TaskDetailResponse detail = service.getTaskDetail(response.getTaskId());
        assertEquals(TaskStatus.FAILED, detail.getStatus());
        assertEquals(TaskProgressStage.FAILED, detail.getProgressStage());
        assertEquals(1, detail.getTotalCount());
        assertEquals(1, detail.getCompletedCount());
        assertEquals(0, detail.getSuccessCount());
        assertEquals(1, detail.getFailedCount());
        assertEquals(100, detail.getProgressPercent());
        assertThrows(DownloadNotReadyException.class, () -> service.getDownloadPath(response.getTaskId()));
    }

    @Test
    void shouldProcessLinksConcurrently() throws Exception {
        ExecutorService workerExecutor = Executors.newFixedThreadPool(4);
        try {
            ParallelExtractor extractor = new ParallelExtractor();
            TaskService service = newService(extractor, new DirectExecutor(), workerExecutor, 10L);
            CreateTaskResponse response = service.createTask(Arrays.asList(
                    "https://www.bilibili.com/video/BV1111111111",
                    "https://www.bilibili.com/video/BV2222222222",
                    "https://www.bilibili.com/video/BV3333333333"
            ));

            TaskDetailResponse detail = service.getTaskDetail(response.getTaskId());
            assertEquals(TaskStatus.SUCCESS, detail.getStatus());
            assertEquals(TaskProgressStage.COMPLETED, detail.getProgressStage());
            assertTrue(extractor.awaited);
            assertTrue(extractor.maxInFlight.get() > 1);
        } finally {
            workerExecutor.shutdownNow();
        }
    }

    private TaskService newService(AudioExtractionService extractionService) {
        DirectExecutor directExecutor = new DirectExecutor();
        return newService(extractionService, directExecutor, directExecutor, 10L);
    }

    private TaskService newService(AudioExtractionService extractionService, long cleanupDelayMinutes) {
        DirectExecutor directExecutor = new DirectExecutor();
        return newService(extractionService, directExecutor, directExecutor, cleanupDelayMinutes);
    }

    private TaskService newService(AudioExtractionService extractionService, Executor coordinatorExecutor, Executor workerExecutor) {
        return newService(extractionService, coordinatorExecutor, workerExecutor, 10L);
    }

    private TaskService newService(AudioExtractionService extractionService, Executor coordinatorExecutor, Executor workerExecutor, long cleanupDelayMinutes) {
        MediaProperties mediaProperties = new MediaProperties();
        mediaProperties.setWorkDir(tempDir.resolve("work").toString());
        TaskProperties taskProperties = new TaskProperties();
        taskProperties.setCleanupDelayMinutes(cleanupDelayMinutes);
        return new TaskService(
                mediaProperties,
                taskProperties,
                new BilibiliLinkValidator(),
                new FilenameSanitizer(),
                extractionService,
                new ZipBundleService(),
                coordinatorExecutor,
                workerExecutor
        );
    }

    private void waitForDeletion(Path taskDir) throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            if (Files.notExists(taskDir)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("任务目录未在预期时间内删除: " + taskDir);
    }

    private int countZipEntries(Path zipPath) throws IOException {
        int count = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
            while (zipInputStream.getNextEntry() != null) {
                count++;
            }
        }
        return count;
    }

    private static class DirectExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    private static class SuccessfulExtractor implements AudioExtractionService {

        @Override
        public void verifyDependencies() {
        }

        @Override
        public String resolveTitle(String link) {
            return "测试视频";
        }

        @Override
        public Path downloadAsMp3(String link, String outputBaseName, Path outputDir) {
            try {
                Files.createDirectories(outputDir);
                Path file = outputDir.resolve(outputBaseName + ".mp3");
                Files.write(file, Collections.singletonList("audio"), StandardCharsets.UTF_8);
                return file;
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static class PartialExtractor extends SuccessfulExtractor {

        @Override
        public Path downloadAsMp3(String link, String outputBaseName, Path outputDir) {
            if (link.contains("BV2222222222")) {
                throw new IllegalStateException("模拟下载失败");
            }
            return super.downloadAsMp3(link, outputBaseName, outputDir);
        }
    }

    private static class FailedExtractor implements AudioExtractionService {

        @Override
        public void verifyDependencies() {
        }

        @Override
        public String resolveTitle(String link) {
            throw new IllegalStateException("依赖不可用");
        }

        @Override
        public Path downloadAsMp3(String link, String outputBaseName, Path outputDir) {
            throw new IllegalStateException("依赖不可用");
        }
    }

    private static class ParallelExtractor extends SuccessfulExtractor {

        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxInFlight = new AtomicInteger();
        private final CountDownLatch readyLatch = new CountDownLatch(3);
        private volatile boolean awaited;

        @Override
        public String resolveTitle(String link) {
            int current = inFlight.incrementAndGet();
            updateMax(current);
            readyLatch.countDown();
            try {
                awaited = readyLatch.await(2, TimeUnit.SECONDS);
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            return link.substring(link.length() - 4);
        }

        private void updateMax(int current) {
            while (true) {
                int previous = maxInFlight.get();
                if (current <= previous) {
                    return;
                }
                if (maxInFlight.compareAndSet(previous, current)) {
                    return;
                }
            }
        }
    }
}
