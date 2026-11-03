package com.webvideoplayer.player.dto;

import java.time.Instant;

public record PlaybackProgressDTO(
        String mediaItemId,
        String title,
        String collectionId,
        String thumbnailUrl,
        String posterUrl,
        Long positionSeconds,
        Long durationSeconds,
        Double progressPercent,
        Long remainingSeconds,
        boolean completed,
        Instant lastPlayedAt,
        Instant createdAt,
        Instant updatedAt
) {
}