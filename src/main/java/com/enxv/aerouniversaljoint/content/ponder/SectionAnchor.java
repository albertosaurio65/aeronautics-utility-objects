package com.enxv.aerouniversaljoint.content.ponder;

import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public record SectionAnchor(ElementLink<WorldSectionElement> section, Vec3 position, Vec3 rotationCenter) {
    public SectionAnchor(ElementLink<WorldSectionElement> section, Vec3 position) {
        this(section, position, Vec3.ZERO);
    }

    public Vec3 resolve(PonderScene scene) {
        WorldSectionElement element = scene.resolve(this.section);
        if (element == null) {
            return this.position;
        }

        Vec3 rotated = rotateAroundCenter(this.position, element.getAnimatedRotation(), this.rotationCenter);
        return rotated.add(element.getAnimatedOffset());
    }

    private static Vec3 rotateAroundCenter(Vec3 point, Vec3 rotationDegrees, Vec3 center) {
        if (rotationDegrees.equals(Vec3.ZERO)) {
            return point;
        }

        Vector3d local = new Vector3d(point.x - center.x, point.y - center.y, point.z - center.z);
        new Quaterniond()
                .rotateX(Math.toRadians(rotationDegrees.x))
                .rotateY(Math.toRadians(rotationDegrees.y))
                .rotateZ(Math.toRadians(rotationDegrees.z))
                .transform(local);
        return new Vec3(local.x + center.x, local.y + center.y, local.z + center.z);
    }
}
