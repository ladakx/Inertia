package com.ladakx.inertia.api;

import org.jetbrains.annotations.NotNull;

public interface InertiaApiProvider {
    @NotNull InertiaApi getApi();
}
