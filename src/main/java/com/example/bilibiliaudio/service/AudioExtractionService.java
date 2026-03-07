package com.example.bilibiliaudio.service;

import java.nio.file.Path;

public interface AudioExtractionService {

    void verifyDependencies();

    String resolveTitle(String link);

    Path downloadAsMp3(String link, String outputBaseName, Path outputDir);
}
