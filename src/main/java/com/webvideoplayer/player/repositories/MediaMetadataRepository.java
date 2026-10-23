
package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.MediaMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MediaMetadataRepository
        extends JpaRepository<MediaMetadata, String> {

    Optional<MediaMetadata> findByMediaItem_Id(String mediaItemId);
}
