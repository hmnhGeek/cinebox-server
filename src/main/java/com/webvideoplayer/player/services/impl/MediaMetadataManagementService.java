package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.*;
import com.webvideoplayer.player.entities.*;
import com.webvideoplayer.player.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MediaMetadataManagementService {

    private final MediaItemRepository mediaItemRepository;
    private final MediaMetadataRepository mediaMetadataRepository;
    private final MediaItemPersonRepository mediaItemPersonRepository;
    private final MediaItemTagRepository mediaItemTagRepository;

    private final PersonRepository personRepository;
    private final GenreRepository genreRepository;
    private final StudioRepository studioRepository;
    private final TagRepository tagRepository;

    private final MediaItemNfoStatusRepository nfoStatusRepository;

    private final CollectionService collectionService;
    private final NfoFileService nfoFileService;

    @Transactional(readOnly = true)
    public VideoMetadata getMetadata(String mediaItemId) {

        MediaItem item = getMediaItem(mediaItemId);

        return collectionService.mapToVideoMetadata(
                item,
                Path.of(item.getCollection().getFolder())
                        .toAbsolutePath()
                        .normalize()
        );
    }

    @Transactional
    public VideoMetadata updateMetadata(
            String mediaItemId,
            VideoMetadata source
    ) {

        if (source == null) {
            throw new IllegalArgumentException(
                    "Metadata request cannot be empty"
            );
        }

        MediaItem item = getMediaItem(mediaItemId);

        applyMetadata(item, source);

        markNfoPending(item);

        return collectionService.mapToVideoMetadata(
                item,
                Path.of(item.getCollection().getFolder())
                        .toAbsolutePath()
                        .normalize()
        );
    }

    @Transactional
    public VideoMetadata reimportNfo(String mediaItemId)
            throws java.io.IOException {

        MediaItem item = getMediaItem(mediaItemId);

        Path movieFolder = resolveMovieFolder(item);

        NfoMetadata nfo = nfoFileService.readNfo(movieFolder);

        VideoMetadata source = convertNfoToVideoMetadata(nfo);

        applyMetadata(item, source);

        MediaItemNfoStatus status = getOrCreateStatus(item);

        status.setStatus("SYNCED");
        status.setErrorMessage(null);
        status.setLastAttemptAt(Instant.now());
        status.setLastSyncedAt(Instant.now());

        nfoStatusRepository.save(status);

        return collectionService.mapToVideoMetadata(
                item,
                Path.of(item.getCollection().getFolder())
                        .toAbsolutePath()
                        .normalize()
        );
    }

    private void applyMetadata(
            MediaItem item,
            VideoMetadata source
    ) {

        if (hasText(source.getTitle())) {
            item.setTitle(source.getTitle().trim());
        }

        MediaMetadata metadata = mediaMetadataRepository
                .findByMediaItem_Id(item.getId())
                .orElseGet(() -> {
                    MediaMetadata created = new MediaMetadata();
                    created.setMediaItem(item);
                    return created;
                });

        metadata.setOriginalTitle(trimToNull(source.getOriginalTitle()));
        metadata.setSortTitle(trimToNull(source.getSortTitle()));
        metadata.setPlot(trimToNull(source.getPlot()));
        metadata.setOutline(trimToNull(source.getOutline()));
        metadata.setYear(source.getYear());
        metadata.setPremiered(trimToNull(source.getPremiered()));
        metadata.setReleaseDate(trimToNull(source.getReleaseDate()));
        metadata.setRuntimeMinutes(source.getRuntime());
        metadata.setCountry(trimToNull(source.getCountry()));
        metadata.setOriginalCountry(
                trimToNull(source.getOriginalCountry())
        );
        metadata.setLanguage(trimToNull(source.getLanguage()));
        metadata.setMpaa(trimToNull(source.getMpaa()));
        metadata.setWebsite(trimToNull(source.getWebsite()));

        Map<String, Genre> genres = new LinkedHashMap<>();

        if (source.getGenres() != null) {
            for (GenreDTO genreDTO : source.getGenres()) {
                if (genreDTO == null || !hasText(genreDTO.getName())) {
                    continue;
                }

                String name = genreDTO.getName().trim();

                genres.put(
                        normalize(name),
                        getOrCreateGenre(name)
                );
            }
        }

        metadata.setGenres(new HashSet<>(genres.values()));

        Map<String, Studio> studios = new LinkedHashMap<>();

        addStudio(studios, source.getStudio());

        if (source.getStudios() != null) {
            for (StudioDTO studio : source.getStudios()) {
                addStudio(studios, studio);
            }
        }

        metadata.setStudios(new HashSet<>(studios.values()));

        mediaMetadataRepository.save(metadata);

        replacePeople(item, source);
        replaceTags(item, source);
    }

    private void replacePeople(
            MediaItem item,
            VideoMetadata source
    ) {

        mediaItemPersonRepository.deleteByMediaItem_Id(item.getId());

        List<MediaItemPerson> links = new ArrayList<>();

        addPeople(
                links,
                item,
                source.getDirectors(),
                "DIRECTOR"
        );

        addPeople(
                links,
                item,
                source.getWriters(),
                "WRITER"
        );

        Map<String, Person> actors = new LinkedHashMap<>();

        if (source.getActors() != null) {
            for (ActorDTO actor : source.getActors()) {
                if (actor == null || !hasText(actor.getName())) {
                    continue;
                }

                String name = actor.getName().trim();
                String key = normalize(name);

                Person person = getOrCreatePerson(name);

                MediaItemPerson link = new MediaItemPerson();
                link.setMediaItem(item);
                link.setPerson(person);
                link.setRole("ACTOR");
                link.setCharacterName(trimToNull(actor.getRole()));

                // Keep separate character credits for the same actor
                // only when the database uniqueness constraint permits it.
                String uniqueKey = key + "|"
                        + normalize(
                        actor.getRole() == null
                                ? ""
                                : actor.getRole()
                );

                actors.putIfAbsent(uniqueKey, person);

                if (links.stream().noneMatch(existing ->
                        "ACTOR".equals(existing.getRole())
                                && existing.getPerson().getId()
                                .equals(person.getId()))) {
                    links.add(link);
                }
            }
        }

        mediaItemPersonRepository.saveAll(links);
    }

    private void addPeople(
            List<MediaItemPerson> links,
            MediaItem item,
            List<PersonDTO> source,
            String role
    ) {

        if (source == null) {
            return;
        }

        Map<String, Person> unique = new LinkedHashMap<>();

        for (PersonDTO personDTO : source) {
            if (personDTO == null || !hasText(personDTO.getName())) {
                continue;
            }

            String name = personDTO.getName().trim();

            unique.putIfAbsent(
                    normalize(name),
                    getOrCreatePerson(name)
            );
        }

        for (Person person : unique.values()) {
            MediaItemPerson link = new MediaItemPerson();
            link.setMediaItem(item);
            link.setPerson(person);
            link.setRole(role);
            links.add(link);
        }
    }

    private void replaceTags(
            MediaItem item,
            VideoMetadata source
    ) {

        mediaItemTagRepository.deleteByMediaItem_Id(item.getId());

        Map<String, Tag> uniqueTags = new LinkedHashMap<>();

        if (source.getTags() != null) {
            for (TagDTO tagDTO : source.getTags()) {
                if (tagDTO == null || !hasText(tagDTO.getName())) {
                    continue;
                }

                String name = tagDTO.getName().trim();

                uniqueTags.putIfAbsent(
                        normalize(name),
                        getOrCreateTag(name)
                );
            }
        }

        List<MediaItemTag> links = new ArrayList<>();

        for (Tag tag : uniqueTags.values()) {
            MediaItemTag link = new MediaItemTag();
            link.setMediaItem(item);
            link.setTag(tag);
            links.add(link);
        }

        mediaItemTagRepository.saveAll(links);
    }

    private void addStudio(
            Map<String, Studio> studios,
            StudioDTO studioDTO
    ) {

        if (studioDTO == null || !hasText(studioDTO.getName())) {
            return;
        }

        String name = studioDTO.getName().trim();

        studios.putIfAbsent(
                normalize(name),
                getOrCreateStudio(name)
        );
    }

    private Person getOrCreatePerson(String name) {
        String normalized = normalize(name);

        return personRepository.findByNormalizedName(normalized)
                .orElseGet(() -> {
                    Person person = new Person();
                    person.setName(name.trim());
                    person.setNormalizedName(normalized);
                    return personRepository.save(person);
                });
    }

    private Genre getOrCreateGenre(String name) {
        String normalized = normalize(name);

        return genreRepository.findByNormalizedName(normalized)
                .orElseGet(() -> {
                    Genre genre = new Genre();
                    genre.setName(name.trim());
                    genre.setNormalizedName(normalized);
                    return genreRepository.save(genre);
                });
    }

    private Studio getOrCreateStudio(String name) {
        String normalized = normalize(name);

        return studioRepository.findByNormalizedName(normalized)
                .orElseGet(() -> {
                    Studio studio = new Studio();
                    studio.setName(name.trim());
                    studio.setNormalizedName(normalized);
                    return studioRepository.save(studio);
                });
    }

    private Tag getOrCreateTag(String name) {
        String normalized = normalize(name);

        return tagRepository.findByNormalizedName(normalized)
                .orElseGet(() -> {
                    Tag tag = new Tag();
                    tag.setName(name.trim());
                    tag.setNormalizedName(normalized);
                    return tagRepository.save(tag);
                });
    }

    private void markNfoPending(MediaItem item) {

        MediaItemNfoStatus status = getOrCreateStatus(item);

        status.setStatus("PENDING");
        status.setErrorMessage(null);

        nfoStatusRepository.save(status);
    }

    private MediaItemNfoStatus getOrCreateStatus(MediaItem item) {

        return nfoStatusRepository
                .findByMediaItem_Id(item.getId())
                .orElseGet(() -> {
                    MediaItemNfoStatus status =
                            new MediaItemNfoStatus();

                    status.setMediaItem(item);
                    status.setStatus("PENDING");

                    return status;
                });
    }

    private MediaItem getMediaItem(String mediaItemId) {

        return mediaItemRepository.findById(mediaItemId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Media item not found: " + mediaItemId
                        )
                );
    }

    private Path resolveMovieFolder(MediaItem item) {

        Path collectionFolder = Path.of(
                item.getCollection().getFolder()
        ).toAbsolutePath().normalize();

        Path videoPath = collectionFolder.resolve(
                item.getRelativePath()
        ).normalize();

        Path movieFolder = videoPath.getParent();

        if (movieFolder == null
                || !movieFolder.startsWith(collectionFolder)) {
            throw new IllegalArgumentException(
                    "Invalid media item path: " + item.getRelativePath()
            );
        }

        return movieFolder;
    }

    private VideoMetadata convertNfoToVideoMetadata(
            NfoMetadata nfo
    ) {

        List<GenreDTO> genres = nfo.genres.stream()
                .filter(this::hasText)
                .map(name -> GenreDTO.builder()
                        .name(name.trim())
                        .build())
                .toList();

        List<StudioDTO> studios = nfo.studios.stream()
                .filter(this::hasText)
                .map(name -> StudioDTO.builder()
                        .name(name.trim())
                        .build())
                .toList();

        List<PersonDTO> directors = nfo.directors.stream()
                .filter(this::hasText)
                .map(name -> PersonDTO.builder()
                        .name(name.trim())
                        .build())
                .toList();

        List<PersonDTO> writers = nfo.writers.stream()
                .filter(this::hasText)
                .map(name -> PersonDTO.builder()
                        .name(name.trim())
                        .build())
                .toList();

        List<TagDTO> tags = nfo.tags.stream()
                .filter(this::hasText)
                .map(name -> TagDTO.builder()
                        .name(name.trim())
                        .build())
                .toList();

        List<ActorDTO> actors = nfo.actors.stream()
                .filter(actor -> actor != null
                        && hasText(actor.getName()))
                .map(actor -> ActorDTO.builder()
                        .name(actor.getName().trim())
                        .role(trimToNull(actor.getRole()))
                        .build())
                .toList();

        StudioDTO studio = nfo.studio == null
                ? null
                : StudioDTO.builder()
                .name(nfo.studio)
                .build();

        return VideoMetadata.builder()
                .title(nfo.title)
                .originalTitle(nfo.originalTitle)
                .sortTitle(nfo.sortTitle)
                .plot(nfo.plot)
                .outline(nfo.outline)
                .year(nfo.year)
                .premiered(nfo.premiered)
                .releaseDate(nfo.releaseDate)
                .runtime(nfo.runtime)
                .genres(genres)
                .studios(studios)
                .studio(studio)
                .directors(directors)
                .writers(writers)
                .actors(actors)
                .tags(tags)
                .country(nfo.country)
                .originalCountry(nfo.originalCountry)
                .language(nfo.language)
                .mpaa(nfo.mpaa)
                .website(nfo.website)
                .build();
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}