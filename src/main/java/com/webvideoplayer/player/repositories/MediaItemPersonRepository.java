
package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.MediaItemPerson;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaItemPersonRepository
        extends JpaRepository<MediaItemPerson, String> {

    void deleteByMediaItem_Id(String mediaItemId);
}
