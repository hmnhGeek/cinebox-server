
package com.webvideoplayer.player;

import com.webvideoplayer.player.config.PersonProfileStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PersonProfileStorageProperties.class)
public class CineBoxApplication {

	public static void main(String[] args) {
		SpringApplication.run(CineBoxApplication.class, args);
	}
}
