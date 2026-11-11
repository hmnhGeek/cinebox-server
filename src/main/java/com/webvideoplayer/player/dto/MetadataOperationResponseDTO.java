package com.webvideoplayer.player.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetadataOperationResponseDTO {
    private VideoMetadata metadata;
    private NfoSyncStatusDTO nfoStatus;
}