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
        name = "media_item_nfo_status",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_media_item_nfo_status",
                columnNames = "media_item_id"
        )
)
public class MediaItemNfoStatus {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "media_item_id",
            nullable = false,
            unique = true
    )
    private MediaItem mediaItem;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 2000)
    private String errorMessage;

    @Column
    private Instant lastAttemptAt;

    @Column
    private Instant lastSyncedAt;
}