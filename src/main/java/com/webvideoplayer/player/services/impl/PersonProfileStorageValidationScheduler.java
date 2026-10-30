
package com.webvideoplayer.player.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PersonProfileStorageValidationScheduler {

    private final PersonProfileStorageValidationService validationService;

    @Scheduled(
            initialDelayString =
                    "${cinebox.person-profiles.validation.initial-delay-ms:30000}",
            fixedDelayString =
                    "${cinebox.person-profiles.validation.fixed-delay-ms:21600000}"
    )
    public void validatePersonProfileStorage() {
        log.debug("Starting scheduled person profile storage validation.");
        validationService.validateStorage();
    }
}
