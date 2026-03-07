package com.example.bilibiliaudio.dto;

import javax.validation.constraints.NotEmpty;
import java.util.List;

public class CreateTaskRequest {

    @NotEmpty(message = "links 不能为空")
    private List<String> links;

    public List<String> getLinks() {
        return links;
    }

    public void setLinks(List<String> links) {
        this.links = links;
    }
}
