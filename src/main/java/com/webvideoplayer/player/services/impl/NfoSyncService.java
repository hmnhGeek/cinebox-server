package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.NfoSyncStatusDTO;
import com.webvideoplayer.player.dto.VideoMetadata;
import com.webvideoplayer.player.entities.MediaItem;
import com.webvideoplayer.player.entities.MediaItemNfoStatus;
import com.webvideoplayer.player.repositories.MediaItemNfoStatusRepository;
import com.webvideoplayer.player.repositories.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NfoSyncService {

    private final MediaItemRepository mediaItemRepository;
    private final MediaItemNfoStatusRepository nfoStatusRepository;
    private final CollectionService collectionService;
    private final NfoFileService nfoFileService;

    @Transactional
    public NfoSyncStatusDTO synchronize(String mediaItemId) {

        MediaItem item = getMediaItem(mediaItemId);

        MediaItemNfoStatus status = getOrCreateStatus(item);

        status.setStatus("PENDING");
        status.setErrorMessage(null);
        status.setLastAttemptAt(Instant.now());

        nfoStatusRepository.saveAndFlush(status);

        try {
            Path collectionFolder = Path.of(
                    item.getCollection().getFolder()
            ).toAbsolutePath().normalize();

            VideoMetadata metadata =
                    collectionService.mapToVideoMetadata(
                            item,
                            collectionFolder
                    );

            Path movieFolder = resolveMovieFolder(item);

            nfoFileService.writeNfo(movieFolder, metadata);

            status.setStatus("SYNCED");
            status.setErrorMessage(null);
            status.setLastSyncedAt(Instant.now());

        } catch (Exception e) {

            status.setStatus("FAILED");
            status.setErrorMessage(limitErrorMessage(e));

        }

        return toDTO(nfoStatusRepository.save(status));
    }

    @Transactional(readOnly = true)
    public NfoSyncStatusDTO getStatus(String mediaItemId) {

        MediaItem item = getMediaItem(mediaItemId);

        return nfoStatusRepository
                .findByMediaItem_Id(mediaItemId)
                .map(this::toDTO)
                .orElseGet(() -> {

                    String status;

                    try {
                        status = nfoFileService.hasNfoFile(
                                resolveMovieFolder(item)
                        ) ? "SYNCED" : "MISSING";

                    } catch (IOException e) {
                        status = "UNKNOWN";
                    }

                    return NfoSyncStatusDTO.builder()
                            .mediaItemId(mediaItemId)
                            .status(status)
                            .build();
                });
    }

    private MediaItem getMediaItem(String mediaItemId) {

        return mediaItemRepository.findById(mediaItemId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Media item not found: " + mediaItemId
                        )
                );
    }

    private MediaItemNfoStatus getOrCreateStatus(MediaItem item) {

        return nfoStatusRepository
                .findByMediaItem_Id(item.getId())
                .orElseGet(() -> {
                    MediaItemNfoStatus status =
                            new MediaItemNfoStatus();

                    status.setMediaItem(item);
                    status.setStatus("PENDING");

                    return status;
                });
    }

    private Path resolveMovieFolder(MediaItem item) {

        Path collectionFolder = Path.of(
                item.getCollection().getFolder()
        ).toAbsolutePath().normalize();

        Path videoPath = collectionFolder.resolve(
                item.getRelativePath()
        ).normalize();

        Path movieFolder = videoPath.getParent();

        if (movieFolder == null
                || !movieFolder.startsWith(collectionFolder)) {
            throw new IllegalArgumentException(
                    "Invalid media item path: " + item.getRelativePath()
            );
        }

        return movieFolder;
    }

    private String limitErrorMessage(Exception exception) {

        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }

        return message.length() > 2000
                ? message.substring(0, 2000)
                : message;
    }

    private NfoSyncStatusDTO toDTO(MediaItemNfoStatus status) {

        return NfoSyncStatusDTO.builder()
                .mediaItemId(status.getMediaItem().getId())
                .status(status.getStatus())
                .errorMessage(status.getErrorMessage())
                .lastAttemptAt(status.getLastAttemptAt())
                .lastSyncedAt(status.getLastSyncedAt())
                .build();
    }
}