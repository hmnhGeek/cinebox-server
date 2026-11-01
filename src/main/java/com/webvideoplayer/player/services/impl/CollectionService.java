package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.*;
import com.webvideoplayer.player.entities.Collection;
import com.webvideoplayer.player.entities.MediaItem;
import com.webvideoplayer.player.entities.MediaItemPerson;
import com.webvideoplayer.player.entities.MediaItemTag;
import com.webvideoplayer.player.entities.MediaMetadata;
import com.webvideoplayer.player.entities.MediaTechnicalInfo;
import com.webvideoplayer.player.enums.CollectionSyncStatus;
import com.webvideoplayer.player.repositories.CollectionRepository;
import com.webvideoplayer.player.repositories.MediaItemPersonRepository;
import com.webvideoplayer.player.repositories.MediaItemRepository;
import com.webvideoplayer.player.repositories.MediaItemTagRepository;
import com.webvideoplayer.player.repositories.MediaMetadataRepository;
import com.webvideoplayer.player.repositories.MediaTechnicalInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final MediaTechnicalInfoRepository mediaTechnicalInfoRepository;

    public Collection addCollection(
            String title,
            String folder,
            String tagline
    ) {

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
            mediaTechnicalInfoRepository.deleteByMediaItem_Id(itemId);

            mediaMetadataRepository.findByMediaItem_Id(itemId)
                    .ifPresent(mediaMetadataRepository::delete);

            mediaItemRepository.delete(item);
        }

        collectionRepository.delete(collection);
    }

    /*
     * Returns a page of videos from a collection.
     *
     * Pagination is performed by the database. Only the records
     * belonging to the requested page are loaded and mapped.
     */
    @Transactional(readOnly = true)
    public Page<VideoMetadata> getVideos(
            String id,
            int page,
            int size,
            String sortBy,
            String direction
    ) {

        getCollectionById(id);

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page index cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and 100"
            );
        }

        String sortProperty = resolveVideoSortProperty(sortBy);

        Sort.Direction sortDirection;

        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid sort direction. Supported values are asc and desc."
            );
        }

        Sort sort = Sort.by(sortDirection, sortProperty);

        // Add a deterministic secondary sort so records with identical
        // primary sort values have a stable position between pages.
        if (!"id".equals(sortProperty)) {
            sort = sort.and(
                    Sort.by(Sort.Direction.ASC, "id")
            );
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                sort
        );

        Page<MediaItem> items =
                mediaItemRepository.findByCollection_Id(id, pageable);

        return mapToVideoMetadata(items);
    }

    /*
     * Only explicitly supported entity properties can be used for sorting.
     * This prevents clients from passing arbitrary property names.
     */
    private String resolveVideoSortProperty(String sortBy) {

        if (sortBy == null || sortBy.isBlank()) {
            return "title";
        }

        return switch (sortBy.trim()) {
            case "title" -> "title";
            case "fileSizeBytes" -> "fileSizeBytes";
            case "durationSeconds" -> "durationSeconds";
            case "lastModifiedAt" -> "lastModifiedAt";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported sort field. Supported fields are: "
                            + "title, fileSizeBytes, durationSeconds, lastModifiedAt."
            );
        };
    }

    /*
     * Maps a paginated result while preserving its pagination metadata.
     */
    @Transactional(readOnly = true)
    public Page<VideoMetadata> mapToVideoMetadata(
            Page<MediaItem> videos
    ) {

        List<VideoMetadata> content =
                mapToVideoMetadata(videos.getContent());

        return new PageImpl<>(
                content,
                videos.getPageable(),
                videos.getTotalElements()
        );
    }

    /*
     * Maps a list of media items to VideoMetadata.
     *
     * All associated information is loaded in batches for the current
     * page, avoiding per-video repository lookups.
     */
    @Transactional(readOnly = true)
    public List<VideoMetadata> mapToVideoMetadata(
            List<MediaItem> items
    ) {

        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<String> mediaItemIds = items.stream()
                .map(MediaItem::getId)
                .toList();

        Map<String, MediaTechnicalInfo> technicalInfoByMediaItemId =
                mediaTechnicalInfoRepository
                        .findAllByMediaItem_IdIn(mediaItemIds)
                        .stream()
                        .collect(Collectors.toMap(
                                info -> info.getMediaItem().getId(),
                                Function.identity(),
                                (first, second) -> first
                        ));

        Map<String, MediaMetadata> metadataByMediaItemId =
                mediaMetadataRepository
                        .findAllByMediaItem_IdIn(mediaItemIds)
                        .stream()
                        .collect(Collectors.toMap(
                                metadata -> metadata.getMediaItem().getId(),
                                Function.identity(),
                                (first, second) -> first
                        ));

        Map<String, List<MediaItemPerson>> peopleByMediaItemId =
                mediaItemPersonRepository
                        .findByMediaItem_IdIn(mediaItemIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                person -> person.getMediaItem().getId()
                        ));

        Map<String, List<MediaItemTag>> tagsByMediaItemId =
                mediaItemTagRepository
                        .findByMediaItem_IdIn(mediaItemIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                tag -> tag.getMediaItem().getId()
                        ));

        return items.stream()
                .map(item -> {

                    Path collectionPath = Paths.get(
                            item.getCollection().getFolder()
                    ).toAbsolutePath().normalize();

                    return mapToVideoMetadata(
                            item,
                            collectionPath,
                            technicalInfoByMediaItemId.get(item.getId()),
                            metadataByMediaItemId.get(item.getId()),
                            peopleByMediaItemId.getOrDefault(
                                    item.getId(),
                                    List.of()
                            ),
                            tagsByMediaItemId.getOrDefault(
                                    item.getId(),
                                    List.of()
                            )
                    );
                })
                .toList();
    }

    /*
     * Maps one media item to VideoMetadata.
     *
     * This overload is retained for individual-video metadata requests.
     */
    @Transactional(readOnly = true)
    public VideoMetadata mapToVideoMetadata(
            MediaItem item,
            Path collectionPath
    ) {

        MediaTechnicalInfo technicalInfo =
                mediaTechnicalInfoRepository
                        .findByMediaItem_Id(item.getId())
                        .orElse(null);

        MediaMetadata metadata =
                mediaMetadataRepository
                        .findByMediaItem_Id(item.getId())
                        .orElse(null);

        List<MediaItemPerson> people =
                mediaItemPersonRepository.findByMediaItem_Id(item.getId());

        List<MediaItemTag> itemTags =
                mediaItemTagRepository.findByMediaItem_Id(item.getId());

        return mapToVideoMetadata(
                item,
                collectionPath,
                technicalInfo,
                metadata,
                people,
                itemTags
        );
    }

    /*
     * Shared mapping implementation.
     *
     * All associations are passed in by the caller. This method does
     * not perform repository queries.
     */
    private VideoMetadata mapToVideoMetadata(
            MediaItem item,
            Path collectionPath,
            MediaTechnicalInfo technicalInfo,
            MediaMetadata metadata,
            List<MediaItemPerson> people,
            List<MediaItemTag> itemTags
    ) {

        // Directors
        List<PersonDTO> directors = people.stream()
                .filter(p -> "DIRECTOR".equalsIgnoreCase(p.getRole()))
                .map(p -> PersonDTO.builder()
                        .id(p.getPerson().getId())
                        .name(p.getPerson().getName())
                        .build())
                .distinct()
                .toList();

        // Writers
        List<PersonDTO> writers = people.stream()
                .filter(p -> "WRITER".equalsIgnoreCase(p.getRole()))
                .map(p -> PersonDTO.builder()
                        .id(p.getPerson().getId())
                        .name(p.getPerson().getName())
                        .build())
                .distinct()
                .toList();

        // Actors
        List<ActorDTO> actors = people.stream()
                .filter(p -> "ACTOR".equalsIgnoreCase(p.getRole()))
                .map(p -> ActorDTO.builder()
                        .id(p.getPerson().getId())
                        .name(p.getPerson().getName())
                        .role(p.getCharacterName())
                        .build())
                .distinct()
                .toList();

        // Tags
        List<TagDTO> tags = itemTags.stream()
                .map(t -> TagDTO.builder()
                        .id(t.getTag().getId())
                        .name(t.getTag().getName())
                        .build())
                .distinct()
                .sorted(Comparator.comparing(
                        TagDTO::getName,
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();

        // Genres
        List<GenreDTO> genres = metadata == null
                ? List.of()
                : metadata.getGenres().stream()
                .map(genre -> GenreDTO.builder()
                        .id(genre.getId())
                        .name(genre.getName())
                        .build())
                .distinct()
                .sorted(Comparator.comparing(
                        GenreDTO::getName,
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();

        // Studios
        List<StudioDTO> studios = metadata == null
                ? List.of()
                : metadata.getStudios().stream()
                .map(studio -> StudioDTO.builder()
                        .id(studio.getId())
                        .name(studio.getName())
                        .build())
                .distinct()
                .sorted(Comparator.comparing(
                        StudioDTO::getName,
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();

        String thumbnailUrl =
                "/api/v1/collections/thumbnail?mediaItemId="
                        + URLEncoder.encode(
                        item.getId(),
                        StandardCharsets.UTF_8
                );

        return VideoMetadata.builder()
                .id(item.getId())
                .collectionId(item.getCollection().getId())
                .title(item.getTitle())
                .originalTitle(metadata == null
                        ? null : metadata.getOriginalTitle())
                .sortTitle(metadata == null
                        ? null : metadata.getSortTitle())
                .plot(metadata == null
                        ? null : metadata.getPlot())
                .outline(metadata == null
                        ? null : metadata.getOutline())
                .year(metadata == null
                        ? null : metadata.getYear())
                .premiered(metadata == null
                        ? null : metadata.getPremiered())
                .releaseDate(metadata == null
                        ? null : metadata.getReleaseDate())
                .runtime(metadata != null
                        && metadata.getRuntimeMinutes() != null
                        ? metadata.getRuntimeMinutes()
                        : item.getDurationSeconds() == null
                        ? null
                        : (int) Math.round(
                        item.getDurationSeconds() / 60.0
                ))

                // Technical media information
                .resolution(technicalInfo == null
                        ? null : technicalInfo.getResolutionLabel())
                .width(technicalInfo == null
                        ? null : technicalInfo.getWidth())
                .height(technicalInfo == null
                        ? null : technicalInfo.getHeight())
                .durationSeconds(technicalInfo == null
                        ? null : technicalInfo.getDurationSeconds())
                .fileSizeBytes(item.getFileSizeBytes())

                .studio(studios.isEmpty() ? null : studios.getFirst())
                .studios(studios)
                .directors(directors)
                .writers(writers)
                .actors(actors)
                .genres(genres)
                .tags(tags)
                .country(metadata == null
                        ? null : metadata.getCountry())
                .originalCountry(metadata == null
                        ? null : metadata.getOriginalCountry())
                .language(metadata == null
                        ? null : metadata.getLanguage())
                .mpaa(metadata == null
                        ? null : metadata.getMpaa())
                .website(metadata == null
                        ? null : metadata.getWebsite())
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
     * Get all collections whose folders are currently available.
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
     * Get collection by ID.
     */
    public Collection getCollectionById(String id) {

        return collectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Collection not found: " + id
                ));
    }

    public void synchroniseCollection(String collectionId) {

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
     * Get preview generation progress.
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
     * Get preview by media item ID.
     */
    @Transactional(readOnly = true)
    public Resource getPreview(String mediaItemId) throws IOException {

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
     * Get poster by media item ID.
     */
    @Transactional(readOnly = true)
    public Resource getPoster(String mediaItemId) throws IOException {

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
     * Get video by media item ID.
     */
    @Transactional(readOnly = true)
    public Resource getVideo(String mediaItemId) {

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

        if (!Files.isRegularFile(videoPath)
                || !Files.isReadable(videoPath)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Video file not found or not readable"
            );
        }

        return new FileSystemResource(videoPath);
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
            String videoFolderName
    ) {

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

    public CollectionSyncStatus getPreviewGenerationStatus(
            String collectionId
    ) {
        return previewGenerationService.getSyncStatus(collectionId);
    }

    public CatalogSyncProgress getCatalogSyncProgress(
            String collectionId
    ) {
        return mediaCatalogSyncAsyncService.getProgress(collectionId);
    }
}