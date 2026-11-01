package com.webvideoplayer.player.dto;

import java.util.Map;

public record UpdateSettingsRequest(
        Map<String, Object> settings
) {
}