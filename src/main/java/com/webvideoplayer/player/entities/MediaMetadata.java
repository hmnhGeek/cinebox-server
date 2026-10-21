package com.webvideoplayer.player.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "media_metadata")
public class MediaMetadata {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_item_id", nullable = false, unique = true)
    private MediaItem mediaItem;

    private String originalTitle;
    private String sortTitle;

    @Column(length = 10000)
    private String plot;

    @Column(length = 4000)
    private String outline;

    private Integer year;
    private String premiered;
    private String releaseDate;
    private String country;
    private String originalCountry;
    private String language;
    private String mpaa;
    private String website;

    @ManyToMany
    @JoinTable(
            name = "media_metadata_genres",
            joinColumns = @JoinColumn(name = "metadata_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"),
            uniqueConstraints = @UniqueConstraint(
                    columnNames = {"metadata_id", "genre_id"}
            )
    )
    private Set<Genre> genres = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "media_metadata_studios",
            joinColumns = @JoinColumn(name = "metadata_id"),
            inverseJoinColumns = @JoinColumn(name = "studio_id"),
            uniqueConstraints = @UniqueConstraint(
                    columnNames = {"metadata_id", "studio_id"}
            )
    )
    private Set<Studio> studios = new HashSet<>();

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;
}