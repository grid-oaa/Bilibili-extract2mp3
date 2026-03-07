package com.example.bilibiliaudio.controller;

import com.example.bilibiliaudio.config.TaskProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private final TaskProperties taskProperties;

    public PageController(TaskProperties taskProperties) {
        this.taskProperties = taskProperties;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pollIntervalMs", taskProperties.getPollIntervalMs());
        return "index";
    }
}
