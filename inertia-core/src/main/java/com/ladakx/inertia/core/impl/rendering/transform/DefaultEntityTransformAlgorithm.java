package com.ladakx.inertia.core.impl.rendering.transform;

import com.ladakx.inertia.api.rendering.transform.EntityTransformAlgorithm;
import com.ladakx.inertia.api.rendering.transform.EntityTransformContext;
import com.ladakx.inertia.api.rendering.transform.MutableEntityTransform;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DefaultEntityTransformAlgorithm implements EntityTransformAlgorithm {

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    @Override
    public void compute(@NotNull EntityTransformContext context, @NotNull MutableEntityTransform output) {
        Scratch scratch = SCRATCH.get();
        Vector3f localPos = scratch.localPos;
        Vector3f scratchVec = scratch.scratchVec;
        Quaternionf localRot = scratch.localRot;
        Quaternionf baseRot = context.baseRotation();

        // localRot = defRot * stackRot
        localRot.set(context.defaultLocalRotation()).mul(context.stackRotation());

        // localPos = (syncPosition ? defOffset : 0) + defRot * stackPos
        localPos.zero();
        if (context.syncPosition()) {
            localPos.add(context.defaultLocalOffset());
        }
        scratchVec.set(context.stackTranslation());
        context.defaultLocalRotation().transform(scratchVec);
        localPos.add(scratchVec);

        // worldPos = basePos + baseRot * localPos
        output.position().set(localPos);
        baseRot.transform(output.position());
        output.position().add(context.basePosition());

        // worldRot = (syncRotation ? baseRot : I) * localRot
        if (context.syncRotation()) {
            output.rotation().set(baseRot).mul(localRot);
        } else {
            output.rotation().set(localRot);
        }
    }

    private static final class Scratch {
        private final Vector3f localPos = new Vector3f();
        private final Vector3f scratchVec = new Vector3f();
        private final Quaternionf localRot = new Quaternionf();
    }
}
