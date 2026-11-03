package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.PlaybackProgressDTO;
import com.webvideoplayer.player.dto.UpdatePlaybackProgressRequest;
import com.webvideoplayer.player.entities.MediaItem;
import com.webvideoplayer.player.entities.PlaybackProgress;
import com.webvideoplayer.player.repositories.MediaItemRepository;
import com.webvideoplayer.player.repositories.PlaybackProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PlaybackProgressService {

    private static final long MINIMUM_CONTINUE_WATCHING_SECONDS = 30L;

    private static final double COMPLETION_THRESHOLD_PERCENT = 95.0;

    private static final String THUMBNAIL_URL =
            "/api/v1/collections/thumbnail?mediaItemId=";

    private static final String POSTER_URL =
            "/api/v1/collections/poster?mediaItemId=";

    private final PlaybackProgressRepository playbackProgressRepository;

    private final MediaItemRepository mediaItemRepository;

    private final CollectionService collectionService;

    /*
     * Returns unfinished playback progress only for collections
     * whose folders are currently available.
     *
     * Availability is checked at request time, so locked Cryptomator
     * collections are excluded without deleting their progress records.
     *
     * Filtering is performed by the database before pagination,
     * preserving correct page content and total-element counts.
     */
    @Transactional(readOnly = true)
    public Page<PlaybackProgressDTO> getContinueWatching(
            Pageable pageable
    ) {

        List<String> availableCollectionIds =
                collectionService.getAvailableCollectionIds();

        if (availableCollectionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        return playbackProgressRepository
                .findByCompletedFalseAndPositionSecondsGreaterThanEqualAndMediaItem_Collection_IdInOrderByLastPlayedAtDesc(
                        MINIMUM_CONTINUE_WATCHING_SECONDS,
                        availableCollectionIds,
                        pageable
                )
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public PlaybackProgressDTO getProgress(String mediaItemId) {

        PlaybackProgress progress = playbackProgressRepository
                .findByMediaItem_Id(mediaItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Playback progress not found for this media item."
                ));

        return toDTO(progress);
    }

    public PlaybackProgressDTO updateProgress(
            String mediaItemId,
            UpdatePlaybackProgressRequest request
    ) {

        validateRequest(request);

        MediaItem mediaItem = mediaItemRepository
                .findById(mediaItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Media item not found."
                ));

        PlaybackProgress progress = playbackProgressRepository
                .findByMediaItem_Id(mediaItemId)
                .orElseGet(() -> {
                    PlaybackProgress newProgress =
                            new PlaybackProgress();

                    newProgress.setMediaItem(mediaItem);
                    newProgress.setPositionSeconds(0L);
                    newProgress.setProgressPercent(0.0);
                    newProgress.setCompleted(false);

                    return newProgress;
                });

        Long durationSeconds = resolveDuration(
                request.durationSeconds(),
                progress.getDurationSeconds(),
                mediaItem.getDurationSeconds()
        );

        long positionSeconds = request.positionSeconds();

        if (durationSeconds != null && durationSeconds > 0) {
            positionSeconds = Math.min(positionSeconds, durationSeconds);
        }

        double progressPercent = calculateProgressPercent(
                positionSeconds,
                durationSeconds
        );

        boolean completed =
                durationSeconds != null
                        && durationSeconds > 0
                        && progressPercent >= COMPLETION_THRESHOLD_PERCENT;

        progress.setPositionSeconds(positionSeconds);
        progress.setDurationSeconds(durationSeconds);
        progress.setProgressPercent(progressPercent);
        progress.setCompleted(completed);
        progress.setLastPlayedAt(Instant.now());

        PlaybackProgress saved =
                playbackProgressRepository.save(progress);

        return toDTO(saved);
    }

    public void deleteProgress(String mediaItemId) {

        playbackProgressRepository
                .findByMediaItem_Id(mediaItemId)
                .ifPresent(playbackProgressRepository::delete);
    }

    public PlaybackProgressDTO markCompleted(String mediaItemId) {

        MediaItem mediaItem = mediaItemRepository
                .findById(mediaItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Media item not found."
                ));

        PlaybackProgress progress = playbackProgressRepository
                .findByMediaItem_Id(mediaItemId)
                .orElseGet(() -> {
                    PlaybackProgress newProgress =
                            new PlaybackProgress();

                    newProgress.setMediaItem(mediaItem);
                    return newProgress;
                });

        Long durationSeconds = resolveDuration(
                null,
                progress.getDurationSeconds(),
                mediaItem.getDurationSeconds()
        );

        progress.setDurationSeconds(durationSeconds);
        progress.setCompleted(true);
        progress.setProgressPercent(100.0);
        progress.setLastPlayedAt(Instant.now());

        if (durationSeconds != null && durationSeconds > 0) {
            progress.setPositionSeconds(durationSeconds);
        } else if (progress.getPositionSeconds() == null) {
            progress.setPositionSeconds(0L);
        }

        return toDTO(playbackProgressRepository.save(progress));
    }

    private void validateRequest(
            UpdatePlaybackProgressRequest request
    ) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Playback progress request is required."
            );
        }

        if (request.positionSeconds() == null
                || request.positionSeconds() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "positionSeconds must be zero or greater."
            );
        }

        if (request.durationSeconds() != null
                && request.durationSeconds() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "durationSeconds must be greater than zero."
            );
        }
    }

    private Long resolveDuration(
            Long requestedDuration,
            Long existingDuration,
            Long mediaItemDuration
    ) {

        if (requestedDuration != null && requestedDuration > 0) {
            return requestedDuration;
        }

        if (existingDuration != null && existingDuration > 0) {
            return existingDuration;
        }

        if (mediaItemDuration != null && mediaItemDuration > 0) {
            return mediaItemDuration;
        }

        return null;
    }

    private double calculateProgressPercent(
            long positionSeconds,
            Long durationSeconds
    ) {

        if (durationSeconds == null || durationSeconds <= 0) {
            return 0.0;
        }

        double percentage =
                ((double) positionSeconds / durationSeconds) * 100.0;

        percentage = Math.min(100.0, Math.max(0.0, percentage));

        return BigDecimal.valueOf(percentage)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private PlaybackProgressDTO toDTO(
            PlaybackProgress progress
    ) {

        MediaItem mediaItem = progress.getMediaItem();

        Long positionSeconds = progress.getPositionSeconds() == null
                ? 0L
                : progress.getPositionSeconds();

        Long durationSeconds = progress.getDurationSeconds();

        Long remainingSeconds = null;

        if (durationSeconds != null && durationSeconds > 0) {
            remainingSeconds = Math.max(
                    0L,
                    durationSeconds - positionSeconds
            );
        }

        return new PlaybackProgressDTO(
                mediaItem.getId(),
                mediaItem.getTitle(),
                mediaItem.getCollection().getId(),
                THUMBNAIL_URL + mediaItem.getId(),
                POSTER_URL + mediaItem.getId(),
                positionSeconds,
                durationSeconds,
                progress.getProgressPercent() == null
                        ? 0.0
                        : progress.getProgressPercent(),
                remainingSeconds,
                progress.isCompleted(),
                progress.getLastPlayedAt(),
                progress.getCreatedAt(),
                progress.getUpdatedAt()
        );
    }
}