package com.webvideoplayer.player.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "playback_progress",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_playback_progress_media_item",
                        columnNames = "media_item_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_playback_progress_last_played",
                        columnList = "last_played_at"
                )
        }
)
public class PlaybackProgress {

    @Id
    @GeneratedValue
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

    @Column(name = "position_seconds", nullable = false)
    private Long positionSeconds = 0L;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "progress_percent", nullable = false)
    private Double progressPercent = 0.0;

    @Column(name = "completed", nullable = false)
    private boolean completed = false;

    @Column(name = "last_played_at", nullable = false)
    private Instant lastPlayedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (lastPlayedAt == null) {
            lastPlayedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}