package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.MediaItemNfoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaItemNfoStatusRepository
        extends JpaRepository<MediaItemNfoStatus, String> {

    Optional<MediaItemNfoStatus> findByMediaItem_Id(String mediaItemId);

    void deleteByMediaItem_Id(String mediaItemId);
}
