package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.VideoMetadata;
import com.webvideoplayer.player.entities.Collection;
import com.webvideoplayer.player.repositories.CollectionRepository;
import com.webvideoplayer.player.utils.VideoDurationUtils;
import com.webvideoplayer.player.utils.VideoPreviewUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final VideoScannerService videoScannerService;

    public void synchroniseCollection(String collectionId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Collection not found"
                        )
                );

        Path collectionPath =
                Paths.get(collection.getFolder())
                        .toAbsolutePath()
                        .normalize();

        generatePreviewsAsync(collectionPath);
    }

    public Collection addCollection(String title, String folder) {

        Path collectionPath =
                Paths.get(folder)
                        .toAbsolutePath()
                        .normalize();

        if (!Files.exists(collectionPath)) {
            throw new IllegalArgumentException(
                    "Folder does not exist: " + collectionPath
            );
        }

        if (!Files.isDirectory(collectionPath)) {
            throw new IllegalArgumentException(
                    "Path is not a directory: " + collectionPath
            );
        }

        if (collectionRepository.existsByFolder(
                collectionPath.toString()
        )) {
            throw new IllegalArgumentException(
                    "Collection already exists: "
                            + collectionPath
            );
        }

        Collection collection = new Collection(null, title, folder);

        Collection saved =
                collectionRepository.save(collection);

        /*
         * Preview generation is deliberately asynchronous.
         *
         * The POST API does not remain blocked while FFmpeg
         * processes potentially large video files.
         */
        generatePreviewsAsync(collectionPath);

        return saved;
    }

    public void deleteCollection(String id) {

        Collection collection =
                collectionRepository.findById(id)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Collection not found: " + id
                                )
                        );

        /*
         * We only remove the MongoDB collection record.
         *
         * The actual videos/files are NOT deleted.
         */
        collectionRepository.delete(collection);
    }

    public List<VideoMetadata> getVideos(
            String id
    ) {

        Collection collection =
                collectionRepository.findById(id)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Collection not found: " + id
                                )
                        );

        return videoScannerService.getVideos(
                Paths.get(collection.getFolder())
        );
    }

    @Async
    public void generatePreviewsAsync(
            Path collectionPath
    ) {
        log.info(
                "Starting preview generation for collection: {}",
                collectionPath
        );

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(collectionPath)) {

            for (Path videoFolder : stream) {

                if (!Files.isDirectory(videoFolder)) {
                    continue;
                }

                processVideoFolder(videoFolder);
            }

        } catch (IOException e) {

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

    private void processVideoFolder(
            Path videoFolder
    ) {

        try {

            Path videoFile =
                    findVideoFile(videoFolder);

            if (videoFile == null) {
                log.warn(
                        "No video found in folder: {}",
                        videoFolder
                );
                return;
            }

            Path preview =
                    videoFolder.resolve("preview.mp4");

            /*
             * Most important check:
             *
             * If preview already exists, don't even invoke
             * ffprobe/ffmpeg.
             */
            if (Files.exists(preview)) {

                log.info(
                        "Preview already exists. Skipping: {}",
                        preview
                );

                return;
            }

            double duration =
                    VideoDurationUtils.getDuration(
                            videoFile
                    );

            VideoPreviewUtils.generatePreview(
                    videoFile,
                    videoFolder,
                    duration
            );

        } catch (Exception e) {

            log.error(
                    "Failed generating preview for {}",
                    videoFolder,
                    e
            );
        }
    }

    private Path findVideoFile(
            Path videoFolder
    ) throws IOException {

        String folderName =
                videoFolder.getFileName().toString();

        List<String> extensions =
                List.of(
                        ".mp4",
                        ".mkv",
                        ".avi",
                        ".mov",
                        ".webm",
                        ".m4v",
                        ".ts"
                );

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(videoFolder)) {

            for (Path file : stream) {

                if (!Files.isRegularFile(file)) {
                    continue;
                }

                String filename =
                        file.getFileName().toString();

                int extensionIndex =
                        filename.lastIndexOf('.');

                if (extensionIndex <= 0) {
                    continue;
                }

                String baseName =
                        filename.substring(
                                0,
                                extensionIndex
                        );

                String extension =
                        filename.substring(
                                extensionIndex
                        ).toLowerCase();

                if (baseName.equals(folderName)
                        && extensions.contains(extension)) {

                    return file;
                }
            }
        }

        return null;
    }

    public List<Collection> getAllCollections() {
        return collectionRepository.findAll()
                .stream()
                .filter(collection -> {
                    try {
                        Path folderPath = Paths.get(collection.getFolder());

                        return Files.isDirectory(folderPath)
                                && Files.isReadable(folderPath);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toList();
    }

    public Resource getThumbnail(
            String collectionId,
            String videoFolderName
    ) throws IOException {

        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Collection not found"
                        )
                );

        Path collectionFolder = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        Path videoFolder = collectionFolder.resolve(videoFolderName)
                .normalize();

        // Ensure the requested folder stays inside the collection folder.
        if (!videoFolder.startsWith(collectionFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid video folder name"
            );
        }
        if (!Files.isDirectory(videoFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Video folder not found"
            );
        }

        // Find thumbnail.jpg, thumbnail.png, thumbnail.webp, etc.
        Path thumbnailPath;

        try (Stream<Path> files = Files.list(videoFolder)) {
            thumbnailPath = files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String filename = path.getFileName().toString();
                        int dotIndex = filename.lastIndexOf('.');

                        String baseName = dotIndex > 0
                                ? filename.substring(0, dotIndex)
                                : filename;

                        return baseName.equalsIgnoreCase("thumbnail");
                    })
                    .findFirst()
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Thumbnail not found"
                            )
                    );
        }

        return new FileSystemResource(thumbnailPath);
    }

    public Resource getPreview(
            String collectionId,
            String videoFolderName
    ) throws IOException {

        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Collection not found"
                        )
                );

        Path collectionFolder = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        Path videoFolder = collectionFolder.resolve(videoFolderName)
                .normalize();

        // Ensure the requested folder stays inside the collection folder.
        if (!videoFolder.startsWith(collectionFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid video folder name"
            );
        }
        if (!Files.isDirectory(videoFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Video folder not found"
            );
        }

        Path previewPath;

        try (Stream<Path> files = Files.list(videoFolder)) {
            previewPath = files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String filename = path.getFileName().toString();
                        int dotIndex = filename.lastIndexOf('.');

                        String baseName = dotIndex > 0
                                ? filename.substring(0, dotIndex)
                                : filename;

                        return baseName.equalsIgnoreCase("preview");
                    })
                    .findFirst()
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Preview not found"
                            )
                    );
        }

        return new FileSystemResource(previewPath);
    }

    public Resource getPoster(
            String collectionId,
            String videoFolderName
    ) throws IOException {

        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Collection not found"
                        )
                );

        Path collectionFolder = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        Path videoFolder = collectionFolder.resolve(videoFolderName)
                .normalize();

        // Ensure the requested folder stays inside the collection folder.
        if (!videoFolder.startsWith(collectionFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid video folder name"
            );
        }
        if (!Files.isDirectory(videoFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Video folder not found"
            );
        }

        Path posterPath;

        try (Stream<Path> files = Files.list(videoFolder)) {
            posterPath = files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String filename = path.getFileName().toString();
                        int dotIndex = filename.lastIndexOf('.');

                        String baseName = dotIndex > 0
                                ? filename.substring(0, dotIndex)
                                : filename;

                        return baseName.equalsIgnoreCase("poster");
                    })
                    .findFirst()
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Poster not found"
                            )
                    );
        }

        return new FileSystemResource(posterPath);
    }

    public Resource getVideo(
            String collectionId,
            String videoFolderName
    ) {

        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Collection not found"
                        )
                );

        Path collectionFolder = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        Path videoFolder = collectionFolder.resolve(videoFolderName)
                .normalize();

        // Ensure the requested folder stays inside the collection folder.
        if (!videoFolder.startsWith(collectionFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid video folder name"
            );
        }
        if (!Files.isDirectory(videoFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Video folder not found"
            );
        }

        VideoMetadata videoMetadata = videoScannerService.getVideoMetadata(collectionFolder, videoFolder);

        return new FileSystemResource(videoMetadata.getVideoPath());
    }

    public VideoMetadata getVideoMetaData(String collectionId, String videoFolder) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Collection not found"
                        )
                );

        Path collectionFolder = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        Path videoFolderPath = collectionFolder.resolve(videoFolder)
                .normalize();
        return videoScannerService.getVideoMetadata(collectionFolder, videoFolderPath);
    }
}