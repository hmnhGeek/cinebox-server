package com.webvideoplayer.player.dto;

import lombok.Data;

@Data
public class CreateCollectionRequest {
    private String title;
    private String folder;
    private String tagline;
}