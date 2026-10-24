package com.webvideoplayer.player.dto;

import java.util.List;

public record SearchSuggestionItem(
        String mediaItemId,
        String title,
        String description,
        Integer year,
        String thumbnailUrl,
        String collectionId,
        List<String> matchedBy
) {
}