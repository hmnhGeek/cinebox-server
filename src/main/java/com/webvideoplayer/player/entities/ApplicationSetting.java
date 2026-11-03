
package com.webvideoplayer.player.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "application_settings")
@Getter
@Setter
@NoArgsConstructor
public class ApplicationSetting {

    @Id
    @Column(name = "setting_key", nullable = false, length = 150)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String value;

    @Column(name = "updated_at", nullable = false, length = 255)
    private String updatedAt;

    public ApplicationSetting(String key, String value) {
        this.key = key;
        this.value = value;
        this.updatedAt = LocalDateTime.now().toString();
    }
}
