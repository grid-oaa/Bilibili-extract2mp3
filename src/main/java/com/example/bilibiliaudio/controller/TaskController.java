package com.example.bilibiliaudio.controller;

import com.example.bilibiliaudio.dto.CreateTaskRequest;
import com.example.bilibiliaudio.dto.CreateTaskResponse;
import com.example.bilibiliaudio.dto.ResolveLinksResponse;
import com.example.bilibiliaudio.dto.TaskDetailResponse;
import com.example.bilibiliaudio.service.LinkResolveService;
import com.example.bilibiliaudio.service.TaskService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/tasks")
@Validated
public class TaskController {

    private final TaskService taskService;
    private final LinkResolveService linkResolveService;

    public TaskController(TaskService taskService, LinkResolveService linkResolveService) {
        this.taskService = taskService;
        this.linkResolveService = linkResolveService;
    }

    @PostMapping("/resolve-links")
    public ResolveLinksResponse resolveLinks(@Valid @RequestBody CreateTaskRequest request) {
        return new ResolveLinksResponse(linkResolveService.resolveLinks(request.getLinks()));
    }

    @PostMapping
    public ResponseEntity<CreateTaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        CreateTaskResponse response = taskService.createTask(request.getLinks());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{taskId}")
    public TaskDetailResponse getTask(@PathVariable String taskId) {
        return taskService.getTaskDetail(taskId);
    }

    @GetMapping("/{taskId}/download")
    public ResponseEntity<Resource> download(@PathVariable String taskId) {
        Path zipPath = taskService.getDownloadPath(taskId);
        FileSystemResource resource = new FileSystemResource(zipPath);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(zipPath.getFileName().toString(), StandardCharsets.UTF_8)
                .build());
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        return new ResponseEntity<Resource>(resource, headers, HttpStatus.OK);
    }
}