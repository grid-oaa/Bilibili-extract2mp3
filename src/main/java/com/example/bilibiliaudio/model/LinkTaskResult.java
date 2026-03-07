package com.example.bilibiliaudio.model;

public class LinkTaskResult {

    private final int index;
    private final String link;
    private volatile String title;
    private volatile String fileName;
    private volatile LinkTaskStatus status;
    private volatile String errorMessage;

    public LinkTaskResult(int index, String link) {
        this.index = index;
        this.link = link;
        this.status = LinkTaskStatus.PENDING;
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

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public LinkTaskStatus getStatus() {
        return status;
    }

    public void setStatus(LinkTaskStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
