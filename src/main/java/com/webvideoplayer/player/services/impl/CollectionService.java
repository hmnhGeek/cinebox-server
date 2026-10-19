
package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.VideoMetadata;
import com.webvideoplayer.player.entities.Collection;
import com.webvideoplayer.player.repositories.CollectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.webvideoplayer.player.enums.CollectionSyncStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final VideoScannerService videoScannerService;
    private final PreviewGenerationService previewGenerationService;

    /*
     * 1. Add collection
     */
    public Collection addCollection(
            String title,
            String folder,
            String tagline) {

        Path collectionPath = Paths.get(folder)
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
                collectionPath.toString())) {
            throw new IllegalArgumentException(
                    "Collection already exists: " + collectionPath
            );
        }

        Collection collection = new Collection(
                null,
                title,
                folder,
                tagline
        );

        Collection saved = collectionRepository.save(collection);

        previewGenerationService.markQueued(saved.getId());

        // Submit preview generation to the background executor.
        // This call goes through another Spring-managed bean.
        previewGenerationService.generatePreviewsAsync(
                collectionPath,
                saved.getId()
        );

        return saved;
    }

    /*
     * 2. Delete collection
     *
     * Only the MongoDB record is deleted.
     * Actual video files remain untouched.
     */
    public void deleteCollection(String id) {

        Collection collection = collectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Collection not found: " + id
                ));

        collectionRepository.delete(collection);
    }

    /*
     * 3. Get all videos in a collection
     */
    public List<VideoMetadata> getVideos(String id) {

        Collection collection = collectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Collection not found: " + id
                ));

        return videoScannerService.getVideos(
                Paths.get(collection.getFolder())
        );
    }

    /*
     * 4. Get all collections
     */
    public List<Collection> getAllCollections() {

        return collectionRepository.findAll()
                .stream()
                .filter(collection -> {
                    try {
                        Path folderPath =
                                Paths.get(collection.getFolder());

                        return Files.isDirectory(folderPath)
                                && Files.isReadable(folderPath);

                    } catch (Exception e) {
                        return false;
                    }
                })
                .toList();
    }

    /*
     * 5. Get collection by ID
     */
    public Collection getCollectionById(String id) {

        return collectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Collection not found: " + id
                ));
    }

    /*
     * 6. Synchronise collection
     *
     * Preview generation runs asynchronously.
     */
    public void synchroniseCollection(String collectionId) {

        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Collection not found"
                ));

        Path collectionPath = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        previewGenerationService.markQueued(collectionId);

        previewGenerationService.generatePreviewsAsync(
                collectionPath,
                collectionId
        );
    }

    /*
     * 7. Get preview generation progress
     */
    public Map<String, Integer> getPreviewProgress() {
        return previewGenerationService.getPreviewProgress();
    }

    /*
     * 8. Get thumbnail
     */
    public Resource getThumbnail(
            String collectionId,
            String videoFolderName) throws IOException {

        Collection collection = getCollectionById(collectionId);

        Path collectionFolder = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        Path videoFolder = resolveVideoFolder(
                collectionFolder,
                videoFolderName
        );

        Path thumbnailPath;

        try (Stream<Path> files = Files.list(videoFolder)) {
            thumbnailPath = files
                    .filter(Files::isRegularFile)
                    .filter(path -> getBaseName(path)
                            .equalsIgnoreCase("thumbnail"))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Thumbnail not found"
                    ));
        }

        return new FileSystemResource(thumbnailPath);
    }

    /*
     * 9. Get preview
     */
    public Resource getPreview(
            String collectionId,
            String videoFolderName) throws IOException {

        Collection collection = getCollectionById(collectionId);

        Path collectionFolder = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        Path videoFolder = resolveVideoFolder(
                collectionFolder,
                videoFolderName
        );

        Path previewPath;

        try (Stream<Path> files = Files.list(videoFolder)) {
            previewPath = files
                    .filter(Files::isRegularFile)
                    .filter(path -> getBaseName(path)
                            .equalsIgnoreCase("preview"))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Preview not found"
                    ));
        }

        return new FileSystemResource(previewPath);
    }

    /*
     * 10. Get poster
     */
    public Resource getPoster(
            String collectionId,
            String videoFolderName) throws IOException {

        Collection collection = getCollectionById(collectionId);

        Path collectionFolder = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        Path videoFolder = resolveVideoFolder(
                collectionFolder,
                videoFolderName
        );

        Path posterPath;

        try (Stream<Path> files = Files.list(videoFolder)) {
            posterPath = files
                    .filter(Files::isRegularFile)
                    .filter(path -> getBaseName(path)
                            .equalsIgnoreCase("poster"))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Poster not found"
                    ));
        }

        return new FileSystemResource(posterPath);
    }

    /*
     * 11. Get video file
     */
    public Resource getVideo(
            String collectionId,
            String videoFolderName) {

        Collection collection = getCollectionById(collectionId);

        Path collectionFolder = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        Path videoFolder = resolveVideoFolder(
                collectionFolder,
                videoFolderName
        );

        VideoMetadata videoMetadata =
                videoScannerService.getVideoMetadata(
                        collectionFolder,
                        videoFolder
                );

        return new FileSystemResource(videoMetadata.getVideoPath());
    }

    /*
     * 12. Get video metadata
     */
    public VideoMetadata getVideoMetaData(
            String collectionId,
            String videoFolderName) {

        Collection collection = getCollectionById(collectionId);

        Path collectionFolder = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        Path videoFolder = resolveVideoFolder(
                collectionFolder,
                videoFolderName
        );

        return videoScannerService.getVideoMetadata(
                collectionFolder,
                videoFolder
        );
    }

    public Resource getHeroImage(String collectionId) throws IOException {

        Collection collection = getCollectionById(collectionId);

        Path collectionFolder = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        if (!Files.isDirectory(collectionFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Collection folder not found"
            );
        }

        Path heroImagesFolder = collectionFolder.resolve("hero_images");

        if (!Files.isDirectory(heroImagesFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Hero images folder not found"
            );
        }

        List<Path> images;

        try (Stream<Path> files = Files.list(heroImagesFolder)) {
            images = new ArrayList<>(
                    files.filter(Files::isRegularFile).toList()
            );
        }

        if (images.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No hero images found"
            );
        }

        Collections.shuffle(images);

        return new FileSystemResource(images.getFirst());
    }

    /*
     * Helper: resolve a video folder safely.
     *
     * Prevents paths outside the collection directory.
     */
    private Path resolveVideoFolder(
            Path collectionFolder,
            String videoFolderName) {

        Path videoFolder = collectionFolder
                .resolve(videoFolderName)
                .normalize();

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

        return videoFolder;
    }

    /*
     * Helper: get filename without extension.
     */
    private String getBaseName(Path path) {

        String filename = path.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');

        return dotIndex > 0
                ? filename.substring(0, dotIndex)
                : filename;
    }

    public CollectionSyncStatus getSyncStatus(String collectionId) {
        return previewGenerationService.getSyncStatus(collectionId);
    }
}
