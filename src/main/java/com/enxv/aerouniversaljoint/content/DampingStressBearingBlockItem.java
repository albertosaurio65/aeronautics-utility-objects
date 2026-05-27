package com.enxv.aerouniversaljoint.content;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class DampingStressBearingBlockItem extends BlockItem {
    public DampingStressBearingBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        tooltipComponents.add(Component.translatable("tooltip.aeronautics_utility_objects.damping_bearing_hint").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_utility_objects.damping_bearing_cycle").withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_utility_objects.damping_bearing_stress").withStyle(ChatFormatting.DARK_GRAY));
    }
}
