
package com.webvideoplayer.player.dto;

import java.util.List;

public record SearchSuggestionsResponse(
        String query,
        List<ActorBrowseDTO> actors,
        List<SearchSuggestionItem> results,
        boolean hasMore
) {
}
