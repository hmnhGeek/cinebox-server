package com.webvideoplayer.player.controllers;

import com.webvideoplayer.player.dto.SearchSuggestionsResponse;
import com.webvideoplayer.player.dto.VideoMetadata;
import com.webvideoplayer.player.services.impl.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /*
     * Live header autocomplete.
     *
     * Example:
     * GET /api/v1/search/suggestions?q=batman&limit=8
     */
    @GetMapping("/suggestions")
    public SearchSuggestionsResponse getSuggestions(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") int limit
    ) {
        return searchService.getSuggestions(q, limit);
    }

    /*
     * Full paginated search results.
     *
     * Example:
     * GET /api/v1/search/results?q=batman&page=0&size=24
     */
    @GetMapping("/results")
    public Page<VideoMetadata> searchResults(
            @RequestParam String q,
            @PageableDefault(size = 24) Pageable pageable
    ) {
        return searchService.searchResults(q, pageable);
    }
}