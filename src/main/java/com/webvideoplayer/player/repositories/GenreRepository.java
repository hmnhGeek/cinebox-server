
package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, String> {
    Optional<Genre> findByNormalizedName(String normalizedName);
}
