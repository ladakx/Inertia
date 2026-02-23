package com.ladakx.inertia.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ApiResult<T> {
    private final boolean success;
    private final T value;
    private final ApiErrorCode errorCode;
    private final String messageKey;

    private ApiResult(boolean success, @Nullable T value, @Nullable ApiErrorCode errorCode, @Nullable String messageKey) {
        this.success = success;
        this.value = value;
        this.errorCode = errorCode;
        this.messageKey = messageKey;
    }

    public static <T> @NotNull ApiResult<T> success(@NotNull T value) {
        return new ApiResult<>(true, Objects.requireNonNull(value, "value"), null, null);
    }

    public static <T> @NotNull ApiResult<T> failure(@NotNull ApiErrorCode errorCode, @NotNull String messageKey) {
        return new ApiResult<>(false, null, Objects.requireNonNull(errorCode, "errorCode"), Objects.requireNonNull(messageKey, "messageKey"));
    }

    public boolean isSuccess() {
        return success;
    }

    public @Nullable T getValue() {
        return value;
    }

    public @Nullable ApiErrorCode getErrorCode() {
        return errorCode;
    }

    public @Nullable String getMessageKey() {
        return messageKey;
    }
}
