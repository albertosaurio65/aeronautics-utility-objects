package com.enxv.aerouniversaljoint.content;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

final class DampingStressBearingAngleHelper {
    private static final Vector3d ANGLE_EXTRACTION_AXIS = new Vector3d(0.0D, 1.0D, 0.0D);

    private DampingStressBearingAngleHelper() {
    }

    static Direction resolvePlateFacing(DampingStressBearingBlockEntity bearing) {
        if (bearing.getPlatePos() != null && bearing.getLevel() != null) {
            BlockState plateState = bearing.getLevel().getBlockState(bearing.getPlatePos());
            if (plateState.hasProperty(SwivelBearingPlateBlock.FACING)) {
                return plateState.getValue(SwivelBearingPlateBlock.FACING);
            }
        }

        return bearing.getBlockState().getValue(SwivelBearingBlock.FACING);
    }

    static double extractRelativeAngleDegrees(Direction baseFacing, Direction plateFacing, @Nullable Pose3dc containingPose, Pose3dc attachedPose) {
        Quaterniond orientationA = containingPose != null
                ? new Quaterniond(containingPose.orientation())
                : new Quaterniond();
        Quaterniond blockOrientationA = new Quaterniond(baseFacing.getRotation());
        Quaterniond orientationB = new Quaterniond(attachedPose.orientation());
        Quaterniond blockOrientationB = new Quaterniond(plateFacing.getRotation());

        Quaterniond localB = new Quaterniond(orientationA)
                .mul(blockOrientationA)
                .conjugate()
                .mul(orientationB.mul(blockOrientationB));

        double d = ANGLE_EXTRACTION_AXIS.dot(localB.x(), localB.y(), localB.z());
        return -2.0D * Math.toDegrees(Math.atan2(-d, localB.w()));
    }
}
