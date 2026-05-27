package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.ModBlocks;
import com.enxv.aerouniversaljoint.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class DampingStressBearingMenu extends AbstractContainerMenu implements ContainerData {
    private final ContainerLevelAccess access;
    private final BlockPos blockPos;
    @Nullable
    private final DampingStressBearingBlockEntity blockEntity;

    public DampingStressBearingMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    public DampingStressBearingMenu(int containerId, Inventory inventory, DampingStressBearingBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity.getBlockPos(), blockEntity);
    }

    private DampingStressBearingMenu(int containerId, Inventory inventory, BlockPos blockPos) {
        this(containerId, inventory, blockPos, resolveBlockEntity(inventory, blockPos));
    }

    private DampingStressBearingMenu(int containerId, Inventory inventory, BlockPos blockPos,
                                     @Nullable DampingStressBearingBlockEntity blockEntity) {
        super(ModMenuTypes.DAMPING_STRESS_BEARING.get(), containerId);
        this.blockPos = blockPos.immutable();
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(inventory.player.level(), this.blockPos);
        this.addDataSlots(this);
    }

    @Nullable
    private static DampingStressBearingBlockEntity resolveBlockEntity(Inventory inventory, BlockPos blockPos) {
        return inventory.player.level().getBlockEntity(blockPos) instanceof DampingStressBearingBlockEntity be ? be : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return this.stillValid(player)
                && this.blockEntity != null
                && this.blockEntity.setResistanceValueByIndex(id);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.DAMPING_STRESS_BEARING.get());
    }

    @Override
    public int get(int index) {
        return index == 0 && this.blockEntity != null ? this.blockEntity.getResistanceValue() : 0;
    }

    @Override
    public void set(int index, int value) {
        if (index == 0 && this.blockEntity != null) {
            this.blockEntity.applySyncedResistanceValue(value);
        }
    }

    @Override
    public int getCount() {
        return 1;
    }

    public int getCurrentResistanceValue() {
        return this.get(0);
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }
}
