package com.webvideoplayer.player.dto;

public record MediaBarItemDTO(
        String id,
        String collectionId,
        String title,
        String plot,
        Integer year,
        Integer runtime,
        Long durationSeconds,
        String backdropUrl
) {
}