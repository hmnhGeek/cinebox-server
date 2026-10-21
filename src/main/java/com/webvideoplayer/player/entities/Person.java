package com.webvideoplayer.player.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "people",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_person_normalized_name",
                columnNames = "normalized_name"
        )
)
public class Person {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;
}