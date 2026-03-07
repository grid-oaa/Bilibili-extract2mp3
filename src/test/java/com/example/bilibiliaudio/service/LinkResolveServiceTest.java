package com.example.bilibiliaudio.service;

import com.example.bilibiliaudio.config.MediaProperties;
import com.example.bilibiliaudio.dto.ResolvedLinkGroupResponse;
import com.example.bilibiliaudio.util.BilibiliLinkValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LinkResolveServiceTest {

    @Test
    void shouldResolvePlaylistEntriesFromYtDlp() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        LinkResolveService service = new LinkResolveService(
                new BilibiliLinkValidator(),
                restTemplate,
                new ObjectMapper(),
                mediaProperties(),
                new LinkResolveService.CommandRunner() {
                    @Override
                    public String run(java.util.List<String> command, String stepName) {
                        return "{\"title\":\"Java 面试合集\",\"entries\":[{\"title\":\"ConcurrentHashMap 扩容\",\"webpage_url\":\"https://www.bilibili.com/video/BV1111111111?p=1\",\"playlist_index\":1},{\"title\":\"线程池\",\"webpage_url\":\"https://www.bilibili.com/video/BV1111111111?p=2\",\"playlist_index\":2}]}";
                    }
                }
        );

        List<ResolvedLinkGroupResponse> groups = service.resolveLinks(Collections.singletonList(
                "https://www.bilibili.com/video/BV1111111111"
        ));

        assertEquals(1, groups.size());
        assertTrue(groups.get(0).isMultipleParts());
        assertEquals("Java 面试合集", groups.get(0).getTitle());
        assertEquals(2, groups.get(0).getOptions().size());
        assertEquals("https://www.bilibili.com/video/BV1111111111?p=1", groups.get(0).getOptions().get(0).getLink());
        assertEquals("P2 · 线程池", groups.get(0).getOptions().get(1).getTitle());
        server.verify();
    }

    @Test
    void shouldPreselectCurrentPageWhenYtDlpReturnsPageEntries() {
        RestTemplate restTemplate = new RestTemplate();
        LinkResolveService service = new LinkResolveService(
                new BilibiliLinkValidator(),
                restTemplate,
                new ObjectMapper(),
                mediaProperties(),
                new LinkResolveService.CommandRunner() {
                    @Override
                    public String run(java.util.List<String> command, String stepName) {
                        return "{\"title\":\"合集课程\",\"entries\":[{\"title\":\"第一节\",\"webpage_url\":\"https://www.bilibili.com/video/BV2222222222?p=1\",\"playlist_index\":1},{\"title\":\"第二节\",\"webpage_url\":\"https://www.bilibili.com/video/BV2222222222?p=2\",\"playlist_index\":2}]}";
                    }
                }
        );

        List<ResolvedLinkGroupResponse> groups = service.resolveLinks(Collections.singletonList(
                "https://www.bilibili.com/video/BV2222222222?p=2"
        ));

        assertEquals(2, groups.get(0).getOptions().size());
        assertFalse(groups.get(0).getOptions().get(0).isSelected());
        assertTrue(groups.get(0).getOptions().get(1).isSelected());
    }

    @Test
    void shouldFallbackToBilibiliApiWhenYtDlpHasNoEntries() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo(containsString("bvid=BV3333333333")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":{\"title\":\"API 合集课程\",\"pages\":[{\"page\":1,\"part\":\"第一节\"},{\"page\":2,\"part\":\"第二节\"}]}}", MediaType.APPLICATION_JSON));

        LinkResolveService service = new LinkResolveService(
                new BilibiliLinkValidator(),
                restTemplate,
                new ObjectMapper(),
                mediaProperties(),
                new LinkResolveService.CommandRunner() {
                    @Override
                    public String run(java.util.List<String> command, String stepName) {
                        return "{\"title\":\"单视频\"}";
                    }
                }
        );

        List<ResolvedLinkGroupResponse> groups = service.resolveLinks(Collections.singletonList(
                "https://www.bilibili.com/video/BV3333333333"
        ));

        assertTrue(groups.get(0).isMultipleParts());
        assertEquals(2, groups.get(0).getOptions().size());
        assertEquals("https://www.bilibili.com/video/BV3333333333?p=2", groups.get(0).getOptions().get(1).getLink());
        server.verify();
    }

    private MediaProperties mediaProperties() {
        MediaProperties mediaProperties = new MediaProperties();
        mediaProperties.setYtDlpPath("yt-dlp");
        return mediaProperties;
    }
}