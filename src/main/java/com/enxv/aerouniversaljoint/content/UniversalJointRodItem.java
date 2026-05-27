package com.enxv.aerouniversaljoint.content;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class UniversalJointRodItem extends Item {
    private final boolean brass;

    public UniversalJointRodItem(Properties properties, boolean brass) {
        super(properties);
        this.brass = brass;
    }

    public boolean isBrass() {
        return this.brass;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();

        if (player != null && player.isShiftKeyDown() && PendingRodSelections.read(player).isPresent()) {
            if (!level.isClientSide) {
                PendingRodSelections.clear(player);
                player.displayClientMessage(Component.translatable("message.aeronautics_utility_objects.selection_cleared"), true);
            } else {
                PendingRodSelections.clearClient(player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        tooltipComponents.add(Component.translatable("tooltip.aeronautics_utility_objects.rod_hint").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(this.brass
                ? "tooltip.aeronautics_utility_objects.brass_rod_hint"
                : "tooltip.aeronautics_utility_objects.andesite_rod_hint").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_utility_objects.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable(this.brass
                        ? "tooltip.aeronautics_utility_objects.rod_range_brass"
                        : "tooltip.aeronautics_utility_objects.rod_range_andesite")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}

