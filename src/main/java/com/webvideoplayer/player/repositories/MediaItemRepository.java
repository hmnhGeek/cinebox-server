
package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.MediaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaItemRepository
        extends JpaRepository<MediaItem, String> {

    List<MediaItem> findByCollection_IdOrderByTitleAsc(
            String collectionId
    );

    Optional<MediaItem> findByCollection_IdAndRelativePath(
            String collectionId,
            String relativePath
    );

    boolean existsByCollection_IdAndRelativePath(
            String collectionId,
            String relativePath
    );
}
