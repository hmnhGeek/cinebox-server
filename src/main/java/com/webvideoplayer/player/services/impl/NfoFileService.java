
package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.Actor;
import com.webvideoplayer.player.dto.ActorDTO;
import com.webvideoplayer.player.dto.NfoMetadata;
import com.webvideoplayer.player.dto.VideoMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class NfoFileService {

    public NfoMetadata readNfo(Path movieFolder) throws IOException {

        Path nfoFile = findNfoFile(movieFolder);

        if (nfoFile == null) {
            throw new IOException(
                    "NFO file not found in: " + movieFolder
            );
        }

        try {
            Document document = newDocumentBuilderFactory()
                    .newDocumentBuilder()
                    .parse(nfoFile.toFile());

            document.getDocumentElement().normalize();

            Element root = document.getDocumentElement();

            NfoMetadata metadata = new NfoMetadata();

            metadata.title = getChildValue(root, "title");
            metadata.originalTitle =
                    getChildValue(root, "originaltitle");
            metadata.sortTitle =
                    getChildValue(root, "sorttitle");
            metadata.plot = getChildValue(root, "plot");
            metadata.outline = getChildValue(root, "outline");
            metadata.country = getChildValue(root, "country");
            metadata.originalCountry =
                    getChildValue(root, "originalcountry");
            metadata.language = getChildValue(root, "language");
            metadata.mpaa = getChildValue(root, "mpaa");
            metadata.premiered = getChildValue(root, "premiered");
            metadata.releaseDate =
                    getChildValue(root, "releasedate");
            metadata.website = getChildValue(root, "website");

            metadata.year = parseInteger(
                    getChildValue(root, "year")
            );

            metadata.runtime = parseInteger(
                    getChildValue(root, "runtime")
            );

            metadata.genres = getChildValues(root, "genre");
            metadata.studios = getChildValues(root, "studio");
            metadata.directors = getChildValues(root, "director");
            metadata.writers = getChildValues(root, "writer");
            metadata.tags = getChildValues(root, "tag");

            metadata.studio = metadata.studios.isEmpty()
                    ? null
                    : metadata.studios.getFirst();

            for (Element actorElement :
                    getDirectChildElements(root, "actor")) {

                String name = getChildValue(actorElement, "name");

                if (name == null || name.isBlank()) {
                    continue;
                }

                metadata.actors.add(
                        Actor.builder()
                                .name(name)
                                .role(getChildValue(
                                        actorElement,
                                        "role"
                                ))
                                .build()
                );
            }

            return metadata;

        } catch (Exception e) {
            throw new IOException(
                    "Failed to read NFO file: " + nfoFile,
                    e
            );
        }
    }

    public boolean hasNfoFile(Path movieFolder) throws IOException {
        return findNfoFile(movieFolder) != null;
    }

    public void writeNfo(
            Path movieFolder,
            VideoMetadata metadata
    ) throws IOException {

        if (movieFolder == null
                || !Files.isDirectory(movieFolder)
                || !Files.isWritable(movieFolder)) {

            throw new IOException(
                    "Movie folder is unavailable or not writable: "
                            + movieFolder
            );
        }

        Path nfoFile = findNfoFile(movieFolder);

        if (nfoFile == null) {
            String folderName = movieFolder.getFileName().toString();
            nfoFile = movieFolder.resolve(folderName + ".nfo");
        }

        Document document;

        try {
            if (Files.exists(nfoFile)) {
                document = newDocumentBuilderFactory()
                        .newDocumentBuilder()
                        .parse(nfoFile.toFile());

                document.getDocumentElement().normalize();

            } else {
                document = newDocumentBuilderFactory()
                        .newDocumentBuilder()
                        .newDocument();

                document.appendChild(
                        document.createElement("movie")
                );
            }

            Element root = document.getDocumentElement();

            setChildValue(root, "title", metadata.getTitle());
            setChildValue(
                    root,
                    "originaltitle",
                    metadata.getOriginalTitle()
            );
            setChildValue(
                    root,
                    "sorttitle",
                    metadata.getSortTitle()
            );
            setChildValue(root, "plot", metadata.getPlot());
            setChildValue(root, "outline", metadata.getOutline());
            setChildValue(
                    root,
                    "year",
                    metadata.getYear() == null
                            ? null
                            : metadata.getYear().toString()
            );
            setChildValue(
                    root,
                    "premiered",
                    metadata.getPremiered()
            );
            setChildValue(
                    root,
                    "releasedate",
                    metadata.getReleaseDate()
            );
            setChildValue(
                    root,
                    "runtime",
                    metadata.getRuntime() == null
                            ? null
                            : metadata.getRuntime().toString()
            );
            setChildValue(root, "country", metadata.getCountry());
            setChildValue(
                    root,
                    "originalcountry",
                    metadata.getOriginalCountry()
            );
            setChildValue(root, "language", metadata.getLanguage());
            setChildValue(root, "mpaa", metadata.getMpaa());
            setChildValue(root, "website", metadata.getWebsite());

            List<String> genres = metadata.getGenres() == null
                    ? List.of()
                    : metadata.getGenres().stream()
                    .filter(value -> value != null
                            && value.getName() != null)
                    .map(value -> value.getName().trim())
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .toList();

            replaceChildValues(root, "genre", genres);

            List<String> studios = new ArrayList<>();

            if (metadata.getStudios() != null) {
                metadata.getStudios().stream()
                        .filter(value -> value != null
                                && value.getName() != null)
                        .map(value -> value.getName().trim())
                        .filter(value -> !value.isEmpty())
                        .forEach(studios::add);
            }

            if (studios.isEmpty()
                    && metadata.getStudio() != null
                    && metadata.getStudio().getName() != null
                    && !metadata.getStudio().getName().isBlank()) {

                studios.add(metadata.getStudio().getName().trim());
            }

            replaceChildValues(
                    root,
                    "studio",
                    studios.stream().distinct().toList()
            );

            List<String> directors = metadata.getDirectors() == null
                    ? List.of()
                    : metadata.getDirectors().stream()
                    .filter(value -> value != null
                            && value.getName() != null)
                    .map(value -> value.getName().trim())
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .toList();

            replaceChildValues(root, "director", directors);

            List<String> writers = metadata.getWriters() == null
                    ? List.of()
                    : metadata.getWriters().stream()
                    .filter(value -> value != null
                            && value.getName() != null)
                    .map(value -> value.getName().trim())
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .toList();

            replaceChildValues(root, "writer", writers);

            List<String> tags = metadata.getTags() == null
                    ? List.of()
                    : metadata.getTags().stream()
                    .filter(value -> value != null
                            && value.getName() != null)
                    .map(value -> value.getName().trim())
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .toList();

            replaceChildValues(root, "tag", tags);

            replaceActors(root, metadata.getActors());

            writeAtomically(document, nfoFile);

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(
                    "Failed to write NFO file: " + nfoFile,
                    e
            );
        }
    }

    private DocumentBuilderFactory newDocumentBuilderFactory()
            throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setFeature(
                XMLConstants.FEATURE_SECURE_PROCESSING,
                true
        );

        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );

        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
        );

        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        return factory;
    }

    /**
     * Removes whitespace-only text nodes from the XML DOM.
     * This prevents blank lines from accumulating when an existing
     * NFO document is updated repeatedly.
     *
     * Text content inside actual metadata elements is preserved.
     */
    private void removeWhitespaceNodes(Node node) {

        NodeList children = node.getChildNodes();

        for (int i = children.getLength() - 1; i >= 0; i--) {

            Node child = children.item(i);

            if (child.getNodeType() == Node.TEXT_NODE
                    && child.getTextContent().isBlank()) {

                node.removeChild(child);

            } else if (child.hasChildNodes()) {

                removeWhitespaceNodes(child);
            }
        }
    }

    private void writeAtomically(
            Document document,
            Path destination
    ) throws Exception {

        Path temporaryFile = Files.createTempFile(
                destination.getParent(),
                ".cinebox-",
                ".nfo.tmp"
        );

        try {
            TransformerFactory transformerFactory =
                    TransformerFactory.newInstance();

            transformerFactory.setFeature(
                    XMLConstants.FEATURE_SECURE_PROCESSING,
                    true
            );

            transformerFactory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );

            transformerFactory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_STYLESHEET,
                    ""
            );

            // Remove existing blank lines before serialization.
            removeWhitespaceNodes(document);

            Transformer transformer =
                    transformerFactory.newTransformer();

            transformer.setOutputProperty(
                    OutputKeys.ENCODING,
                    "UTF-8"
            );

            transformer.setOutputProperty(
                    OutputKeys.INDENT,
                    "yes"
            );

            transformer.transform(
                    new DOMSource(document),
                    new StreamResult(temporaryFile.toFile())
            );

            try {
                Files.move(
                        temporaryFile,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        temporaryFile,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private Path findNfoFile(Path movieFolder) throws IOException {

        if (!Files.isDirectory(movieFolder)) {
            throw new IOException(
                    "Movie folder does not exist: " + movieFolder
            );
        }

        try (var files = Files.list(movieFolder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .toLowerCase(Locale.ROOT)
                            .endsWith(".nfo"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
        }
    }

    private String getChildValue(
            Element parent,
            String tagName
    ) {

        for (Element child :
                getDirectChildElements(parent, tagName)) {

            String value = child.getTextContent();

            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    private List<String> getChildValues(
            Element parent,
            String tagName
    ) {

        List<String> values = new ArrayList<>();

        for (Element child :
                getDirectChildElements(parent, tagName)) {

            String value = child.getTextContent();

            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }

        return values;
    }

    private List<Element> getDirectChildElements(
            Element parent,
            String tagName
    ) {

        List<Element> elements = new ArrayList<>();

        NodeList children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);

            if (node instanceof Element element
                    && tagName.equalsIgnoreCase(
                    element.getTagName()
            )) {
                elements.add(element);
            }
        }

        return elements;
    }

    private void setChildValue(
            Element parent,
            String tagName,
            String value
    ) {

        List<Element> existing =
                getDirectChildElements(parent, tagName);

        if (value == null || value.isBlank()) {
            existing.forEach(parent::removeChild);
            return;
        }

        Element target;

        if (existing.isEmpty()) {
            target = parent.getOwnerDocument()
                    .createElement(tagName);

            parent.appendChild(target);

        } else {
            target = existing.getFirst();

            for (int i = 1; i < existing.size(); i++) {
                parent.removeChild(existing.get(i));
            }
        }

        target.setTextContent(value.trim());
    }

    private void replaceChildValues(
            Element parent,
            String tagName,
            List<String> values
    ) {

        for (Element existing :
                getDirectChildElements(parent, tagName)) {
            parent.removeChild(existing);
        }

        if (values == null) {
            return;
        }

        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }

            Element element = parent.getOwnerDocument()
                    .createElement(tagName);

            element.setTextContent(value.trim());
            parent.appendChild(element);
        }
    }

    private void replaceActors(
            Element root,
            List<ActorDTO> actors
    ) {

        List<Element> existingActors =
                getDirectChildElements(root, "actor");

        List<Element> result = new ArrayList<>();

        if (actors != null) {
            for (ActorDTO actor : actors) {
                if (actor == null
                        || actor.getName() == null
                        || actor.getName().isBlank()) {
                    continue;
                }

                Element actorElement = existingActors.stream()
                        .filter(existing ->
                                actor.getName().trim().equalsIgnoreCase(
                                        getChildValue(existing, "name")
                                )
                        )
                        .findFirst()
                        .orElseGet(() ->
                                root.getOwnerDocument()
                                        .createElement("actor")
                        );

                setChildValue(
                        actorElement,
                        "name",
                        actor.getName()
                );

                setChildValue(
                        actorElement,
                        "role",
                        actor.getRole()
                );

                result.add(actorElement);
            }
        }

        for (Element existing : existingActors) {
            root.removeChild(existing);
        }

        for (Element actor : result) {
            root.appendChild(actor);
        }
    }

    private Integer parseInteger(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value in NFO: {}", value);
            return null;
        }
    }
}
