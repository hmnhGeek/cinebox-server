
package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.MediaItemPerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaItemPersonRepository
        extends JpaRepository<MediaItemPerson, String> {

    void deleteByMediaItem_Id(String mediaItemId);

    List<MediaItemPerson> findByMediaItem_Id(String mediaItemId);
}
