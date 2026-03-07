package com.example.bilibiliaudio.controller;

import com.example.bilibiliaudio.dto.CreateTaskResponse;
import com.example.bilibiliaudio.dto.ResolveLinksResponse;
import com.example.bilibiliaudio.dto.ResolvedLinkGroupResponse;
import com.example.bilibiliaudio.dto.ResolvedLinkOptionResponse;
import com.example.bilibiliaudio.exception.GlobalExceptionHandler;
import com.example.bilibiliaudio.model.TaskStatus;
import com.example.bilibiliaudio.service.LinkResolveService;
import com.example.bilibiliaudio.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerTest {

    private MockMvc mockMvc;
    private TaskService taskService;
    private LinkResolveService linkResolveService;

    @BeforeEach
    void setUp() {
        taskService = Mockito.mock(TaskService.class);
        linkResolveService = Mockito.mock(LinkResolveService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(taskService, linkResolveService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldCreateTask() throws Exception {
        when(taskService.createTask(anyList())).thenReturn(new CreateTaskResponse("task-1", TaskStatus.PENDING, 2000L));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"links\":[\"https://www.bilibili.com/video/BV1111111111\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").value("task-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldResolveLinks() throws Exception {
        ResolveLinksResponse response = new ResolveLinksResponse(Collections.singletonList(
                new ResolvedLinkGroupResponse(
                        "https://www.bilibili.com/video/BV1111111111",
                        "Java 面试题精选",
                        true,
                        Arrays.asList(
                                new ResolvedLinkOptionResponse("https://www.bilibili.com/video/BV1111111111?p=1", "P1 · 第一集", 1, false),
                                new ResolvedLinkOptionResponse("https://www.bilibili.com/video/BV1111111111?p=2", "P2 · 第二集", 2, false)
                        )
                )
        ));
        when(linkResolveService.resolveLinks(anyList())).thenReturn(response.getGroups());

        mockMvc.perform(post("/api/tasks/resolve-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"links\":[\"https://www.bilibili.com/video/BV1111111111\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].title").value("Java 面试题精选"))
                .andExpect(jsonPath("$.groups[0].multipleParts").value(true))
                .andExpect(jsonPath("$.groups[0].options[1].link").value("https://www.bilibili.com/video/BV1111111111?p=2"));
    }

    @Test
    void shouldRejectEmptyLinks() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"links\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}