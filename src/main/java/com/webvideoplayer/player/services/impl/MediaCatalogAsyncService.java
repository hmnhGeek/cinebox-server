package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.CatalogSyncProgress;
import com.webvideoplayer.player.enums.CollectionSyncStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaCatalogAsyncService {

    private final MediaCatalogSyncService mediaCatalogSyncService;

    private final Map<String, CatalogSyncProgress> progressByCollection =
            new ConcurrentHashMap<>();

    @Async("catalogSyncExecutor")
    public void synchronizeCollectionAsync(String collectionId) {
        progressByCollection.put(
                collectionId,
                new CatalogSyncProgress(
                        CollectionSyncStatus.QUEUED, 0, 0, 0
                )
        );

        try {
            progressByCollection.put(
                    collectionId,
                    new CatalogSyncProgress(
                            CollectionSyncStatus.IN_PROGRESS, 0, 0, 0
                    )
            );

            log.info("Starting catalog sync for collection {}", collectionId);

            mediaCatalogSyncService.synchronizeCollection(
                    collectionId,
                    (processed, total) -> {
                        int percentage = total == 0
                                ? 100
                                : (int) ((processed * 100L) / total);

                        progressByCollection.put(
                                collectionId,
                                new CatalogSyncProgress(
                                        CollectionSyncStatus.IN_PROGRESS,
                                        percentage,
                                        processed,
                                        total
                                )
                        );
                    }
            );

            CatalogSyncProgress current =
                    progressByCollection.get(collectionId);

            progressByCollection.put(
                    collectionId,
                    new CatalogSyncProgress(
                            CollectionSyncStatus.COMPLETED,
                            100,
                            current.total(),
                            current.total()
                    )
            );

            log.info("Catalog sync completed for collection {}", collectionId);

        } catch (IOException | RuntimeException e) {
            CatalogSyncProgress current =
                    progressByCollection.get(collectionId);

            int processed = current == null ? 0 : current.processed();
            int total = current == null ? 0 : current.total();
            int percentage = current == null ? 0 : current.percentage();

            progressByCollection.put(
                    collectionId,
                    new CatalogSyncProgress(
                            CollectionSyncStatus.FAILED,
                            percentage,
                            processed,
                            total
                    )
            );

            log.error("Catalog sync failed for collection {}", collectionId, e);
        }
    }

    public CatalogSyncProgress getProgress(String collectionId) {
        return progressByCollection.getOrDefault(
                collectionId,
                new CatalogSyncProgress(
                        CollectionSyncStatus.QUEUED, 0, 0, 0
                )
        );
    }

    public CollectionSyncStatus getSyncStatus(String collectionId) {
        return getProgress(collectionId).status();
    }
}