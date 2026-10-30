
package com.webvideoplayer.player.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "person_profiles")
public class PersonProfile {

    @Id
    @Column(name = "person_id", nullable = false)
    private String personId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "hero_image_path")
    private String heroImagePath;

    @Column(name = "profile_image_path")
    private String profileImagePath;

    @Column(name = "biography", columnDefinition = "TEXT")
    private String biography;
}
