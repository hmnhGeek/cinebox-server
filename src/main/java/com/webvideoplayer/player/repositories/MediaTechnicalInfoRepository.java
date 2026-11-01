package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.MediaTechnicalInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaTechnicalInfoRepository
        extends JpaRepository<MediaTechnicalInfo, String> {

    Optional<MediaTechnicalInfo> findByMediaItem_Id(String mediaItemId);

    List<MediaTechnicalInfo> findAllByMediaItem_IdIn(
            Collection<String> mediaItemIds
    );

    void deleteByMediaItem_Id(String mediaItemId);
}