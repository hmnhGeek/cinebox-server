
package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.CatalogSyncProgress;
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

import com.webvideoplayer.player.entities.MediaItem;
import com.webvideoplayer.player.entities.MediaMetadata;
import com.webvideoplayer.player.entities.MediaItemPerson;
import com.webvideoplayer.player.entities.MediaItemTag;
import com.webvideoplayer.player.repositories.MediaItemRepository;
import com.webvideoplayer.player.repositories.MediaMetadataRepository;
import com.webvideoplayer.player.repositories.MediaItemPersonRepository;
import com.webvideoplayer.player.repositories.MediaItemTagRepository;
import com.webvideoplayer.player.dto.Actor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Comparator;

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
    private final MediaCatalogAsyncService mediaCatalogAsyncService;
    private final MediaCatalogAsyncService mediaCatalogSyncAsyncService;
    private final MediaItemRepository mediaItemRepository;
    private final MediaMetadataRepository mediaMetadataRepository;
    private final MediaItemPersonRepository mediaItemPersonRepository;
    private final MediaItemTagRepository mediaItemTagRepository;


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

        if (!Files.isReadable(collectionPath)) {
            throw new IllegalArgumentException(
                    "Folder is not readable: " + collectionPath
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
                collectionPath.toString(),
                tagline
        );

        Collection saved = collectionRepository.save(collection);

        // Start catalog synchronization in the background.
        mediaCatalogSyncAsyncService.synchronizeCollectionAsync(
                saved.getId()
        );

        // Preview generation is intentionally not triggered here.

        return saved;
    }

    @Transactional
    public void deleteCollection(String id) {

        Collection collection = collectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Collection not found: " + id
                ));

        List<MediaItem> items =
                mediaItemRepository.findByCollection_IdOrderByTitleAsc(id);

        for (MediaItem item : items) {

            String itemId = item.getId();

            mediaItemPersonRepository.deleteByMediaItem_Id(itemId);
            mediaItemTagRepository.deleteByMediaItem_Id(itemId);

            mediaMetadataRepository.findByMediaItem_Id(itemId)
                    .ifPresent(mediaMetadataRepository::delete);

            mediaItemRepository.delete(item);
        }

        collectionRepository.delete(collection);
    }

    @Transactional(readOnly = true)
    public List<VideoMetadata> getVideos(String id) {

        Collection collection = getCollectionById(id);

        Path collectionPath = Paths.get(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        return mediaItemRepository
                .findByCollection_IdOrderByTitleAsc(id)
                .stream()
                .map(item -> mapToVideoMetadata(item, collectionPath))
                .toList();
    }

    private VideoMetadata mapToVideoMetadata(
            MediaItem item,
            Path collectionPath) {

        MediaMetadata metadata = mediaMetadataRepository
                .findByMediaItem_Id(item.getId())
                .orElse(null);

        Path videoPath = collectionPath
                .resolve(item.getRelativePath())
                .normalize();

        Path videoFolder = videoPath.getParent();

        List<MediaItemPerson> people =
                mediaItemPersonRepository.findByMediaItem_Id(item.getId());

        List<MediaItemTag> itemTags =
                mediaItemTagRepository.findByMediaItem_Id(item.getId());

        List<String> directors = people.stream()
                .filter(p -> "DIRECTOR".equalsIgnoreCase(p.getRole()))
                .map(p -> p.getPerson().getName())
                .distinct()
                .toList();

        List<String> writers = people.stream()
                .filter(p -> "WRITER".equalsIgnoreCase(p.getRole()))
                .map(p -> p.getPerson().getName())
                .distinct()
                .toList();

        List<Actor> actors = people.stream()
                .filter(p -> "ACTOR".equalsIgnoreCase(p.getRole()))
                .map(p -> Actor.builder()
                        .name(p.getPerson().getName())
                        .role(p.getCharacterName())
                        .build())
                .toList();

        List<String> tags = itemTags.stream()
                .map(t -> t.getTag().getName())
                .distinct()
                .toList();

        List<String> genres = metadata == null
                ? List.of()
                : metadata.getGenres().stream()
                .map(genre -> genre.getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        List<String> studios = metadata == null
                ? List.of()
                : metadata.getStudios().stream()
                .map(studio -> studio.getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        String thumbnailUrl =
                "/api/v1/collections/thumbnail?mediaItemId="
                        + URLEncoder.encode(
                        item.getId(),
                        StandardCharsets.UTF_8
                );

        return VideoMetadata.builder()
                .id(item.getId())
                .title(item.getTitle())
                .originalTitle(metadata == null
                        ? null : metadata.getOriginalTitle())
                .sortTitle(metadata == null
                        ? null : metadata.getSortTitle())
                .plot(metadata == null ? null : metadata.getPlot())
                .outline(metadata == null ? null : metadata.getOutline())
                .year(metadata == null ? null : metadata.getYear())
                .premiered(metadata == null ? null : metadata.getPremiered())
                .releaseDate(metadata == null
                        ? null : metadata.getReleaseDate())
                .runtime(metadata != null && metadata.getRuntimeMinutes() != null
                        ? metadata.getRuntimeMinutes()
                        : item.getDurationSeconds() == null
                        ? null
                        : (int) Math.round(
                        item.getDurationSeconds() / 60.0))
                .studio(studios.isEmpty() ? null : studios.getFirst())
                .studios(studios)
                .directors(directors)
                .writers(writers)
                .actors(actors)
                .genres(genres)
                .tags(tags)
                .country(metadata == null ? null : metadata.getCountry())
                .originalCountry(metadata == null
                        ? null : metadata.getOriginalCountry())
                .language(metadata == null ? null : metadata.getLanguage())
                .mpaa(metadata == null ? null : metadata.getMpaa())
                .website(metadata == null ? null : metadata.getWebsite())
                .videoPath(videoPath.toString())
                .posterPath(videoFolder == null
                        ? null : findAsset(videoFolder, "poster"))
                .thumbnailPath(videoFolder == null
                        ? null : findAsset(videoFolder, "thumbnail"))
                .previewPath(videoFolder == null
                        ? null : findAsset(videoFolder, "preview"))
                .thumbnailUrl(thumbnailUrl)
                .build();
    }

    private String findAsset(Path folder, String baseName) {

        if (folder == null || !Files.isDirectory(folder)) {
            return null;
        }

        try (Stream<Path> files = Files.list(folder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> getBaseName(path)
                            .equalsIgnoreCase(baseName))
                    .findFirst()
                    .map(Path::toString)
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Could not locate {} in {}", baseName, folder, e);
            return null;
        }
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

    public void synchroniseCollection(String collectionId) {
        getCollectionById(collectionId);
        mediaCatalogSyncAsyncService.synchronizeCollectionAsync(
                collectionId
        );
    }


    public void generatePreviews(String collectionId) {

        Collection collection = getCollectionById(collectionId);

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
     * Get thumbnail by media item ID.
     */
    @Transactional(readOnly = true)
    public Resource getThumbnail(String mediaItemId) throws IOException {

        MediaItem item = mediaItemRepository.findById(mediaItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Media item not found"
                ));

        Path collectionFolder = Paths.get(
                item.getCollection().getFolder()
        ).toAbsolutePath().normalize();

        Path videoPath = collectionFolder
                .resolve(item.getRelativePath())
                .normalize();

        // Prevent paths outside the collection directory.
        if (!videoPath.startsWith(collectionFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid media item path"
            );
        }

        Path videoFolder = videoPath.getParent();

        if (videoFolder == null || !Files.isDirectory(videoFolder)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Video folder not found"
            );
        }

        Path thumbnailPath;

        try (Stream<Path> files = Files.list(videoFolder)) {
            thumbnailPath = files
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            getBaseName(path).equalsIgnoreCase("thumbnail")
                    )
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

    @Transactional(readOnly = true)
    public VideoMetadata getVideoMetadata(String mediaItemId) {
        MediaItem item = mediaItemRepository.findById(mediaItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Media item not found"
                ));

        Path collectionPath = Paths.get(item.getCollection().getFolder())
                .toAbsolutePath()
                .normalize();

        return mapToVideoMetadata(item, collectionPath);
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

    public CollectionSyncStatus getPreviewGenerationStatus(String collectionId) {
        return previewGenerationService.getSyncStatus(collectionId);
    }

    public CatalogSyncProgress getCatalogSyncProgress(String collectionId) {
        return mediaCatalogSyncAsyncService.getProgress(collectionId);
    }
}
