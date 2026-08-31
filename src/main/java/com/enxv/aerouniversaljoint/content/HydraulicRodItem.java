package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.network.SyncHydraulicSelectionPayload;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class HydraulicRodItem extends Item {
    private final boolean creative;
    private final boolean giant;

    public HydraulicRodItem(Properties properties) {
        this(properties, false, false);
    }

    public HydraulicRodItem(Properties properties, boolean creative) {
        this(properties, creative, false);
    }

    public HydraulicRodItem(Properties properties, boolean creative, boolean giant) {
        super(properties);
        this.creative = creative;
        this.giant = giant;
    }

    public boolean isCreative() {
        return this.creative;
    }

    public boolean isGiant() {
        return this.giant;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();

        if (player != null && player.isShiftKeyDown() && PendingHydraulicSelections.read(player).isPresent()) {
            if (!level.isClientSide) {
                PendingHydraulicSelections.clear(player);
                if (player instanceof ServerPlayer serverPlayer) {
                    SyncHydraulicSelectionPayload.send(serverPlayer, null);
                }
                player.displayClientMessage(Component.translatable("message.aeronautics_utility_objects.selection_cleared"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        tooltipComponents.add(Component.translatable("tooltip.aeronautics_utility_objects.hydraulic_rod_hint")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_utility_objects.same_type_hint")
                .withStyle(ChatFormatting.GRAY));
        if (this.creative) {
            tooltipComponents.add(Component.translatable("tooltip.aeronautics_utility_objects.creative_hydraulic_rod_acceleration")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_utility_objects.clear_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_utility_objects.hydraulic_rod_range")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
