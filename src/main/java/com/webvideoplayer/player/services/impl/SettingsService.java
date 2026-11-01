
package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.ApplicationSettingsDTO;
import com.webvideoplayer.player.dto.UpdateSettingsRequest;
import com.webvideoplayer.player.entities.ApplicationSetting;
import com.webvideoplayer.player.enums.ApplicationSettingKey;
import com.webvideoplayer.player.repositories.ApplicationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingsService {

    private final ApplicationSettingRepository settingsRepository;

    private static final Map<String, ApplicationSettingKey> SUPPORTED_SETTINGS =
            Arrays.stream(ApplicationSettingKey.values())
                    .collect(Collectors.toUnmodifiableMap(
                            ApplicationSettingKey::getKey,
                            Function.identity()
                    ));

    public ApplicationSettingsDTO getSettings() {

        Map<String, Boolean> effectiveSettings = loadEffectiveSettings();

        return ApplicationSettingsDTO.from(effectiveSettings);
    }

    @Transactional
    public ApplicationSettingsDTO updateSettings(
            UpdateSettingsRequest request
    ) {

        validateRequest(request);

        Map<String, Boolean> updates = validateAndConvertSettings(
                request.settings()
        );

        if (updates.isEmpty()) {
            return getSettings();
        }

        List<ApplicationSetting> existingSettings =
                settingsRepository.findAllById(updates.keySet());

        Map<String, ApplicationSetting> existingSettingsByKey =
                existingSettings.stream()
                        .collect(Collectors.toMap(
                                ApplicationSetting::getKey,
                                Function.identity()
                        ));

        String updatedAt = LocalDateTime.now().toString();

        List<ApplicationSetting> settingsToSave = updates.entrySet()
                .stream()
                .map(entry -> {

                    String key = entry.getKey();
                    String value = Boolean.toString(entry.getValue());

                    ApplicationSetting setting =
                            existingSettingsByKey.get(key);

                    if (setting == null) {
                        return new ApplicationSetting(key, value);
                    }

                    setting.setValue(value);
                    setting.setUpdatedAt(updatedAt);

                    return setting;
                })
                .toList();

        settingsRepository.saveAll(settingsToSave);

        return getSettings();
    }

    private Map<String, Boolean> loadEffectiveSettings() {

        Map<String, Boolean> effectiveSettings = new HashMap<>();

        // Load defaults from the central settings registry.
        for (ApplicationSettingKey settingKey :
                ApplicationSettingKey.values()) {

            effectiveSettings.put(
                    settingKey.getKey(),
                    settingKey.getDefaultValue()
            );
        }

        // Override defaults with persisted database values.
        List<ApplicationSetting> persistedSettings =
                settingsRepository.findAll();

        for (ApplicationSetting setting : persistedSettings) {

            ApplicationSettingKey supportedSetting =
                    SUPPORTED_SETTINGS.get(setting.getKey());

            // Ignore database entries for settings no longer supported.
            if (supportedSetting == null) {
                continue;
            }

            effectiveSettings.put(
                    setting.getKey(),
                    parseBooleanValue(setting)
            );
        }

        return effectiveSettings;
    }

    private Map<String, Boolean> validateAndConvertSettings(
            Map<String, Object> requestedSettings
    ) {

        Map<String, Boolean> validatedSettings = new HashMap<>();

        for (Map.Entry<String, Object> entry :
                requestedSettings.entrySet()) {

            String key = entry.getKey();
            Object value = entry.getValue();

            if (key == null || !SUPPORTED_SETTINGS.containsKey(key)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported application setting: " + key
                );
            }

            if (!(value instanceof Boolean booleanValue)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid value for setting '" + key
                                + "'. Expected a boolean value."
                );
            }

            validatedSettings.put(key, booleanValue);
        }

        return validatedSettings;
    }

    private void validateRequest(UpdateSettingsRequest request) {

        if (request == null || request.settings() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Settings request cannot be empty."
            );
        }

        if (request.settings().isEmpty()) {
            return;
        }

        Set<String> nullKeys = request.settings()
                .keySet()
                .stream()
                .filter(key -> key == null || key.isBlank())
                .collect(Collectors.toSet());

        if (!nullKeys.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Setting keys cannot be null or blank."
            );
        }
    }

    private boolean parseBooleanValue(ApplicationSetting setting) {

        String value = setting.getValue();

        if ("true".equalsIgnoreCase(value)) {
            return true;
        }

        if ("false".equalsIgnoreCase(value)) {
            return false;
        }

        throw new IllegalStateException(
                "Invalid persisted value for application setting: "
                        + setting.getKey()
        );
    }
}
