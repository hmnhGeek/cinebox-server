
package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.GalleryImagePageDTO;
import com.webvideoplayer.player.entities.MediaItem;
import com.webvideoplayer.player.repositories.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class GalleryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final String GALLERY_FOLDER_NAME = "gallery";

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".webp",
            ".gif",
            ".avif",
            ".bmp"
    );

    private final MediaItemRepository mediaItemRepository;

    /**
     * Returns paginated metadata for images in a movie's gallery folder.
     *
     * A missing gallery folder is treated as an empty gallery.
     */
    @Transactional(readOnly = true)
    public GalleryImagePageDTO getGalleryImages(
            String collectionId,
            String mediaItemId,
            int page,
            int size
    ) {
        validatePagination(page, size);

        GalleryContext context = resolveGalleryContext(
                collectionId,
                mediaItemId
        );

        if (context.galleryFolder() == null) {
            return emptyPage(page, size);
        }

        List<Path> imageFiles = listGalleryImages(
                context.galleryFolder()
        );

        long totalImages = imageFiles.size();

        int totalPages = totalImages == 0
                ? 0
                : (int) ((totalImages + size - 1) / size);

        long offset = (long) page * size;

        List<GalleryImagePageDTO.GalleryImageDTO> images =
                new ArrayList<>();

        if (offset < totalImages) {
            int startIndex = (int) offset;
            int endIndex = (int) Math.min(
                    offset + size,
                    totalImages
            );

            for (Path imagePath : imageFiles.subList(
                    startIndex,
                    endIndex
            )) {
                try {
                    String filename = imagePath
                            .getFileName()
                            .toString();

                    images.add(
                            new GalleryImagePageDTO.GalleryImageDTO(
                                    encodeImageId(filename),
                                    filename,
                                    getContentType(filename).toString(),
                                    Files.size(imagePath),
                                    buildImageUrl(
                                            collectionId,
                                            mediaItemId,
                                            filename
                                    )
                            )
                    );
                } catch (IOException e) {
                    throw new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Unable to read gallery image metadata",
                            e
                    );
                }
            }
        }

        return new GalleryImagePageDTO(
                page,
                size,
                totalImages,
                totalPages,
                page + 1 < totalPages,
                page > 0,
                images
        );
    }

    /**
     * Returns one original gallery image.
     */
    @Transactional(readOnly = true)
    public GalleryImageFile getGalleryImage(
            String collectionId,
            String mediaItemId,
            String imageId
    ) {
        GalleryContext context = resolveGalleryContext(
                collectionId,
                mediaItemId
        );

        if (context.galleryFolder() == null) {
            throw notFound("Gallery folder not found");
        }

        String filename = decodeImageId(imageId);

        if (!isSupportedImage(filename)) {
            throw notFound("Gallery image not found");
        }

        Path imagePath = context.galleryFolder()
                .resolve(filename)
                .normalize();

        if (!imagePath.startsWith(context.galleryFolder())) {
            throw notFound("Gallery image not found");
        }

        // Do not allow symbolic links to expose files outside the gallery.
        if (Files.isSymbolicLink(imagePath)
                || !Files.isRegularFile(
                imagePath,
                LinkOption.NOFOLLOW_LINKS
        )
                || !Files.isReadable(imagePath)) {
            throw notFound("Gallery image not found");
        }

        try {
            Path realImagePath = imagePath.toRealPath();

            if (!realImagePath.startsWith(context.galleryFolder())) {
                throw notFound("Gallery image not found");
            }

            long sizeBytes = Files.size(realImagePath);

            return new GalleryImageFile(
                    new FileSystemResource(realImagePath),
                    getContentType(filename),
                    sizeBytes,
                    filename
            );
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Gallery image not found",
                    e
            );
        }
    }

    /**
     * Resolves the movie folder from the existing media catalog record.
     *
     * The media item must belong to the collection specified in the URL.
     */
    private GalleryContext resolveGalleryContext(
            String collectionId,
            String mediaItemId
    ) {
        MediaItem item = mediaItemRepository.findById(mediaItemId)
                .orElseThrow(() ->
                        notFound("Media item not found")
                );

        if (item.getCollection() == null
                || !Objects.equals(
                item.getCollection().getId(),
                collectionId
        )) {
            throw notFound("Media item not found in this collection");
        }

        Path collectionPath;

        try {
            collectionPath = Paths.get(
                    item.getCollection().getFolder()
            ).toAbsolutePath().normalize();

            if (!Files.isDirectory(collectionPath)
                    || !Files.isReadable(collectionPath)) {
                throw notFound("Collection folder not found");
            }

            Path realCollectionPath = collectionPath.toRealPath();

            Path videoPath = collectionPath
                    .resolve(item.getRelativePath())
                    .normalize();

            // Reject absolute or relative paths escaping the collection.
            if (!videoPath.startsWith(collectionPath)) {
                throw invalidPath();
            }

            if (!Files.isRegularFile(videoPath)) {
                throw notFound("Video file not found");
            }

            Path realVideoPath = videoPath.toRealPath();

            // Also check the resolved filesystem path, not just the
            // normalized path, to guard against symbolic-link escapes.
            if (!realVideoPath.startsWith(realCollectionPath)) {
                throw invalidPath();
            }

            Path movieFolder = realVideoPath.getParent();

            if (movieFolder == null
                    || !movieFolder.startsWith(realCollectionPath)) {
                throw invalidPath();
            }

            Path galleryPath = movieFolder
                    .resolve(GALLERY_FOLDER_NAME)
                    .normalize();

            if (!galleryPath.startsWith(movieFolder)) {
                throw invalidPath();
            }

            // A missing gallery directory is a valid empty gallery.
            if (!Files.exists(
                    galleryPath,
                    LinkOption.NOFOLLOW_LINKS
            )) {
                return new GalleryContext(
                        realCollectionPath,
                        movieFolder,
                        null
                );
            }

            // Do not follow a gallery directory symlink.
            if (Files.isSymbolicLink(galleryPath)
                    || !Files.isDirectory(
                    galleryPath,
                    LinkOption.NOFOLLOW_LINKS
            )) {
                throw invalidPath();
            }

            Path realGalleryPath = galleryPath.toRealPath();

            if (!realGalleryPath.startsWith(movieFolder)
                    || !realGalleryPath.startsWith(realCollectionPath)) {
                throw invalidPath();
            }

            return new GalleryContext(
                    realCollectionPath,
                    movieFolder,
                    realGalleryPath
            );

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Unable to access the movie gallery",
                    e
            );
        }
    }

    /**
     * Lists supported images in a deterministic filename order.
     */
    private List<Path> listGalleryImages(Path galleryFolder) {
        try (Stream<Path> files = Files.list(galleryFolder)) {
            return files
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isRegularFile(
                            path,
                            LinkOption.NOFOLLOW_LINKS
                    ))
                    .filter(path -> isSupportedImage(
                            path.getFileName().toString()
                    ))
                    .sorted(
                            Comparator.comparing(
                                    (Path path) -> path.getFileName()
                                            .toString(),
                                    String.CASE_INSENSITIVE_ORDER
                            ).thenComparing(
                                    path -> path.getFileName().toString()
                            )
                    )
                    .toList();

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to read gallery directory",
                    e
            );
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and "
                            + MAX_PAGE_SIZE
            );
        }
    }

    private GalleryImagePageDTO emptyPage(int page, int size) {
        return new GalleryImagePageDTO(
                page,
                size,
                0,
                0,
                false,
                page > 0,
                List.of()
        );
    }

    /**
     * Uses URL-safe Base64 of the filename as a stable filesystem ID.
     * No database records are created.
     */
    private String encodeImageId(String filename) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        filename.getBytes(StandardCharsets.UTF_8)
                );
    }

    private String decodeImageId(String imageId) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(imageId);

            String filename = new String(
                    decoded,
                    StandardCharsets.UTF_8
            );

            // Only a filename is accepted, never a path.
            if (filename.isBlank()
                    || filename.equals(".")
                    || filename.equals("..")
                    || filename.contains("/")
                    || filename.contains("\\")
                    || !encodeImageId(filename).equals(imageId)) {
                throw notFound("Gallery image not found");
            }

            return filename;

        } catch (IllegalArgumentException e) {
            throw notFound("Gallery image not found");
        }
    }

    private boolean isSupportedImage(String filename) {
        String lowerCaseFilename = filename.toLowerCase(Locale.ROOT);

        return SUPPORTED_EXTENSIONS.stream()
                .anyMatch(lowerCaseFilename::endsWith);
    }

    private MediaType getContentType(String filename) {
        String lowerCaseFilename = filename.toLowerCase(Locale.ROOT);

        if (lowerCaseFilename.endsWith(".jpg")
                || lowerCaseFilename.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }

        if (lowerCaseFilename.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }

        if (lowerCaseFilename.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }

        if (lowerCaseFilename.endsWith(".bmp")) {
            return MediaType.parseMediaType("image/bmp");
        }

        if (lowerCaseFilename.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }

        if (lowerCaseFilename.endsWith(".avif")) {
            return MediaType.parseMediaType("image/avif");
        }

        throw notFound("Unsupported gallery image format");
    }

    private String buildImageUrl(
            String collectionId,
            String mediaItemId,
            String filename
    ) {
        return "/api/v1/collections/"
                + collectionId
                + "/videos/"
                + mediaItemId
                + "/gallery/"
                + encodeImageId(filename);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }

    private ResponseStatusException invalidPath() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid gallery path"
        );
    }

    private record GalleryContext(
            Path collectionRoot,
            Path movieFolder,
            Path galleryFolder
    ) {
    }

    public record GalleryImageFile(
            Resource resource,
            MediaType contentType,
            long sizeBytes,
            String filename
    ) {
    }
}
