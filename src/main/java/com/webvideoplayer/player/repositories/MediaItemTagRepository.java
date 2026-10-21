package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.MediaItemTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaItemTagRepository
        extends JpaRepository<MediaItemTag, String> {

    void deleteByMediaItem_Id(String mediaItemId);
}