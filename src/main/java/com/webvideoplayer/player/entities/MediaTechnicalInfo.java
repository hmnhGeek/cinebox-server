package com.webvideoplayer.player.entities;

import com.webvideoplayer.player.enums.TechnicalInfoStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(
        name = "media_technical_info",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_media_technical_info_media_item",
                        columnNames = "media_item_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MediaTechnicalInfo {

    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "media_item_id",
            nullable = false,
            unique = true
    )
    private MediaItem mediaItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TechnicalInfoStatus status;

    @Column(name = "resolution_label", length = 20)
    private String resolutionLabel;

    private Integer width;

    private Integer height;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "video_codec", length = 50)
    private String videoCodec;

    @Column(name = "frame_rate")
    private Double frameRate;

    @Column(name = "video_bitrate")
    private Long videoBitrate;

    @Column(name = "audio_codec", length = 50)
    private String audioCodec;

    @Column(name = "audio_channels")
    private Integer audioChannels;

    @Column(name = "audio_channel_layout", length = 100)
    private String audioChannelLayout;

    @Column(name = "source_file_size_bytes")
    private Long sourceFileSizeBytes;

    @Column(name = "source_last_modified_at")
    private Instant sourceLastModifiedAt;

    @Column(name = "extracted_at")
    private Instant extractedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}