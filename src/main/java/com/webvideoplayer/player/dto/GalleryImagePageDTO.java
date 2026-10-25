
package com.webvideoplayer.player.dto;

import java.util.List;

public record GalleryImagePageDTO(
        int page,
        int size,
        long totalImages,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious,
        List<GalleryImageDTO> images
) {

    public GalleryImagePageDTO {
        images = List.copyOf(images);
    }

    public record GalleryImageDTO(
            String id,
            String filename,
            String contentType,
            long sizeBytes,
            String url
    ) {
    }
}
