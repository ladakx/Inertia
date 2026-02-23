package com.ladakx.inertia.api;

import org.jetbrains.annotations.NotNull;

public interface InertiaApiProvider {
    @NotNull InertiaAPI getApi();
}
