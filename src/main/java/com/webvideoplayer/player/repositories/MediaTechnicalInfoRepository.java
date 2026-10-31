package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.MediaTechnicalInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaTechnicalInfoRepository
        extends JpaRepository<MediaTechnicalInfo, String> {

    Optional<MediaTechnicalInfo> findByMediaItem_Id(String mediaItemId);

    void deleteByMediaItem_Id(String mediaItemId);
}
