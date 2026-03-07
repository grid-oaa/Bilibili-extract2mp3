package com.example.bilibiliaudio.service;

import com.example.bilibiliaudio.config.MediaProperties;
import com.example.bilibiliaudio.dto.ResolvedLinkGroupResponse;
import com.example.bilibiliaudio.dto.ResolvedLinkOptionResponse;
import com.example.bilibiliaudio.exception.BadRequestException;
import com.example.bilibiliaudio.util.BilibiliLinkValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LinkResolveService {

    interface CommandRunner {
        String run(List<String> command, String stepName);
    }

    private static final Pattern BVID_PATTERN = Pattern.compile("(BV[0-9A-Za-z]+)", Pattern.CASE_INSENSITIVE);

    private final BilibiliLinkValidator linkValidator;
    private final RestOperations restOperations;
    private final ObjectMapper objectMapper;
    private final MediaProperties mediaProperties;
    private final CommandRunner commandRunner;

    @Autowired
    public LinkResolveService(BilibiliLinkValidator linkValidator, RestTemplateBuilder restTemplateBuilder,
                              ObjectMapper objectMapper, MediaProperties mediaProperties) {
        this(linkValidator, restTemplateBuilder.build(), objectMapper, mediaProperties, null);
    }

    LinkResolveService(BilibiliLinkValidator linkValidator, RestOperations restOperations, ObjectMapper objectMapper,
                       MediaProperties mediaProperties, CommandRunner commandRunner) {
        this.linkValidator = linkValidator;
        this.restOperations = restOperations;
        this.objectMapper = objectMapper;
        this.mediaProperties = mediaProperties;
        this.commandRunner = commandRunner == null ? new CommandRunner() {
            @Override
            public String run(List<String> command, String stepName) {
                return runCommand(command, stepName);
            }
        } : commandRunner;
    }

    public List<ResolvedLinkGroupResponse> resolveLinks(List<String> rawLinks) {
        if (rawLinks == null || rawLinks.isEmpty()) {
            throw new BadRequestException("请至少输入一个 Bilibili 视频链接");
        }
        List<ResolvedLinkGroupResponse> groups = new ArrayList<ResolvedLinkGroupResponse>();
        for (String rawLink : rawLinks) {
            groups.add(resolveSingle(normalize(rawLink)));
        }
        return groups;
    }

    private ResolvedLinkGroupResponse resolveSingle(String link) {
        ResolvedLinkGroupResponse ytDlpGroup = resolveByYtDlp(link);
        if (ytDlpGroup != null) {
            return ytDlpGroup;
        }
        String bvid = extractBvid(link);
        if (bvid == null) {
            return singleOptionGroup(link, link, true);
        }
        try {
            ResponseEntity<String> response = restOperations.getForEntity(
                    UriComponentsBuilder.fromHttpUrl("https://api.bilibili.com/x/web-interface/view")
                            .queryParam("bvid", bvid)
                            .build()
                            .toUri(),
                    String.class
            );
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");
            JsonNode pages = data.path("pages");
            String title = textOrDefault(data.path("title"), link);
            if (!pages.isArray() || pages.size() <= 1) {
                return singleOptionGroup(link, title, true);
            }
            Integer currentPage = extractPage(link);
            List<ResolvedLinkOptionResponse> options = new ArrayList<ResolvedLinkOptionResponse>();
            for (JsonNode pageNode : pages) {
                int page = pageNode.path("page").asInt();
                String part = textOrDefault(pageNode.path("part"), "P" + page);
                String optionLink = "https://www.bilibili.com/video/" + bvid + "?p=" + page;
                boolean selected = currentPage != null && currentPage.intValue() == page;
                options.add(new ResolvedLinkOptionResponse(optionLink, "P" + page + " · " + part, page, selected));
            }
            return new ResolvedLinkGroupResponse(link, title, true, options);
        } catch (Exception ex) {
            return singleOptionGroup(link, link, true);
        }
    }

    private ResolvedLinkGroupResponse resolveByYtDlp(String link) {
        try {
            String output = commandRunner.run(Arrays.asList(
                    mediaProperties.getYtDlpPath(),
                    "--skip-download",
                    "--dump-single-json",
                    "--no-warnings",
                    link
            ), "链接解析");
            JsonNode root = objectMapper.readTree(output);
            JsonNode entries = root.path("entries");
            if (!entries.isArray() || entries.size() == 0) {
                return null;
            }
            String groupTitle = textOrDefault(root.path("title"), link);
            Integer currentPage = extractPage(link);
            List<ResolvedLinkOptionResponse> options = new ArrayList<ResolvedLinkOptionResponse>();
            Set<String> dedupedLinks = new LinkedHashSet<String>();
            for (JsonNode entry : entries) {
                String optionLink = extractOptionLink(entry, link);
                if (!dedupedLinks.add(optionLink)) {
                    continue;
                }
                Integer page = extractPage(optionLink);
                if (page == null && entry.has("playlist_index") && entry.path("playlist_index").canConvertToInt()) {
                    page = Integer.valueOf(entry.path("playlist_index").asInt());
                }
                String optionTitle = buildOptionTitle(entry, optionLink, page);
                boolean selected = optionLink.equals(link)
                        || (currentPage != null && page != null && currentPage.intValue() == page.intValue());
                options.add(new ResolvedLinkOptionResponse(optionLink, optionTitle, page, selected));
            }
            if (options.isEmpty()) {
                return null;
            }
            if (options.size() == 1) {
                ResolvedLinkOptionResponse option = options.get(0);
                return singleOptionGroup(option.getLink(), option.getTitle(), true);
            }
            return new ResolvedLinkGroupResponse(link, groupTitle, true, options);
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractOptionLink(JsonNode entry, String fallbackLink) {
        String webpageUrl = textOrDefault(entry.path("webpage_url"), null);
        if (webpageUrl != null) {
            return webpageUrl;
        }
        String originalUrl = textOrDefault(entry.path("original_url"), null);
        if (originalUrl != null) {
            return originalUrl;
        }
        String url = textOrDefault(entry.path("url"), null);
        if (url == null) {
            return fallbackLink;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("BV") || url.startsWith("bv")) {
            return "https://www.bilibili.com/video/" + url.toUpperCase(Locale.ROOT);
        }
        return fallbackLink;
    }

    private String buildOptionTitle(JsonNode entry, String optionLink, Integer page) {
        String title = textOrDefault(entry.path("title"), optionLink);
        if (page == null) {
            return title;
        }
        String prefix = "P" + page;
        if (title.startsWith(prefix)) {
            return title;
        }
        return prefix + " · " + title;
    }

    private ResolvedLinkGroupResponse singleOptionGroup(String link, String title, boolean selected) {
        return new ResolvedLinkGroupResponse(
                link,
                title,
                false,
                Collections.singletonList(new ResolvedLinkOptionResponse(link, title, null, selected))
        );
    }

    private String normalize(String rawLink) {
        if (rawLink == null || rawLink.trim().isEmpty()) {
            throw new BadRequestException("链接列表包含空行，请删除后重试");
        }
        String link = rawLink.trim();
        if (!linkValidator.isValid(link)) {
            throw new BadRequestException("仅支持公开可访问的 Bilibili 视频链接");
        }
        return link;
    }

    private String extractBvid(String link) {
        Matcher matcher = BVID_PATTERN.matcher(link);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private Integer extractPage(String link) {
        try {
            URI uri = new URI(link);
            String query = uri.getQuery();
            if (query == null || query.trim().isEmpty()) {
                return null;
            }
            String[] parts = query.split("&");
            for (String part : parts) {
                String[] keyValue = part.split("=", 2);
                if (keyValue.length == 2 && "p".equals(keyValue[0])) {
                    return Integer.valueOf(keyValue[1]);
                }
            }
        } catch (URISyntaxException ex) {
            return null;
        } catch (NumberFormatException ex) {
            return null;
        }
        return null;
    }

    private String textOrDefault(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String value = node.asText();
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
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
            throw new IllegalStateException(stepName + "失败，无法执行命令: " + command.get(0), ex);
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