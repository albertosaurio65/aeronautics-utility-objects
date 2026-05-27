package com.enxv.aerouniversaljoint.mixin;

import com.enxv.aerouniversaljoint.access.SwivelBearingPlateParentAccess;
import dev.simulated_team.simulated.index.SimBlocks;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderDispatcher.class)
public class BlockRenderDispatcherMixin {
    @Inject(method = "renderBatched", at = @At("HEAD"), cancellable = true)
    private void aeronautics$hideDampingBearingPlate(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack,
                                                     VertexConsumer consumer, boolean checkSides, RandomSource random, CallbackInfo ci) {
        if (this.aeronautics$shouldHide(state, pos, level)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBreakingTexture", at = @At("HEAD"), cancellable = true)
    private void aeronautics$hideDampingBearingPlateBreaking(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack,
                                                             VertexConsumer consumer, CallbackInfo ci) {
        if (this.aeronautics$shouldHide(state, pos, level)) {
            ci.cancel();
        }
    }

    private boolean aeronautics$shouldHide(BlockState state, BlockPos pos, BlockAndTintGetter level) {
        if (!state.is(SimBlocks.SWIVEL_BEARING_LINK_BLOCK)) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SwivelBearingPlateParentAccess access)) {
            return false;
        }

        Level actualLevel = blockEntity.getLevel();
        if (actualLevel == null) {
            return false;
        }

        return access.aeronautics$isDampingStressBearingParent(actualLevel);
    }
}
