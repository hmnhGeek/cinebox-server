package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.PlaybackProgress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface PlaybackProgressRepository
        extends JpaRepository<PlaybackProgress, String> {

    Optional<PlaybackProgress> findByMediaItem_Id(String mediaItemId);

    Page<PlaybackProgress>
    findByCompletedFalseAndPositionSecondsGreaterThanEqualOrderByLastPlayedAtDesc(
            Long minimumPositionSeconds,
            Pageable pageable
    );

    Page<PlaybackProgress>
    findByCompletedFalseAndPositionSecondsGreaterThanEqualAndMediaItem_Collection_IdInOrderByLastPlayedAtDesc(
            Long minimumPositionSeconds,
            Collection<String> collectionIds,
            Pageable pageable
    );

    void deleteByMediaItem_Id(String mediaItemId);
}
