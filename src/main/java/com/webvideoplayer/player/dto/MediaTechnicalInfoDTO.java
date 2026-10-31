package com.webvideoplayer.player.dto;

import com.webvideoplayer.player.enums.TechnicalInfoStatus;

import java.time.Instant;

public record MediaTechnicalInfoDTO(
        String mediaItemId,
        TechnicalInfoStatus status,
        String resolution,
        Integer width,
        Integer height,
        Long durationSeconds,
        Long fileSizeBytes,
        String videoCodec,
        Double frameRate,
        Long videoBitrate,
        String audioCodec,
        Integer audioChannels,
        String audioChannelLayout,
        Instant extractedAt,
        String error
) {
}