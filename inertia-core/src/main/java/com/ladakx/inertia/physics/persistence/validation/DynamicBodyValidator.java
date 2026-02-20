package com.ladakx.inertia.physics.persistence.validation;

import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.physics.persistence.storage.DynamicBodyStorageRecord;

import java.util.Objects;

public final class DynamicBodyValidator {

    private final ConfigurationService configurationService;

    public DynamicBodyValidator(ConfigurationService configurationService) {
        this.configurationService = Objects.requireNonNull(configurationService, "configurationService");
    }

    public boolean isValid(DynamicBodyStorageRecord record) {
        Objects.requireNonNull(record, "record");
        if (!configurationService.getWorldsConfig().getAllWorlds().containsKey(record.world())) {
            return false;
        }
        return configurationService.getPhysicsBodyRegistry().find(record.bodyId()).isPresent();
    }
}
