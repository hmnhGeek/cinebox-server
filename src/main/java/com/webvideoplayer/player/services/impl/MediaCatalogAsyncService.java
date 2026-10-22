
package com.webvideoplayer.player.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaCatalogAsyncService {

    private final MediaCatalogSyncService mediaCatalogSyncService;

    @Async("catalogSyncExecutor")
    public void synchronizeCollectionAsync(String collectionId) {
        try {
            log.info(
                    "Starting catalog sync for collection {}",
                    collectionId
            );

            mediaCatalogSyncService.synchronizeCollection(collectionId);

            log.info(
                    "Catalog sync completed for collection {}",
                    collectionId
            );

        } catch (IOException | RuntimeException e) {
            log.error(
                    "Catalog sync failed for collection {}",
                    collectionId,
                    e
            );
        }
    }
}
