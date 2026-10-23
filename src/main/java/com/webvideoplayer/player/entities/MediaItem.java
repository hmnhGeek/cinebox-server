
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
        name = "media_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_media_item_collection_path",
                        columnNames = {"collection_id", "relative_path"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_media_item_collection",
                        columnList = "collection_id"
                )
        }
)
public class MediaItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "relative_path", nullable = false)
    private String relativePath;

    @Column(nullable = false)
    private String title;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "last_modified_at")
    private Instant lastModifiedAt;

    @Column(name = "last_scanned_at")
    private Instant lastScannedAt;
}
