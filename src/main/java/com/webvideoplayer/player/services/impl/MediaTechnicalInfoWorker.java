
package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.entities.MediaItem;
import com.webvideoplayer.player.entities.MediaTechnicalInfo;
import com.webvideoplayer.player.enums.TechnicalInfoStatus;
import com.webvideoplayer.player.repositories.MediaItemRepository;
import com.webvideoplayer.player.repositories.MediaTechnicalInfoRepository;
import com.webvideoplayer.player.utils.MediaFilePathResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MediaTechnicalInfoWorker {

    private final MediaItemRepository mediaItemRepository;
    private final MediaTechnicalInfoRepository technicalInfoRepository;
    private final MediaFilePathResolver pathResolver;
    private final FFprobeService ffprobeService;
    private final TechnicalInfoJobRegistry jobRegistry;

    @Async("technicalInfoExecutor")
    public void extractAsync(String mediaItemId) {

        try {
            MediaTechnicalInfo info = technicalInfoRepository
                    .findByMediaItem_Id(mediaItemId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Technical information record was not found."
                    ));

            MediaItem mediaItem = mediaItemRepository
                    .findWithCollectionById(mediaItemId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Media item was not found."
                    ));

            info.setStatus(TechnicalInfoStatus.PROCESSING);
            info.setLastError(null);
            technicalInfoRepository.save(info);

            Path videoPath = pathResolver.resolve(mediaItem);

            // Capture the file signature before extraction.
            long initialFileSize = Files.size(videoPath);

            Instant initialLastModified = Files
                    .getLastModifiedTime(videoPath)
                    .toInstant();

            // Extract metadata using FFprobe.
            FFprobeService.FFprobeResult result =
                    ffprobeService.extract(videoPath);

            // Verify that the file was not modified during extraction.
            long finalFileSize = Files.size(videoPath);

            Instant finalLastModified = Files
                    .getLastModifiedTime(videoPath)
                    .toInstant();

            if (initialFileSize != finalFileSize
                    || !initialLastModified.equals(finalLastModified)) {
                throw new IllegalStateException(
                        "Media file changed during extraction. Please retry."
                );
            }

            // Video information.
            info.setResolutionLabel(result.resolution());
            info.setWidth(result.width());
            info.setHeight(result.height());

            info.setDurationSeconds(
                    result.durationSeconds() != null
                            ? result.durationSeconds()
                            : mediaItem.getDurationSeconds()
            );

            info.setVideoCodec(result.videoCodec());
            info.setFrameRate(result.frameRate());
            info.setVideoBitrate(result.videoBitrate());

            // Default audio track information.
            info.setAudioCodec(result.audioCodec());
            info.setAudioChannels(result.audioChannels());
            info.setAudioChannelLayout(result.audioChannelLayout());

            // Store the verified source-file signature.
            info.setSourceFileSizeBytes(finalFileSize);
            info.setSourceLastModifiedAt(finalLastModified);

            info.setExtractedAt(Instant.now());
            info.setStatus(TechnicalInfoStatus.COMPLETED);
            info.setLastError(null);

            technicalInfoRepository.save(info);

        } catch (Exception exception) {

            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            technicalInfoRepository.findByMediaItem_Id(mediaItemId)
                    .ifPresent(info -> {
                        info.setStatus(TechnicalInfoStatus.FAILED);
                        info.setLastError(safeErrorMessage(exception));
                        technicalInfoRepository.save(info);
                    });

        } finally {
            jobRegistry.unregister(mediaItemId);
        }
    }

    private String safeErrorMessage(Exception exception) {

        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = "Technical information extraction failed.";
        }

        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();

        return message.length() <= 1000
                ? message
                : message.substring(0, 1000);
    }
}
