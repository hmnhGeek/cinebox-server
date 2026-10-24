
package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.Actor;
import com.webvideoplayer.player.dto.ActorDTO;
import com.webvideoplayer.player.dto.GenreDTO;
import com.webvideoplayer.player.dto.NfoMetadata;
import com.webvideoplayer.player.dto.PersonDTO;
import com.webvideoplayer.player.dto.StudioDTO;
import com.webvideoplayer.player.dto.TagDTO;
import com.webvideoplayer.player.dto.VideoMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class VideoScannerService {

    private static final List<String> VIDEO_EXTENSIONS = List.of(
            ".mp4",
            ".mkv",
            ".avi",
            ".mov",
            ".webm",
            ".m4v",
            ".ts"
    );

    public VideoMetadata getVideoMetadata(
            Path collectionFolder,
            Path videoFolderPath
    ) {

        if (!Files.isDirectory(collectionFolder)) {
            throw new IllegalArgumentException(
                    "Collection folder does not exist: " + collectionFolder
            );
        }

        Path resolvedVideoFolder = collectionFolder.resolve(videoFolderPath);

        if (!Files.isDirectory(resolvedVideoFolder)) {
            throw new IllegalArgumentException(
                    "Video folder does not exist: " + resolvedVideoFolder
            );
        }

        try {
            Optional<Path> videoFile = findVideoFile(resolvedVideoFolder);

            if (videoFile.isEmpty()) {
                throw new IllegalArgumentException(
                        "No video file found in folder: " + resolvedVideoFolder
                );
            }

            return createVideoMetadata(
                    resolvedVideoFolder,
                    videoFile.get()
            );

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to scan video folder: " + resolvedVideoFolder,
                    e
            );
        }
    }

    private Optional<Path> findVideoFile(Path videoFolder)
            throws IOException {

        String folderName = videoFolder.getFileName().toString();

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(videoFolder)) {

            for (Path file : stream) {

                if (!Files.isRegularFile(file)) {
                    continue;
                }

                String fileName = file.getFileName().toString();
                String baseName = removeExtension(fileName);
                String extension = getExtension(fileName).toLowerCase();

                /*
                 * Video filename should match the folder name.
                 *
                 * Example:
                 * Inception/
                 *     Inception.mkv
                 */
                if (baseName.equals(folderName)
                        && VIDEO_EXTENSIONS.contains(extension)) {

                    return Optional.of(file);
                }
            }
        }

        return Optional.empty();
    }

    private VideoMetadata createVideoMetadata(
            Path videoFolder,
            Path videoFile
    ) {

        Path thumbnail = findFile(videoFolder, "thumbnail");
        Path nfo = findNfoFile(videoFolder);

        NfoMetadata nfoMetadata = parseNfo(nfo);

        return VideoMetadata.builder()
                .title(
                        nfoMetadata.title != null
                                ? nfoMetadata.title
                                : videoFolder.getFileName().toString()
                )
                .originalTitle(nfoMetadata.originalTitle)
                .sortTitle(nfoMetadata.sortTitle)
                .plot(nfoMetadata.plot)
                .outline(nfoMetadata.outline)
                .year(nfoMetadata.year)
                .premiered(nfoMetadata.premiered)
                .releaseDate(nfoMetadata.releaseDate)
                .runtime(nfoMetadata.runtime)

                // NFO values are names only; database IDs are resolved later.
                .studio(toStudioDTO(nfoMetadata.studio))
                .studios(toStudioDTOs(nfoMetadata.studios))
                .directors(toPersonDTOs(nfoMetadata.directors))
                .writers(toPersonDTOs(nfoMetadata.writers))
                .tags(toTagDTOs(nfoMetadata.tags))
                .actors(toActorDTOs(nfoMetadata.actors))
                .genres(toGenreDTOs(nfoMetadata.genres))

                .country(nfoMetadata.country)
                .originalCountry(nfoMetadata.originalCountry)
                .language(nfoMetadata.language)
                .mpaa(nfoMetadata.mpaa)
                .website(nfoMetadata.website)

                .thumbnailUrl(
                        String.format(
                                "/collections/{collectionId}/videos/%s/thumbnail",
                                videoFolder.getFileName()
                        )
                )
                .build();
    }

    private StudioDTO toStudioDTO(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        return StudioDTO.builder()
                .name(name)
                .build();
    }

    private List<StudioDTO> toStudioDTOs(List<String> names) {
        if (names == null) {
            return List.of();
        }

        return names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> StudioDTO.builder()
                        .name(name)
                        .build())
                .toList();
    }

    private List<PersonDTO> toPersonDTOs(List<String> names) {
        if (names == null) {
            return List.of();
        }

        return names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> PersonDTO.builder()
                        .name(name)
                        .build())
                .toList();
    }

    private List<TagDTO> toTagDTOs(List<String> names) {
        if (names == null) {
            return List.of();
        }

        return names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> TagDTO.builder()
                        .name(name)
                        .build())
                .toList();
    }

    private List<GenreDTO> toGenreDTOs(List<String> names) {
        if (names == null) {
            return List.of();
        }

        return names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> GenreDTO.builder()
                        .name(name)
                        .build())
                .toList();
    }

    private List<ActorDTO> toActorDTOs(List<Actor> actors) {
        if (actors == null) {
            return List.of();
        }

        return actors.stream()
                .filter(Objects::nonNull)
                .filter(actor -> actor.getName() != null
                        && !actor.getName().isBlank())
                .map(actor -> ActorDTO.builder()
                        .name(actor.getName())
                        .role(actor.getRole())
                        .build())
                .toList();
    }

    private Path findFile(Path folder, String baseName) {

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(folder)) {

            for (Path file : stream) {

                if (!Files.isRegularFile(file)) {
                    continue;
                }

                if (removeExtension(
                        file.getFileName().toString()
                ).equalsIgnoreCase(baseName)) {

                    return file;
                }
            }

        } catch (IOException e) {
            log.error(
                    "Failed searching for {} in {}",
                    baseName,
                    folder,
                    e
            );
        }

        return null;
    }

    private Path findNfoFile(Path folder) {

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(folder, "*.nfo")) {

            for (Path file : stream) {
                return file;
            }

        } catch (IOException e) {
            log.error(
                    "Failed finding NFO in {}",
                    folder,
                    e
            );
        }

        return null;
    }

    private String getValue(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);

        if (nodes.getLength() == 0) {
            return null;
        }

        String value = nodes.item(0).getTextContent().trim();
        return value.isEmpty() ? null : value;
    }

    private List<String> getValues(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        List<String> values = new ArrayList<>();

        for (int i = 0; i < nodes.getLength(); i++) {
            String value = nodes.item(i).getTextContent().trim();

            if (!value.isEmpty()) {
                values.add(value);
            }
        }

        return values;
    }

    private String getChildValue(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);

        if (nodes.getLength() == 0) {
            return null;
        }

        String value = nodes.item(0).getTextContent().trim();
        return value.isEmpty() ? null : value;
    }

    private NfoMetadata parseNfo(Path nfoPath) {

        NfoMetadata metadata = new NfoMetadata();

        if (nfoPath == null || !Files.exists(nfoPath)) {
            return metadata;
        }

        try {
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            // Prevent XML external entity attacks.
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
            );
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory
                    .newDocumentBuilder()
                    .parse(nfoPath.toFile());

            document.getDocumentElement().normalize();

            metadata.title = getValue(document, "title");
            metadata.originalTitle = getValue(document, "originaltitle");
            metadata.sortTitle = getValue(document, "sorttitle");
            metadata.plot = getValue(document, "plot");
            metadata.outline = getValue(document, "outline");
            metadata.country = getValue(document, "country");
            metadata.originalCountry = getValue(document, "originalcountry");
            metadata.language = getValue(document, "language");
            metadata.mpaa = getValue(document, "mpaa");
            metadata.premiered = getValue(document, "premiered");
            metadata.releaseDate = getValue(document, "releasedate");
            metadata.website = getValue(document, "website");

            String year = getValue(document, "year");

            if (year != null) {
                try {
                    metadata.year = Integer.parseInt(year);
                } catch (NumberFormatException ignored) {
                    log.warn("Invalid year in NFO file: {}", year);
                }
            }

            String runtime = getValue(document, "runtime");

            if (runtime != null) {
                try {
                    metadata.runtime = Integer.parseInt(runtime);
                } catch (NumberFormatException ignored) {
                    log.warn("Invalid runtime in NFO file: {}", runtime);
                }
            }

            metadata.genres = getValues(document, "genre");
            metadata.directors = getValues(document, "director");
            metadata.writers = getValues(document, "writer");
            metadata.tags = getValues(document, "tag");
            metadata.studios = getValues(document, "studio");

            metadata.studio = metadata.studios.isEmpty()
                    ? null
                    : metadata.studios.get(0);

            NodeList actorNodes =
                    document.getElementsByTagName("actor");

            for (int i = 0; i < actorNodes.getLength(); i++) {
                Element element = (Element) actorNodes.item(i);

                String name = getChildValue(element, "name");

                if (name == null) {
                    continue;
                }

                Actor actor = Actor.builder()
                        .name(name)
                        .role(getChildValue(element, "role"))
                        .build();

                metadata.actors.add(actor);
            }

        } catch (Exception e) {
            log.warn("Failed to parse NFO file: {}", nfoPath, e);
        }

        return metadata;
    }

    private String removeExtension(String filename) {

        int index = filename.lastIndexOf('.');

        if (index <= 0) {
            return filename;
        }

        return filename.substring(0, index);
    }

    private String getExtension(String filename) {

        int index = filename.lastIndexOf('.');

        if (index <= 0) {
            return "";
        }

        return filename.substring(index);
    }

    private Path findThumbnail(Path videoFolder) throws IOException {
        try (var files = Files.list(videoFolder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .matches("(?i)thumbnail\\.[^.]+"))
                    .findFirst()
                    .orElse(null);
        }
    }
}
