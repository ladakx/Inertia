package com.ladakx.inertia.rendering.tracker.budget;

public record SheddingState(int midTeleportIntervalMultiplier,
                           int farTeleportIntervalMultiplier,
                           int metadataDropModulo) {
    public static SheddingState disabled() {
        return new SheddingState(1, 1, 1);
    }

    public static SheddingState of(int intensity) {
        return switch (intensity) {
            case 0 -> disabled();
            case 1 -> new SheddingState(2, 2, 2);
            case 2 -> new SheddingState(2, 3, 3);
            case 3 -> new SheddingState(3, 4, 4);
            default -> new SheddingState(4, 6, 6);
        };
    }
}
