package com.webvideoplayer.player.utils;

import com.webvideoplayer.player.dto.CatalogSyncProgress;
import com.webvideoplayer.player.enums.CollectionSyncStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class CatalogSyncProgressTracker {

    private final ConcurrentMap<String, CatalogSyncProgress> progress =
            new ConcurrentHashMap<>();

    public void queued(String collectionId) {
        progress.put(collectionId, new CatalogSyncProgress(
                CollectionSyncStatus.QUEUED, 0, 0, 0
        ));
    }

    public void started(String collectionId) {
        progress.put(collectionId, new CatalogSyncProgress(
                CollectionSyncStatus.IN_PROGRESS, 0, 0, 0
        ));
    }

    public void update(
            String collectionId,
            int processed,
            int total
    ) {
        int percentage = total <= 0
                ? 0
                : Math.min(99, (int) ((long) processed * 100 / total));

        progress.put(collectionId, new CatalogSyncProgress(
                CollectionSyncStatus.IN_PROGRESS,
                percentage,
                processed,
                total
        ));
    }

    public void completed(String collectionId, int total) {
        progress.put(collectionId, new CatalogSyncProgress(
                CollectionSyncStatus.COMPLETED, 100, total, total
        ));
    }

    public void failed(String collectionId) {
        CatalogSyncProgress current = progress.get(collectionId);

        progress.put(collectionId, new CatalogSyncProgress(
                CollectionSyncStatus.FAILED,
                current == null ? 0 : current.percentage(),
                current == null ? 0 : current.processed(),
                current == null ? 0 : current.total()
        ));
    }

    public CatalogSyncProgress getStatus(String collectionId) {
        return progress.getOrDefault(
                collectionId,
                new CatalogSyncProgress(
                        CollectionSyncStatus.COMPLETED,
                        100, 0, 0
                )
        );
    }
}
