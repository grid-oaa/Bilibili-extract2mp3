package com.example.bilibiliaudio.dto;

public class ResolvedLinkOptionResponse {

    private final String link;
    private final String title;
    private final Integer page;
    private final boolean selected;

    public ResolvedLinkOptionResponse(String link, String title, Integer page, boolean selected) {
        this.link = link;
        this.title = title;
        this.page = page;
        this.selected = selected;
    }

    public String getLink() {
        return link;
    }

    public String getTitle() {
        return title;
    }

    public Integer getPage() {
        return page;
    }

    public boolean isSelected() {
        return selected;
    }
}