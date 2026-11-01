package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.MediaMetadata;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaMetadataRepository
        extends JpaRepository<MediaMetadata, String> {

    Optional<MediaMetadata> findByMediaItem_Id(String mediaItemId);

    @EntityGraph(attributePaths = {"genres", "studios"})
    List<MediaMetadata> findAllByMediaItem_IdIn(
            Collection<String> mediaItemIds
    );
}