package com.enxv.aerouniversaljoint.content;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class HydraulicRegulatorRenderer extends KineticBlockEntityRenderer<HydraulicRegulatorBlockEntity> {
    public HydraulicRegulatorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(HydraulicRegulatorBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        BlockState state = be.getBlockState();
        VertexConsumer cutoutBuffer = buffer.getBuffer(RenderType.cutoutMipped());
        Direction.Axis axis = HydraulicRegulatorBlock.getStressAxis(state);
        renderShaftHalf(be, state, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), ms, cutoutBuffer, light);
        renderShaftHalf(be, state, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE), ms, cutoutBuffer, light);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(HydraulicRegulatorBlockEntity be, BlockState state) {
        return CachedBuffers.partial(AllPartialModels.SHAFT_HALF, KineticBlockEntityRenderer.shaft(HydraulicRegulatorBlock.getStressAxis(state)));
    }

    private void renderShaftHalf(HydraulicRegulatorBlockEntity be, BlockState state, Direction facing,
                                 PoseStack ms, VertexConsumer buffer, int light) {
        renderRotatingBuffer(be, CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, facing), ms, buffer, light);
    }
}
