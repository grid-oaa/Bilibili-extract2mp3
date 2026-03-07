package com.example.bilibiliaudio.dto;

import java.util.List;

public class ResolveLinksResponse {

    private final List<ResolvedLinkGroupResponse> groups;

    public ResolveLinksResponse(List<ResolvedLinkGroupResponse> groups) {
        this.groups = groups;
    }

    public List<ResolvedLinkGroupResponse> getGroups() {
        return groups;
    }
}