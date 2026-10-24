package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.*;
import com.webvideoplayer.player.entities.Collection;
import com.webvideoplayer.player.entities.MediaItem;
import com.webvideoplayer.player.repositories.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private static final int DEFAULT_SUGGESTION_LIMIT = 8;
    private static final int MAX_SUGGESTION_LIMIT = 12;

    private final MediaItemRepository mediaItemRepository;
    private final CollectionService collectionService;

    public SearchSuggestionsResponse getSuggestions(
            String query,
            int requestedLimit
    ) {
        String normalizedQuery = normalizeQuery(query);

        if (normalizedQuery.length() < 2) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search query must contain at least 2 characters"
            );
        }

        int limit = requestedLimit <= 0
                ? DEFAULT_SUGGESTION_LIMIT
                : Math.min(requestedLimit, MAX_SUGGESTION_LIMIT);

        List<String> collectionIds = getAvailableCollectionIds();

        if (collectionIds.isEmpty()) {
            return new SearchSuggestionsResponse(
                    normalizedQuery,
                    List.of(),
                    false
            );
        }

        Page<MediaItem> page = mediaItemRepository.searchVideos(
                normalizedQuery,
                collectionIds,
                PageRequest.of(0, limit + 1)
        );

        boolean hasMore = page.getContent().size() > limit;

        List<SearchSuggestionItem> results = page.getContent()
                .stream()
                .limit(limit)
                .map(item -> toSuggestion(item, normalizedQuery))
                .toList();

        return new SearchSuggestionsResponse(
                normalizedQuery,
                results,
                hasMore
        );
    }

    public Page<VideoMetadata> searchResults(
            String query,
            Pageable pageable
    ) {
        String normalizedQuery = normalizeQuery(query);

        if (normalizedQuery.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search query cannot be empty"
            );
        }

        List<String> collectionIds = getAvailableCollectionIds();

        if (collectionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<MediaItem> videos = mediaItemRepository.searchVideos(
                normalizedQuery,
                collectionIds,
                pageable
        );

        return videos.map(item -> {
            Path collectionPath = Paths.get(
                    item.getCollection().getFolder()
            );

            return collectionService.mapToVideoMetadata(
                    item,
                    collectionPath
            );
        });
    }

    private SearchSuggestionItem toSuggestion(
            MediaItem item,
            String query
    ) {
        Collection collection = item.getCollection();

        Path collectionPath = Paths.get(
                collection.getFolder()
        ).toAbsolutePath().normalize();

        VideoMetadata metadata =
                collectionService.mapToVideoMetadata(
                        item,
                        collectionPath
                );

        String description = metadata.getPlot();

        if (description == null || description.isBlank()) {
            description = metadata.getOutline();
        }

        return new SearchSuggestionItem(
                item.getId(),
                metadata.getTitle(),
                description,
                metadata.getYear(),
                metadata.getThumbnailUrl(),
                collection.getId(),
                findMatchedFields(metadata, query)
        );
    }

    private List<String> findMatchedFields(
            VideoMetadata metadata,
            String query
    ) {
        String term = query.toLowerCase(Locale.ROOT);
        List<String> matchedBy = new ArrayList<>();

        if (contains(metadata.getTitle(), term)) {
            matchedBy.add("TITLE");
        }

        if (contains(metadata.getOriginalTitle(), term)) {
            matchedBy.add("ORIGINAL_TITLE");
        }

        if (contains(metadata.getSortTitle(), term)) {
            matchedBy.add("SORT_TITLE");
        }

        if (metadata.getTags() != null &&
                metadata.getTags().stream()
                        .anyMatch(tag -> contains(tag.getName(), term))) {
            matchedBy.add("TAG");
        }

        if (metadata.getGenres() != null &&
                metadata.getGenres().stream()
                        .anyMatch(genre ->
                                contains(genre.getName(), term))) {
            matchedBy.add("GENRE");
        }

        if (metadata.getActors() != null &&
                metadata.getActors().stream()
                        .anyMatch(actor ->
                                contains(actor.getName(), term))) {
            matchedBy.add("ACTOR");
        }

        if (metadata.getStudios() != null &&
                metadata.getStudios().stream()
                        .anyMatch(studio ->
                                contains(studio.getName(), term))) {
            matchedBy.add("STUDIO");
        }

        return matchedBy;
    }

    private boolean contains(String value, String term) {
        return value != null &&
                value.toLowerCase(Locale.ROOT).contains(term);
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim();
    }

    private List<String> getAvailableCollectionIds() {
        return collectionService.getAllCollections()
                .stream()
                .map(Collection::getId)
                .distinct()
                .toList();
    }
}