
package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, String> {
    Optional<Tag> findByNormalizedName(String normalizedName);
}
