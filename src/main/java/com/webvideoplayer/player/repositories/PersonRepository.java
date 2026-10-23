
package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, String> {
    Optional<Person> findByNormalizedName(String normalizedName);
}
