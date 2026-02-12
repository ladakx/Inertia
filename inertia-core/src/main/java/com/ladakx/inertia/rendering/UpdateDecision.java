package com.ladakx.inertia.rendering;

record UpdateDecision(boolean positionSent, boolean transformMetadataSent, boolean transformMetadataForced) {
    static final UpdateDecision NONE = new UpdateDecision(false, false, false);
}

