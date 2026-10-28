
package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.PersonProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonProfileRepository
        extends JpaRepository<PersonProfile, String> {
}
