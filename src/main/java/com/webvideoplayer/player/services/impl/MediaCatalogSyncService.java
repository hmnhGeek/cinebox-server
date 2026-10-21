package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.Actor;
import com.webvideoplayer.player.dto.VideoMetadata;
import com.webvideoplayer.player.entities.Collection;
import com.webvideoplayer.player.entities.Genre;
import com.webvideoplayer.player.entities.MediaItem;
import com.webvideoplayer.player.entities.MediaItemPerson;
import com.webvideoplayer.player.entities.MediaItemTag;
import com.webvideoplayer.player.entities.MediaMetadata;
import com.webvideoplayer.player.entities.Person;
import com.webvideoplayer.player.entities.Studio;
import com.webvideoplayer.player.entities.Tag;
import com.webvideoplayer.player.repositories.CollectionRepository;
import com.webvideoplayer.player.repositories.GenreRepository;
import com.webvideoplayer.player.repositories.MediaItemPersonRepository;
import com.webvideoplayer.player.repositories.MediaItemRepository;
import com.webvideoplayer.player.repositories.MediaItemTagRepository;
import com.webvideoplayer.player.repositories.MediaMetadataRepository;
import com.webvideoplayer.player.repositories.PersonRepository;
import com.webvideoplayer.player.repositories.StudioRepository;
import com.webvideoplayer.player.repositories.TagRepository;

import com.webvideoplayer.player.services.impl.VideoScannerService;
import com.webvideoplayer.player.utils.VideoDurationUtils;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MediaCatalogSyncService {

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            ".mp4", ".mkv", ".avi", ".mov",
            ".webm", ".m4v", ".ts"
    );

    private final CollectionRepository collectionRepository;
    private final MediaItemRepository mediaItemRepository;
    private final MediaMetadataRepository mediaMetadataRepository;
    private final MediaItemPersonRepository mediaItemPersonRepository;
    private final MediaItemTagRepository mediaItemTagRepository;

    private final PersonRepository personRepository;
    private final GenreRepository genreRepository;
    private final StudioRepository studioRepository;
    private final TagRepository tagRepository;

    private final VideoScannerService videoScannerService;

    /**
     * Synchronizes a collection's filesystem contents with the database.
     *
     * Existing records are preserved when the directory is unavailable,
     * a scan fails, or no video files are discovered.
     */
    @Transactional(rollbackFor = IOException.class)
    public void synchronizeCollection(String collectionId) throws IOException {

        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Collection not found: " + collectionId
                        )
                );

        Path collectionPath = Path.of(collection.getFolder())
                .toAbsolutePath()
                .normalize();

        if (!Files.isDirectory(collectionPath)
                || !Files.isReadable(collectionPath)) {
            throw new IOException(
                    "Collection folder is unavailable: " + collectionPath
            );
        }

        List<MediaItem> existingItems =
                mediaItemRepository.findByCollection_IdOrderByTitleAsc(
                        collectionId
                );

        Map<String, MediaItem> existingByPath = new HashMap<>();

        for (MediaItem item : existingItems) {
            existingByPath.put(
                    normalizeRelativePath(item.getRelativePath()),
                    item
            );
        }

        Set<String> discoveredPaths = new HashSet<>();
        boolean scanComplete = true;
        int discoveredVideoCount = 0;

        List<Path> movieFolders;

        try (Stream<Path> stream = Files.list(collectionPath)) {
            movieFolders = stream
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
        }

        for (Path movieFolder : movieFolders) {
            Path videoFile;

            try {
                videoFile = findVideoFile(movieFolder);

                // Ignore folders without a supported video file.
                if (videoFile == null) {
                    continue;
                }

                String relativePath = normalizeRelativePath(
                        collectionPath.relativize(videoFile).toString()
                );

                discoveredPaths.add(relativePath);
                discoveredVideoCount++;

                MediaItem item = existingByPath.get(relativePath);

                if (item == null) {
                    item = new MediaItem();
                    item.setCollection(collection);
                    item.setRelativePath(relativePath);
                }

                FileTime fileTime =
                        Files.getLastModifiedTime(videoFile);

                Instant modifiedAt = fileTime.toInstant();
                long fileSize = Files.size(videoFile);

                boolean videoChanged =
                        item.getId() == null
                                || item.getLastModifiedAt() == null
                                || item.getFileSizeBytes() == null
                                || !item.getLastModifiedAt().equals(modifiedAt)
                                || item.getFileSizeBytes() != fileSize;

                VideoMetadata metadata =
                        videoScannerService.getVideoMetadata(
                                collectionPath,
                                movieFolder
                        );

                String title = resolveTitle(metadata, movieFolder);

                item.setTitle(title);
                item.setFileSizeBytes(fileSize);
                item.setLastModifiedAt(modifiedAt);
                item.setLastScannedAt(Instant.now());

                if (videoChanged) {
                    try {
                        double duration = VideoDurationUtils.getDuration(videoFile);

                        if (Double.isFinite(duration) && duration > 0) {
                            item.setDurationSeconds(Math.round(duration));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();

                        throw new IOException(
                                "Video duration detection was interrupted: " + videoFile,
                                e
                        );
                    }
                }

                item = mediaItemRepository.save(item);

                synchronizeMetadata(item, metadata);

            } catch (IOException e) {

                // Stop immediately if the thread was interrupted.
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }

                scanComplete = false;

                // Keep the existing record and continue scanning other folders.
                System.err.println(
                        "Could not scan " + movieFolder + ": "
                                + e.getMessage()
                );
            }
        }

        /*
         * Do not erase the index if the scan was incomplete or found no
         * videos. An empty result can mean a disconnected drive or a
         * temporarily unavailable/locked folder.
         */
        if (!scanComplete || discoveredVideoCount == 0) {
            return;
        }

        for (MediaItem existing : existingItems) {
            String relativePath = normalizeRelativePath(
                    existing.getRelativePath()
            );

            if (!discoveredPaths.contains(relativePath)) {
                deleteIndexedItem(existing);
            }
        }
    }

    private void synchronizeMetadata(
            MediaItem item,
            VideoMetadata source) {

        if (source == null) {
            return;
        }

        MediaMetadata metadata = mediaMetadataRepository
                .findByMediaItem_Id(item.getId())
                .orElseGet(() -> {
                    MediaMetadata created = new MediaMetadata();
                    created.setMediaItem(item);
                    return created;
                });

        metadata.setOriginalTitle(source.getOriginalTitle());
        metadata.setSortTitle(source.getSortTitle());
        metadata.setPlot(source.getPlot());
        metadata.setOutline(source.getOutline());
        metadata.setYear(source.getYear());
        metadata.setPremiered(source.getPremiered());
        metadata.setReleaseDate(source.getReleaseDate());
        metadata.setRuntimeMinutes(source.getRuntime());
        metadata.setCountry(source.getCountry());
        metadata.setOriginalCountry(source.getOriginalCountry());
        metadata.setLanguage(source.getLanguage());
        metadata.setMpaa(source.getMpaa());
        metadata.setWebsite(source.getWebsite());

        // Genres: reuse normalized records.
        Map<String, Genre> genres = new LinkedHashMap<>();

        if (source.getGenres() != null) {
            for (String name : source.getGenres()) {
                if (hasText(name)) {
                    Genre genre = getOrCreateGenre(name);
                    genres.put(normalizeName(name), genre);
                }
            }
        }

        metadata.setGenres(new HashSet<>(genres.values()));

        // Studios: include both the singular and plural DTO fields.
        Map<String, Studio> studios = new LinkedHashMap<>();

        addStudio(studios, source.getStudio());

        if (source.getStudios() != null) {
            for (String name : source.getStudios()) {
                addStudio(studios, name);
            }
        }

        metadata.setStudios(new HashSet<>(studios.values()));

        mediaMetadataRepository.save(metadata);

        // Rebuild people relationships from the current metadata.
        mediaItemPersonRepository.deleteByMediaItem_Id(item.getId());

        List<MediaItemPerson> people = new ArrayList<>();

        addPeople(people, item, source.getDirectors(), "DIRECTOR");
        addPeople(people, item, source.getWriters(), "WRITER");

        if (source.getActors() != null) {
            for (Actor actor : source.getActors()) {
                if (actor == null || !hasText(actor.getName())) {
                    continue;
                }

                MediaItemPerson link = new MediaItemPerson();
                link.setMediaItem(item);
                link.setPerson(getOrCreatePerson(actor.getName()));
                link.setRole("ACTOR");
                link.setCharacterName(trimToNull(actor.getRole()));

                people.add(link);
            }
        }

        mediaItemPersonRepository.saveAll(people);

        // Rebuild tag relationships without duplicating global Tag records.
        mediaItemTagRepository.deleteByMediaItem_Id(item.getId());

        Map<String, Tag> tags = new LinkedHashMap<>();

        if (source.getTags() != null) {
            for (String name : source.getTags()) {
                if (hasText(name)) {
                    tags.put(
                            normalizeName(name),
                            getOrCreateTag(name)
                    );
                }
            }
        }

        List<MediaItemTag> tagLinks = new ArrayList<>();

        for (Tag tag : tags.values()) {
            MediaItemTag link = new MediaItemTag();
            link.setMediaItem(item);
            link.setTag(tag);
            tagLinks.add(link);
        }

        mediaItemTagRepository.saveAll(tagLinks);
    }

    private void addPeople(
            List<MediaItemPerson> target,
            MediaItem item,
            List<String> names,
            String role) {

        if (names == null) {
            return;
        }

        Map<String, Person> uniquePeople = new LinkedHashMap<>();

        for (String name : names) {
            if (hasText(name)) {
                uniquePeople.put(
                        normalizeName(name),
                        getOrCreatePerson(name)
                );
            }
        }

        for (Person person : uniquePeople.values()) {
            MediaItemPerson link = new MediaItemPerson();
            link.setMediaItem(item);
            link.setPerson(person);
            link.setRole(role);
            target.add(link);
        }
    }

    private void addStudio(
            Map<String, Studio> studios,
            String name) {

        if (hasText(name)) {
            studios.put(
                    normalizeName(name),
                    getOrCreateStudio(name)
            );
        }
    }

    private Person getOrCreatePerson(String name) {
        String displayName = name.trim();
        String normalized = normalizeName(displayName);

        return personRepository.findByNormalizedName(normalized)
                .orElseGet(() -> {
                    Person person = new Person();
                    person.setName(displayName);
                    person.setNormalizedName(normalized);
                    return personRepository.save(person);
                });
    }

    private Genre getOrCreateGenre(String name) {
        String displayName = name.trim();
        String normalized = normalizeName(displayName);

        return genreRepository.findByNormalizedName(normalized)
                .orElseGet(() -> {
                    Genre genre = new Genre();
                    genre.setName(displayName);
                    genre.setNormalizedName(normalized);
                    return genreRepository.save(genre);
                });
    }

    private Studio getOrCreateStudio(String name) {
        String displayName = name.trim();
        String normalized = normalizeName(displayName);

        return studioRepository.findByNormalizedName(normalized)
                .orElseGet(() -> {
                    Studio studio = new Studio();
                    studio.setName(displayName);
                    studio.setNormalizedName(normalized);
                    return studioRepository.save(studio);
                });
    }

    private Tag getOrCreateTag(String name) {
        String displayName = name.trim();
        String normalized = normalizeName(displayName);

        return tagRepository.findByNormalizedName(normalized)
                .orElseGet(() -> {
                    Tag tag = new Tag();
                    tag.setName(displayName);
                    tag.setNormalizedName(normalized);
                    return tagRepository.save(tag);
                });
    }

    private void deleteIndexedItem(MediaItem item) {

        String itemId = item.getId();

        mediaItemPersonRepository.deleteByMediaItem_Id(itemId);
        mediaItemTagRepository.deleteByMediaItem_Id(itemId);

        mediaMetadataRepository.findByMediaItem_Id(itemId)
                .ifPresent(mediaMetadataRepository::delete);

        mediaItemRepository.delete(item);
    }

    private Path findVideoFile(Path movieFolder) throws IOException {

        List<Path> videos;

        try (Stream<Path> stream = Files.list(movieFolder)) {
            videos = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isVideoFile)
                    .toList();
        }

        if (videos.isEmpty()) {
            return null;
        }

        String folderName = movieFolder.getFileName().toString();

        for (Path video : videos) {
            String fileName = video.getFileName().toString();
            String baseName = fileName.substring(
                    0,
                    fileName.lastIndexOf('.')
            );

            if (baseName.equalsIgnoreCase(folderName)) {
                return video;
            }
        }

        if (videos.size() == 1) {
            return videos.get(0);
        }

        throw new IOException(
                "Multiple video files found without a matching filename in "
                        + movieFolder
        );
    }

    private boolean isVideoFile(Path path) {
        String fileName = path.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        return VIDEO_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private String resolveTitle(
            VideoMetadata metadata,
            Path movieFolder) {

        if (metadata != null && hasText(metadata.getTitle())) {
            return metadata.getTitle().trim();
        }

        return movieFolder.getFileName().toString();
    }

    private String normalizeRelativePath(String path) {
        return path.replace('\\', '/');
    }

    private String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}