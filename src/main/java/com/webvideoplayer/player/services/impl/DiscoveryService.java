
package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.VideoMetadata;
import com.webvideoplayer.player.entities.Collection;
import com.webvideoplayer.player.entities.MediaItem;
import com.webvideoplayer.player.repositories.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscoveryService {

    private final MediaItemRepository mediaItemRepository;
    private final CollectionService collectionService;

    public Page<VideoMetadata> getVideosByTag(
            String tagId,
            Pageable pageable
    ) {
        List<String> collectionIds = getAvailableCollectionIds();

        if (collectionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<MediaItem> videos = mediaItemRepository.findVideosByTag(
                tagId,
                collectionIds,
                pageable
        );

        return mapToVideoMetadata(videos);
    }

    public Page<VideoMetadata> getVideosByGenre(
            String genreId,
            Pageable pageable
    ) {
        List<String> collectionIds = getAvailableCollectionIds();

        if (collectionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<MediaItem> videos = mediaItemRepository.findVideosByGenre(
                genreId,
                collectionIds,
                pageable
        );

        return mapToVideoMetadata(videos);
    }

    public Page<VideoMetadata> getVideosByActor(
            String personId,
            Pageable pageable
    ) {
        List<String> collectionIds = getAvailableCollectionIds();

        if (collectionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<MediaItem> videos = mediaItemRepository.findVideosByActor(
                personId,
                collectionIds,
                pageable
        );

        return mapToVideoMetadata(videos);
    }

    public Page<VideoMetadata> getVideosByDirector(
            String personId,
            Pageable pageable
    ) {
        List<String> collectionIds = getAvailableCollectionIds();

        if (collectionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<MediaItem> videos = mediaItemRepository.findVideosByDirector(
                personId,
                collectionIds,
                pageable
        );

        return mapToVideoMetadata(videos);
    }

    public Page<VideoMetadata> getVideosByStudio(
            String studioId,
            Pageable pageable
    ) {
        List<String> collectionIds = getAvailableCollectionIds();

        if (collectionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<MediaItem> videos = mediaItemRepository.findVideosByStudio(
                studioId,
                collectionIds,
                pageable
        );

        return mapToVideoMetadata(videos);
    }

    private Page<VideoMetadata> mapToVideoMetadata(
            Page<MediaItem> videos
    ) {
        return videos.map(item -> {
            Collection collection = item.getCollection();
            Path collectionPath = Paths.get(collection.getFolder());

            return collectionService.mapToVideoMetadata(
                    item,
                    collectionPath
            );
        });
    }

    private List<String> getAvailableCollectionIds() {
        return collectionService.getAllCollections()
                .stream()
                .map(Collection::getId)
                .distinct()
                .toList();
    }
}
