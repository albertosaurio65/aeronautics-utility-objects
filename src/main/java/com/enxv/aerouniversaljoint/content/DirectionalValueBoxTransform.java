package com.enxv.aerouniversaljoint.content;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class DirectionalValueBoxTransform extends ValueBoxTransform {
    private static final double FACE_OFFSET = 7.5D / 16.0D;
    private static final double SLOT_OFFSET = 3.5D / 16.0D;

    private final int slot;

    public DirectionalValueBoxTransform(int slot) {
        this.slot = slot;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction face = getFacing(state);
        Vec3 normal = axis(face);
        Vec3 horizontal = tangentHorizontal(face);
        Vec3 vertical = tangentVertical(face);
        double horizontalOffset = (this.slot & 1) == 0 ? -SLOT_OFFSET : SLOT_OFFSET;
        double verticalOffset = (this.slot & 2) == 0 ? SLOT_OFFSET : -SLOT_OFFSET;

        return new Vec3(0.5D, 0.5D, 0.5D)
                .add(normal.scale(FACE_OFFSET))
                .add(horizontal.scale(horizontalOffset))
                .add(vertical.scale(verticalOffset));
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack poseStack) {
        Direction face = getFacing(state);
        float yRot = AngleHelper.horizontalAngle(face) + 180.0F;
        float xRot = face == Direction.UP ? 90.0F : face == Direction.DOWN ? 270.0F : 0.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot));
    }

    @Override
    public float getScale() {
        return 0.4F;
    }

    private static Direction getFacing(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.UP;
    }

    private static Vec3 tangentHorizontal(Direction face) {
        return switch (face.getAxis()) {
            case X -> axis(Direction.SOUTH);
            case Y, Z -> axis(Direction.EAST);
        };
    }

    private static Vec3 tangentVertical(Direction face) {
        return switch (face.getAxis()) {
            case Y -> axis(Direction.SOUTH);
            case X, Z -> axis(Direction.UP);
        };
    }

    private static Vec3 axis(Direction direction) {
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }
}
