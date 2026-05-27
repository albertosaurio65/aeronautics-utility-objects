package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.client.AeroUniversalJointPartials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class DampingStressBearingRenderer extends KineticBlockEntityRenderer<DampingStressBearingBlockEntity> {
    public DampingStressBearingRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(DampingStressBearingBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(SwivelBearingBlock.FACING);
        Direction.Axis axis = ((IRotate) state.getBlock()).getRotationAxis(state);
        VertexConsumer cutoutBuffer = buffer.getBuffer(RenderType.cutoutMipped());
        KineticBlockEntity headKinetics = be.isAssembled() ? be.getOutputKinetics() : be;
        
        // Calculate head angle
        float headAngle;
        if (be.isAssembled()) {
            // When assembled, use actual rotation angle
            Float assembledAngle = computeAssembledHeadAngle(be, partialTicks);
            if (assembledAngle != null) {
                headAngle = assembledAngle;
            } else {
                headAngle = getAngleForBe(headKinetics, be.getBlockPos(), axis);
            }
        } else {
            // When not assembled, head should align with base (angle = 0)
            headAngle = 0.0f;
        }

        SuperByteBuffer head = kineticRotationTransform(
                CachedBuffers.partialFacingVertical(AeroUniversalJointPartials.DAMPING_STRESS_BEARING_HEAD, state, facing),
                be,
                axis,
                headAngle,
                light);
        head.renderInto(ms, cutoutBuffer);

        renderRotatingBuffer(be.isAssembled() ? be.getOutputKinetics() : be,
                CachedBuffers.partialFacing(SimPartialModels.SHAFT_SIXTEENTH, state, facing.getOpposite()),
                ms, cutoutBuffer, light);
        if (!be.isAssembled()) {
            renderRotatingBuffer(be, CachedBuffers.partialFacing(SimPartialModels.SHAFT_SIXTEENTH, state, facing), ms, cutoutBuffer, light);
        }
    }

    @Override
    protected SuperByteBuffer getRotatedModel(DampingStressBearingBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(SimPartialModels.SHAFT_SIXTEENTH, state, state.getValue(SwivelBearingBlock.FACING).getOpposite());
    }

    @Nullable
    private static Float computeAssembledHeadAngle(DampingStressBearingBlockEntity be, float partialTicks) {
        if (!be.isAssembled() || be.getLevel() == null || be.getSubLevelID() == null) {
            return null;
        }

        SubLevel attached = SubLevelContainer.getContainer(be.getLevel()).getSubLevel(be.getSubLevelID());
        if (attached == null) {
            return null;
        }

        Pose3dc attachedPose = getRenderPose(attached, partialTicks);
        Pose3dc containingPose = getRenderPose(Sable.HELPER.getContaining(be), partialTicks);
        double angleDegrees = DampingStressBearingAngleHelper.extractRelativeAngleDegrees(
                be.getBlockState().getValue(SwivelBearingBlock.FACING),
                DampingStressBearingAngleHelper.resolvePlateFacing(be),
                containingPose,
                attachedPose);
        if (be.getBlockState().getValue(SwivelBearingBlock.FACING).getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            angleDegrees = -angleDegrees;
        }
        return (float) Math.toRadians(angleDegrees);
    }

    @Nullable
    private static Pose3dc getRenderPose(@Nullable SubLevel subLevel, float partialTicks) {
        if (subLevel == null) {
            return null;
        }
        if (subLevel instanceof ClientSubLevel clientSubLevel) {
            return clientSubLevel.renderPose(partialTicks);
        }
        return subLevel.logicalPose();
    }
}
