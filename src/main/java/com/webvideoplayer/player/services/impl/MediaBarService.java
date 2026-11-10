
package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.MediaBarItemDTO;
import com.webvideoplayer.player.entities.Collection;
import com.webvideoplayer.player.entities.MediaItem;
import com.webvideoplayer.player.entities.MediaMetadata;
import com.webvideoplayer.player.entities.PlaybackProgress;
import com.webvideoplayer.player.repositories.CollectionRepository;
import com.webvideoplayer.player.repositories.MediaItemRepository;
import com.webvideoplayer.player.repositories.MediaMetadataRepository;
import com.webvideoplayer.player.repositories.PlaybackProgressRepository;
import com.webvideoplayer.player.utils.MediaFilePathResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaBarService {

    private static final int MAX_FEATURED_ITEMS = 5;

    /*
     * Continue Watching currently uses a 30-second minimum position.
     * Keep this threshold aligned with PlaybackProgressService.
     */
    private static final long MINIMUM_PLAYBACK_SECONDS = 30L;

    private final MediaItemRepository mediaItemRepository;
    private final CollectionRepository collectionRepository;
    private final MediaMetadataRepository mediaMetadataRepository;
    private final PlaybackProgressRepository playbackProgressRepository;
    private final MediaFilePathResolver mediaFilePathResolver;

    public List<MediaBarItemDTO> getFeaturedItems() {

        Map<String, Collection> availableCollections =
                getAvailableCollections();

        if (availableCollections.isEmpty()) {
            return List.of();
        }

        Set<String> continueWatchingIds =
                getContinueWatchingMediaIds();

        Map<String, MediaMetadata> metadataByMediaItemId =
                getMetadataByMediaItemId();

        List<MediaItem> mediaItems = mediaItemRepository.findAll();

        List<FeaturedCandidate> candidates = new ArrayList<>();

        for (MediaItem mediaItem : mediaItems) {

            if (mediaItem == null || mediaItem.getId() == null) {
                continue;
            }

            if (continueWatchingIds.contains(mediaItem.getId())) {
                continue;
            }

            Collection collection = mediaItem.getCollection();

            if (collection == null
                    || collection.getId() == null
                    || !availableCollections.containsKey(collection.getId())) {
                continue;
            }

            try {
                Path thumbnailPath = resolveBackdropPath(mediaItem);

                MediaMetadata metadata =
                        metadataByMediaItemId.get(mediaItem.getId());

                candidates.add(
                        new FeaturedCandidate(
                                mediaItem,
                                metadata,
                                thumbnailPath
                        )
                );

            } catch (IOException ignored) {
                /*
                 * A missing or inaccessible thumbnail should not prevent
                 * other movies from appearing in the Media Bar.
                 *
                 * The same applies to unavailable or invalid media paths.
                 */
            }
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        /*
         * Stable daily ordering:
         *
         * - The same candidate set produces the same ordering throughout
         *   the UTC day.
         * - The ordering changes on the next UTC day.
         * - No random reshuffle occurs on each HTTP request.
         * - No additional persistence table or scheduled job is required.
         */
        LocalDate rotationDate = LocalDate.now(ZoneOffset.UTC);

        candidates.sort(
                Comparator.comparing(
                        candidate -> getDailySortKey(
                                rotationDate,
                                candidate.mediaItem().getId()
                        )
                )
        );

        return candidates.stream()
                .limit(MAX_FEATURED_ITEMS)
                .map(this::toDTO)
                .toList();
    }

    /**
     * Resolves a movie's thumbnail image.
     *
     * Supported filename pattern:
     * thumbnail.<image-extension>
     *
     * The extension is not restricted to a predefined list.
     * The operating system determines the image MIME type.
     *
     * The thumbnail must be located in the same directory as the movie file.
     */
    public Path getBackdropPath(String mediaItemId) {

        if (mediaItemId == null || mediaItemId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Media item ID cannot be empty."
            );
        }

        MediaItem mediaItem = mediaItemRepository.findById(mediaItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Media item not found."
                ));

        try {
            return resolveBackdropPath(mediaItem);

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Thumbnail image not found."
            );
        }
    }

    private Map<String, Collection> getAvailableCollections() {

        Map<String, Collection> availableCollections = new HashMap<>();

        for (Collection collection : collectionRepository.findAll()) {

            if (collection == null
                    || collection.getId() == null
                    || collection.getFolder() == null
                    || collection.getFolder().isBlank()) {
                continue;
            }

            try {
                Path folder = Paths.get(collection.getFolder())
                        .toAbsolutePath()
                        .normalize();

                if (Files.isDirectory(folder)
                        && Files.isReadable(folder)) {
                    availableCollections.put(
                            collection.getId(),
                            collection
                    );
                }

            } catch (RuntimeException ignored) {
                /*
                 * Invalid paths and unavailable mounted folders are
                 * excluded without affecting the remaining collections.
                 */
            }
        }

        return availableCollections;
    }

    private Set<String> getContinueWatchingMediaIds() {

        Set<String> mediaItemIds = new HashSet<>();

        /*
         * Playback progress is read in one repository call instead of
         * querying separately for every movie.
         */
        for (PlaybackProgress progress :
                playbackProgressRepository.findAll()) {

            if (progress == null
                    || progress.isCompleted()
                    || progress.getPositionSeconds() == null
                    || progress.getPositionSeconds() < MINIMUM_PLAYBACK_SECONDS
                    || progress.getMediaItem() == null
                    || progress.getMediaItem().getId() == null) {
                continue;
            }

            mediaItemIds.add(progress.getMediaItem().getId());
        }

        return mediaItemIds;
    }

    private Map<String, MediaMetadata> getMetadataByMediaItemId() {

        Map<String, MediaMetadata> metadataByMediaItemId =
                new HashMap<>();

        for (MediaMetadata metadata : mediaMetadataRepository.findAll()) {

            if (metadata == null
                    || metadata.getMediaItem() == null
                    || metadata.getMediaItem().getId() == null) {
                continue;
            }

            metadataByMediaItemId.put(
                    metadata.getMediaItem().getId(),
                    metadata
            );
        }

        return metadataByMediaItemId;
    }

    private Path resolveBackdropPath(MediaItem mediaItem)
            throws IOException {

        /*
         * Resolve the video first. This validates that the indexed movie
         * still exists and that its path remains inside its collection.
         */
        Path videoPath = mediaFilePathResolver.resolve(mediaItem);

        Path movieFolder = videoPath.getParent();

        if (movieFolder == null
                || !Files.isDirectory(movieFolder)
                || !Files.isReadable(movieFolder)) {
            throw new IOException("Movie folder is unavailable.");
        }

        Path realMovieFolder = movieFolder.toRealPath();

        Path collectionFolder = Paths.get(
                mediaItem.getCollection().getFolder()
        ).toAbsolutePath().normalize().toRealPath();

        if (!realMovieFolder.startsWith(collectionFolder)) {
            throw new IOException(
                    "Movie folder resolves outside the collection."
            );
        }

        /*
         * Discover thumbnail images dynamically.
         *
         * Examples:
         * thumbnail.jpg
         * thumbnail.jpeg
         * thumbnail.png
         * thumbnail.webp
         * thumbnail.avif
         * thumbnail.heic
         *
         * No fixed extension list is maintained.
         */
        List<Path> thumbnailCandidates;

        try (var paths = Files.list(realMovieFolder)) {

            thumbnailCandidates = paths
                    .filter(Files::isRegularFile)
                    .filter(Files::isReadable)
                    .filter(path -> {
                        String filename = path.getFileName()
                                .toString()
                                .toLowerCase(Locale.ROOT);

                        int extensionIndex = filename.lastIndexOf('.');

                        return extensionIndex > 0
                                && filename.substring(0, extensionIndex)
                                .equals("thumbnail");
                    })
                    .sorted(Comparator.comparing(
                            path -> path.getFileName()
                                    .toString()
                                    .toLowerCase(Locale.ROOT)
                    ))
                    .toList();
        }

        for (Path candidate : thumbnailCandidates) {

            /*
             * Verify that the operating system recognizes this file
             * as an image.
             */
            String contentType = Files.probeContentType(candidate);

            if (contentType == null
                    || !contentType.toLowerCase(Locale.ROOT)
                    .startsWith("image/")) {
                continue;
            }

            Path realCandidate = candidate.toRealPath();

            /*
             * Do not allow a thumbnail symlink to point outside the
             * movie folder or collection directory.
             */
            if (!realCandidate.startsWith(realMovieFolder)
                    || !realCandidate.startsWith(collectionFolder)) {
                continue;
            }

            return realCandidate;
        }

        throw new IOException("No supported thumbnail image was found.");
    }

    private String getDailySortKey(
            LocalDate date,
            String mediaItemId
    ) {

        String seed = date + ":" + mediaItemId;

        return UUID.nameUUIDFromBytes(
                seed.getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

    private MediaBarItemDTO toDTO(FeaturedCandidate candidate) {

        MediaItem mediaItem = candidate.mediaItem();
        MediaMetadata metadata = candidate.metadata();

        String plot = metadata == null
                ? null
                : firstNonBlank(
                metadata.getPlot(),
                metadata.getOutline()
        );

        Integer year = metadata == null
                ? null
                : metadata.getYear();

        Integer runtime = metadata == null
                ? null
                : metadata.getRuntimeMinutes();

        return new MediaBarItemDTO(
                mediaItem.getId(),
                mediaItem.getCollection().getId(),
                mediaItem.getTitle(),
                plot,
                year,
                runtime,
                mediaItem.getDurationSeconds(),
                "/api/v1/collections/backdrop?mediaItemId="
                        + mediaItem.getId()
        );
    }

    private String firstNonBlank(String first, String second) {

        if (first != null && !first.isBlank()) {
            return first;
        }

        if (second != null && !second.isBlank()) {
            return second;
        }

        return null;
    }

    private record FeaturedCandidate(
            MediaItem mediaItem,
            MediaMetadata metadata,
            Path backdropPath
    ) {
    }
}
