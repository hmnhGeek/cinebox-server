package com.webvideoplayer.player.dto;

public record UpdatePlaybackProgressRequest(
        Long positionSeconds,
        Long durationSeconds
) {
}