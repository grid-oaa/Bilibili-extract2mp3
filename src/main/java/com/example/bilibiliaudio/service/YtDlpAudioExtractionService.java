package com.example.bilibiliaudio.service;

import com.example.bilibiliaudio.config.MediaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class YtDlpAudioExtractionService implements AudioExtractionService {

    private final MediaProperties mediaProperties;
    private final ObjectMapper objectMapper;
    private volatile boolean dependenciesVerified;

    public YtDlpAudioExtractionService(MediaProperties mediaProperties, ObjectMapper objectMapper) {
        this.mediaProperties = mediaProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void verifyDependencies() {
        if (dependenciesVerified) {
            return;
        }
        resolveCookiesPath();
        runCommand(Arrays.asList(mediaProperties.getYtDlpPath(), "--version"), "yt-dlp 检查");
        runCommand(Arrays.asList(resolveFfmpegExecutable(), "-version"), "ffmpeg 检查");
        dependenciesVerified = true;
    }

    @Override
    public String resolveTitle(String link) {
        List<String> command = new ArrayList<String>();
        command.add(mediaProperties.getYtDlpPath());
        appendCookiesArgument(command);
        command.addAll(Arrays.asList(
                "--skip-download",
                "--dump-single-json",
                "--no-playlist",
                "--no-warnings",
                link
        ));
        String output = runCommand(command, "视频元数据解析");
        try {
            JsonNode jsonNode = objectMapper.readTree(output);
            String title = jsonNode.path("title").asText();
            if (title == null || title.trim().isEmpty()) {
                throw new IllegalStateException("未获取到视频标题");
            }
            return title.trim();
        } catch (IOException ex) {
            throw new IllegalStateException("解析视频元数据失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Path downloadAsMp3(String link, String outputBaseName, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
        } catch (IOException ex) {
            throw new IllegalStateException("创建输出目录失败: " + ex.getMessage(), ex);
        }
        String outputTemplate = outputDir.resolve(outputBaseName + ".%(ext)s").toString();
        List<String> command = new ArrayList<String>();
        command.add(mediaProperties.getYtDlpPath());
        appendCookiesArgument(command);
        command.addAll(Arrays.asList(
                "--no-playlist",
                "--no-warnings",
                "--extract-audio",
                "--audio-format",
                "mp3",
                "--audio-quality",
                "0",
                "--ffmpeg-location",
                resolveFfmpegLocation(),
                "--output",
                outputTemplate,
                link
        ));
        runCommand(command, "音频下载与转码");
        Path expectedFile = outputDir.resolve(outputBaseName + ".mp3");
        if (Files.exists(expectedFile)) {
            return expectedFile;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir, outputBaseName + ".*")) {
            for (Path path : stream) {
                if (path.getFileName().toString().toLowerCase().endsWith(".mp3")) {
                    return path;
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("查找音频文件失败: " + ex.getMessage(), ex);
        }
        throw new IllegalStateException("音频文件生成失败，请确认 ffmpeg 转码是否正常");
    }

    private String resolveFfmpegExecutable() {
        String configured = mediaProperties.getFfmpegPath();
        Path path = Paths.get(configured);
        if (Files.isDirectory(path)) {
            Path candidate = path.resolve("ffmpeg.exe");
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
        }
        return configured;
    }

    private String resolveFfmpegLocation() {
        String configured = mediaProperties.getFfmpegPath();
        Path path = Paths.get(configured);
        if (Files.isRegularFile(path)) {
            Path parent = path.getParent();
            if (parent != null) {
                return parent.toString();
            }
        }
        return configured;
    }

    private void appendCookiesArgument(List<String> command) {
        String cookiesPath = resolveCookiesPath();
        if (cookiesPath != null) {
            command.add("--cookies");
            command.add(cookiesPath);
        }
    }

    private String resolveCookiesPath() {
        String configured = mediaProperties.getCookiesPath();
        if (configured == null || configured.trim().isEmpty()) {
            return null;
        }
        Path path = Paths.get(configured.trim()).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalStateException("cookies.txt 不存在或不可读: " + path);
        }
        return path.toString();
    }

    private String runCommand(List<String> command, String stepName) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("PYTHONUTF8", "1");
        try {
            Process process = processBuilder.start();
            String output = readAll(process.getInputStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(stepName + "失败: " + compact(output));
            }
            return output;
        } catch (IOException ex) {
            throw new IllegalStateException(stepName + "失败，无法执行命令: " + command.get(0)
                    + "。请检查 app.media 配置和本机依赖。", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(stepName + "被中断", ex);
        }
    }

    private String readAll(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        List<String> lines = new ArrayList<String>();
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        return String.join(System.lineSeparator(), lines);
    }

    private String compact(String output) {
        String compacted = output == null ? "" : output.replaceAll("\\s+", " ").trim();
        if (compacted.length() > 300) {
            return compacted.substring(0, 300) + "...";
        }
        return compacted;
    }
}
