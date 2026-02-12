package com.ladakx.inertia.rendering.tracker.state;

public record UpdateDecision(boolean positionSent, boolean transformMetadataSent, boolean transformMetadataForced) {
    public static final UpdateDecision NONE = new UpdateDecision(false, false, false);
}
