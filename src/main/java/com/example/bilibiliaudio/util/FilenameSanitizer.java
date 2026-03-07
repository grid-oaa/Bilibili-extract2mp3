package com.example.bilibiliaudio.util;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class FilenameSanitizer {

    private static final String ILLEGAL_CHARS = "[\\\\/:*?\"<>|]";

    public String uniqueBaseName(String rawTitle, Set<String> usedNames) {
        String sanitized = sanitize(rawTitle);
        String candidate = sanitized;
        int suffix = 1;
        while (usedNames.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = sanitized + "-" + suffix;
            suffix++;
        }
        usedNames.add(candidate.toLowerCase(Locale.ROOT));
        return candidate;
    }

    private String sanitize(String rawTitle) {
        String source = rawTitle == null ? "" : rawTitle.trim();
        String sanitized = source.replaceAll(ILLEGAL_CHARS, "_")
                .replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.isEmpty()) {
            sanitized = "audio";
        }
        if (sanitized.length() > 120) {
            sanitized = sanitized.substring(0, 120).trim();
        }
        return sanitized;
    }
}
