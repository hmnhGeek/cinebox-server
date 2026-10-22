
package com.webvideoplayer.player.dto;

import com.webvideoplayer.player.enums.CollectionSyncStatus;

public record CatalogSyncProgress(
        CollectionSyncStatus status,
        int percentage,
        int processed,
        int total
) {
}
