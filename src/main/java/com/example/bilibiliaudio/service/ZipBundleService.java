package com.example.bilibiliaudio.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ZipBundleService {

    public Path createZip(Path sourceDir, Path zipPath, List<String> fileNames) {
        try {
            Files.createDirectories(zipPath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(zipPath);
                 ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                for (String fileName : fileNames) {
                    Path sourceFile = sourceDir.resolve(fileName);
                    if (!Files.exists(sourceFile)) {
                        continue;
                    }
                    zipOutputStream.putNextEntry(new ZipEntry(fileName));
                    copy(Files.newInputStream(sourceFile), zipOutputStream);
                    zipOutputStream.closeEntry();
                }
            }
            return zipPath;
        } catch (IOException ex) {
            throw new IllegalStateException("生成 ZIP 失败: " + ex.getMessage(), ex);
        }
    }

    private void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        try (InputStream in = inputStream) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
    }
}
