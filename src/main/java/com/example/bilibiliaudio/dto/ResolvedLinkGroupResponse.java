package com.example.bilibiliaudio.dto;

import java.util.List;

public class ResolvedLinkGroupResponse {

    private final String sourceLink;
    private final String title;
    private final boolean multipleParts;
    private final List<ResolvedLinkOptionResponse> options;

    public ResolvedLinkGroupResponse(String sourceLink, String title, boolean multipleParts,
                                     List<ResolvedLinkOptionResponse> options) {
        this.sourceLink = sourceLink;
        this.title = title;
        this.multipleParts = multipleParts;
        this.options = options;
    }

    public String getSourceLink() {
        return sourceLink;
    }

    public String getTitle() {
        return title;
    }

    public boolean isMultipleParts() {
        return multipleParts;
    }

    public List<ResolvedLinkOptionResponse> getOptions() {
        return options;
    }
}