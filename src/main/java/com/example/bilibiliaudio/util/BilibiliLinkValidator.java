package com.example.bilibiliaudio.util;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
public class BilibiliLinkValidator {

    public boolean isValid(String rawLink) {
        if (rawLink == null || rawLink.trim().isEmpty()) {
            return false;
        }
        try {
            URI uri = new URI(rawLink.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return false;
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return false;
            }
            return normalizedHost.equals("b23.tv")
                    || normalizedHost.equals("bilibili.com")
                    || normalizedHost.endsWith(".bilibili.com");
        } catch (URISyntaxException ex) {
            return false;
        }
    }
}
