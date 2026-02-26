package com.ladakx.inertia.api.extension;

/**
 * A handle returned by {@link ExtensionRegistry#register} that can be used to unregister.
 */
public interface ExtensionHandle extends AutoCloseable {
    @Override
    void close();
}

