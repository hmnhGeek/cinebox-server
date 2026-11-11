package com.webvideoplayer.player.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class NfoSyncStatusDTO {

    private String mediaItemId;

    /*
     * SYNCED, PENDING, FAILED, or MISSING.
     */
    private String status;

    private String errorMessage;

    private Instant lastAttemptAt;

    private Instant lastSyncedAt;
}