package com.ladakx.inertia.api;

import java.util.Objects;

public final class InertiaApiUnavailableException extends RuntimeException {
    public InertiaApiUnavailableException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }
}
