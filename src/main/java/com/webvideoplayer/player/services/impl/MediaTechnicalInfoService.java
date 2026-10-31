
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

            boolean cacheIsValid = isCacheValid(
                    info,
                    currentFileSize,
                    currentLastModified
            );

            /*
             * A previous extraction is still valid.
             *
             * This also repairs records that were incorrectly marked FAILED
             * by an earlier queue rejection.
             */
            if (cacheIsValid && !force) {

                boolean statusNeedsRepair =
                        info.getStatus() != TechnicalInfoStatus.COMPLETED
                                || info.getLastError() != null;

                if (statusNeedsRepair) {
                    info.setStatus(TechnicalInfoStatus.COMPLETED);
                    info.setLastError(null);
                    technicalInfoRepository.saveAndFlush(info);
                }

                jobRegistry.unregister(mediaItemId);

                return toDTO(info, mediaItem);
            }

            info.setStatus(TechnicalInfoStatus.QUEUED);
            info.setLastError(null);

            technicalInfoRepository.saveAndFlush(info);

            try {
                worker.extractAsync(mediaItemId);

            } catch (TaskRejectedException | IllegalStateException exception) {

                /*
                 * Queue rejection does not invalidate an earlier successful
                 * extraction if the underlying file has not changed.
                 */
                if (cacheIsValid) {
                    info.setStatus(TechnicalInfoStatus.COMPLETED);
                    info.setLastError(null);

                } else {
                    info.setStatus(TechnicalInfoStatus.FAILED);
                    info.setLastError(
                            "Technical information extraction could not be queued. "
                                    + "Please try again later."
                    );
                }

                technicalInfoRepository.saveAndFlush(info);

                jobRegistry.unregister(mediaItemId);
            }

            /*
             * Reload the record because the asynchronous worker may already
             * have started or completed by this point.
             */
            return getTechnicalInfo(mediaItemId);

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

    private boolean isCacheValid(
            MediaTechnicalInfo info,
            long currentFileSize,
            Instant currentLastModified
    ) {

        /*
         * Do not rely exclusively on the status field here.
         *
         * A record might have been incorrectly marked FAILED while still
         * containing a perfectly valid previous extraction.
         */
        return info.getExtractedAt() != null
                && info.getSourceFileSizeBytes() != null
                && info.getSourceFileSizeBytes() == currentFileSize
                && info.getSourceLastModifiedAt() != null
                && info.getSourceLastModifiedAt().equals(currentLastModified);
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
