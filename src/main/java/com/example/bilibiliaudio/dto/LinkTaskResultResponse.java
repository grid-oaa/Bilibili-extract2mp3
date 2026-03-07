package com.example.bilibiliaudio.dto;

import com.example.bilibiliaudio.model.LinkTaskStatus;

public class LinkTaskResultResponse {

    private final int index;
    private final String link;
    private final String title;
    private final String fileName;
    private final LinkTaskStatus status;
    private final String errorMessage;

    public LinkTaskResultResponse(int index, String link, String title, String fileName, LinkTaskStatus status,
                                  String errorMessage) {
        this.index = index;
        this.link = link;
        this.title = title;
        this.fileName = fileName;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public int getIndex() {
        return index;
    }

    public String getLink() {
        return link;
    }

    public String getTitle() {
        return title;
    }

    public String getFileName() {
        return fileName;
    }

    public LinkTaskStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
