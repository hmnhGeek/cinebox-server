package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.ApplicationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationSettingRepository
        extends JpaRepository<ApplicationSetting, String> {
}