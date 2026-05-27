package com.enxv.aerouniversaljoint.content;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

final class WrenchRemovalHelper {
    private WrenchRemovalHelper() {
    }

    static InteractionResult removeWithDrops(BlockState state, UseOnContext context, Runnable beforeRemove) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
        Event posted = NeoForge.EVENT_BUS.post(event);
        if (posted instanceof BlockEvent.BreakEvent breakEvent && breakEvent.isCanceled()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (player != null && !player.isCreative()) {
            List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, player, context.getItemInHand());
            drops.forEach(stack -> player.getInventory().placeItemBackInInventory(stack));
        }

        beforeRemove.run();
        state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, true);
        level.destroyBlock(pos, false);
        IWrenchable.playRemoveSound(level, pos);
        return InteractionResult.SUCCESS;
    }
}
