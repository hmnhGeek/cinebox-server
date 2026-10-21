
package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.Studio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StudioRepository extends JpaRepository<Studio, String> {
    Optional<Studio> findByNormalizedName(String normalizedName);
}
