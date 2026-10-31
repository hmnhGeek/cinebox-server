package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.MediaTechnicalInfoDTO;
import com.webvideoplayer.player.entities.MediaItem;
import com.webvideoplayer.player.entities.MediaTechnicalInfo;
import com.webvideoplayer.player.enums.TechnicalInfoStatus;
import com.webvideoplayer.player.repositories.MediaItemRepository;
import com.webvideoplayer.player.repositories.MediaTechnicalInfoRepository;
import com.webvideoplayer.player.utils.MediaFilePathResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MediaTechnicalInfoService {

    private final MediaItemRepository mediaItemRepository;
    private final MediaTechnicalInfoRepository technicalInfoRepository;
    private final MediaFilePathResolver pathResolver;
    private final MediaTechnicalInfoWorker worker;
    private final TechnicalInfoJobRegistry jobRegistry;

    public MediaTechnicalInfoDTO getTechnicalInfo(String mediaItemId) {

        MediaItem mediaItem = getMediaItem(mediaItemId);

        return technicalInfoRepository.findByMediaItem_Id(mediaItemId)
                .map(info -> toDTO(info, mediaItem))
                .orElseGet(() -> new MediaTechnicalInfoDTO(
                        mediaItemId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        mediaItem.getFileSizeBytes(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
    }

    public MediaTechnicalInfoDTO requestExtraction(
            String mediaItemId,
            boolean force
    ) {

        MediaItem mediaItem = getMediaItem(mediaItemId);

        if (!jobRegistry.register(mediaItemId)) {
            return getTechnicalInfo(mediaItemId);
        }

        try {
            Path videoPath = pathResolver.resolve(mediaItem);

            long currentFileSize = Files.size(videoPath);
            Instant currentLastModified = Files
                    .getLastModifiedTime(videoPath)
                    .toInstant();

            MediaTechnicalInfo info = technicalInfoRepository
                    .findByMediaItem_Id(mediaItemId)
                    .orElseGet(() -> {
                        MediaTechnicalInfo newInfo = new MediaTechnicalInfo();
                        newInfo.setMediaItem(mediaItem);
                        return newInfo;
                    });

            boolean cacheIsValid =
                    info.getStatus() == TechnicalInfoStatus.COMPLETED
                            && info.getSourceFileSizeBytes() != null
                            && info.getSourceFileSizeBytes() == currentFileSize
                            && info.getSourceLastModifiedAt() != null
                            && info.getSourceLastModifiedAt().equals(
                            currentLastModified
                    );

            if (cacheIsValid && !force) {
                jobRegistry.unregister(mediaItemId);
                return toDTO(info, mediaItem);
            }

            info.setStatus(TechnicalInfoStatus.QUEUED);
            info.setLastError(null);

            technicalInfoRepository.saveAndFlush(info);

            try {
                worker.extractAsync(mediaItemId);
            } catch (TaskRejectedException | IllegalStateException exception) {

                info.setStatus(TechnicalInfoStatus.FAILED);
                info.setLastError(
                        "Technical information extraction could not be queued. "
                                + "Please try again later."
                );

                technicalInfoRepository.save(info);
                jobRegistry.unregister(mediaItemId);
            }

            return toDTO(info, mediaItem);

        } catch (IOException exception) {

            jobRegistry.unregister(mediaItemId);

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unable to access the media file: " + exception.getMessage()
            );

        } catch (RuntimeException exception) {

            jobRegistry.unregister(mediaItemId);
            throw exception;
        }
    }

    private MediaItem getMediaItem(String mediaItemId) {

        return mediaItemRepository.findWithCollectionById(mediaItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Media item not found."
                ));
    }

    private MediaTechnicalInfoDTO toDTO(
            MediaTechnicalInfo info,
            MediaItem mediaItem
    ) {

        return new MediaTechnicalInfoDTO(
                mediaItem.getId(),
                info.getStatus(),
                info.getResolutionLabel(),
                info.getWidth(),
                info.getHeight(),
                info.getDurationSeconds(),
                mediaItem.getFileSizeBytes(),
                info.getVideoCodec(),
                info.getFrameRate(),
                info.getVideoBitrate(),
                info.getAudioCodec(),
                info.getAudioChannels(),
                info.getAudioChannelLayout(),
                info.getExtractedAt(),
                info.getLastError()
        );
    }
}