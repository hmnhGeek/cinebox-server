
package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.enums.CollectionSyncStatus;
import com.webvideoplayer.player.utils.VideoDurationUtils;
import com.webvideoplayer.player.utils.VideoPreviewUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PreviewGenerationService {

    private final Map<String, Integer> previewProgress =
            new ConcurrentHashMap<>();
    private final Map<String, CollectionSyncStatus> syncStatus =
            new ConcurrentHashMap<>();

    private static final List<String> VIDEO_EXTENSIONS = List.of(
            ".mp4",
            ".mkv",
            ".avi",
            ".mov",
            ".webm",
            ".m4v",
            ".ts"
    );

    public void markQueued(String collectionId) {
        syncStatus.put(collectionId, CollectionSyncStatus.QUEUED);
    }

    public CollectionSyncStatus getSyncStatus(String collectionId) {
        return syncStatus.getOrDefault(
                collectionId,
                CollectionSyncStatus.COMPLETED
        );
    }

    /*
     * Generate missing previews asynchronously.
     *
     * This method must be invoked through the Spring-managed
     * PreviewGenerationService bean for @Async to work.
     */
    @Async("previewExecutor")
    public void generatePreviewsAsync(
            Path collectionPath,
            String collectionId) {

        log.info(
                "Starting preview generation for collection: {}",
                collectionPath
        );

        syncStatus.put(collectionId, CollectionSyncStatus.IN_PROGRESS);

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(collectionPath)) {

            for (Path videoFolder : stream) {

                if (!Files.isDirectory(videoFolder)) {
                    continue;
                }

                processVideoFolder(videoFolder, collectionId);
            }

            syncStatus.put(collectionId, CollectionSyncStatus.COMPLETED);

        } catch (IOException e) {
            syncStatus.put(collectionId, CollectionSyncStatus.FAILED);
            log.error(
                    "Failed scanning collection: {}",
                    collectionPath,
                    e
            );
        }

        log.info(
                "Finished preview generation for collection: {}",
                collectionPath
        );
    }

    /*
     * Process one movie folder and generate its preview
     * only if the preview does not already exist.
     */
    private void processVideoFolder(
            Path videoFolder,
            String collectionId) {

        try {
            Path videoFile = findVideoFile(videoFolder);

            if (videoFile == null) {
                log.warn(
                        "No video found in folder: {}",
                        videoFolder
                );
                return;
            }

            Path previewPath = videoFolder.resolve("preview.mp4");

            // Skip FFmpeg entirely when the preview already exists.
            if (Files.exists(previewPath)) {
                log.info(
                        "Preview already exists. Skipping: {}",
                        previewPath
                );
                return;
            }

            String progressKey =
                    previewPath.toString() + "_" + collectionId;

            // Mark the task as queued/starting.
            previewProgress.put(progressKey, 0);

            double duration =
                    VideoDurationUtils.getDuration(videoFile);

            VideoPreviewUtils.generatePreview(
                    videoFile,
                    videoFolder,
                    duration,
                    progress -> previewProgress.put(
                            progressKey,
                            progress
                    )
            );

            log.info(
                    "Preview generation completed: {}",
                    previewPath
            );

        } catch (Exception e) {
            log.error(
                    "Failed generating preview for {}",
                    videoFolder,
                    e
            );
        }
    }

    /*
     * Find the video whose filename matches its folder name.
     *
     * Example:
     * Insidious/Insidious.mkv
     */
    private Path findVideoFile(Path videoFolder)
            throws IOException {

        String folderName =
                videoFolder.getFileName().toString();

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(videoFolder)) {

            for (Path file : stream) {

                if (!Files.isRegularFile(file)) {
                    continue;
                }

                String filename =
                        file.getFileName().toString();

                int extensionIndex = filename.lastIndexOf('.');

                if (extensionIndex <= 0) {
                    continue;
                }

                String baseName =
                        filename.substring(0, extensionIndex);

                String extension =
                        filename.substring(extensionIndex)
                                .toLowerCase();

                if (baseName.equals(folderName)
                        && VIDEO_EXTENSIONS.contains(extension)) {
                    return file;
                }
            }
        }

        return null;
    }

    /*
     * Return active preview-generation progress.
     *
     * Completed entries are excluded, matching the original
     * CollectionService behavior.
     */
    public Map<String, Integer> getPreviewProgress() {

        return previewProgress.entrySet()
                .stream()
                .filter(entry -> entry.getValue() != 100)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }
}
