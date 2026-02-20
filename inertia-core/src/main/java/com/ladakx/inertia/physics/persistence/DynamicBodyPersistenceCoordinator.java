package com.ladakx.inertia.physics.persistence;

import com.ladakx.inertia.physics.persistence.runtime.DynamicBodyRuntimeLoader;
import com.ladakx.inertia.physics.persistence.storage.DynamicBodyStorage;
import com.ladakx.inertia.physics.persistence.storage.DynamicBodyStorageFile;

import java.nio.file.Path;
import java.util.Objects;

public final class DynamicBodyPersistenceCoordinator {

    private final DynamicBodyStorage storage;
    private final DynamicBodyRuntimeLoader runtimeLoader;
    private final DynamicBodyStorageFile storageFile;

    public DynamicBodyPersistenceCoordinator(DynamicBodyStorage storage,
                                             DynamicBodyRuntimeLoader runtimeLoader,
                                             Path storagePath) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.runtimeLoader = Objects.requireNonNull(runtimeLoader, "runtimeLoader");
        this.storageFile = new DynamicBodyStorageFile(Objects.requireNonNull(storagePath, "storagePath"));
    }

    public void saveNow() {
        storageFile.write(storage.snapshot());
    }

    public void loadAsync() {
        runtimeLoader.loadAsync();
    }

    public void onChunkLoad(String world, int chunkX, int chunkZ) {
        Objects.requireNonNull(world, "world");
        runtimeLoader.onChunkLoaded(world, chunkX, chunkZ);
    }

    public void shutdown() {
        runtimeLoader.clear();
    }
}
