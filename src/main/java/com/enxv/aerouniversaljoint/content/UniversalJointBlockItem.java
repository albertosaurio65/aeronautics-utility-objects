package com.enxv.aerouniversaljoint.content;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class UniversalJointBlockItem extends BlockItem {
    public UniversalJointBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        tooltipComponents.add(Component.translatable("tooltip.aeronautics_utility_objects.head_hint").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_utility_objects.head_rod_hint")
                .withStyle(ChatFormatting.GRAY));
    }
}

