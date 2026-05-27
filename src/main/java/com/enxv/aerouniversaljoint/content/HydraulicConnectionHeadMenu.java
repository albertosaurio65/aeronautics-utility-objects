package com.enxv.aerouniversaljoint.content;

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

public class HydraulicConnectionHeadMenu extends AbstractContainerMenu implements ContainerData {
    private final ContainerLevelAccess access;
    private final BlockPos blockPos;
    @Nullable
    private final HydraulicConnectionHeadBlockEntity blockEntity;

    public HydraulicConnectionHeadMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    public HydraulicConnectionHeadMenu(int containerId, Inventory inventory, HydraulicConnectionHeadBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity.getBlockPos(), blockEntity);
    }

    private HydraulicConnectionHeadMenu(int containerId, Inventory inventory, BlockPos blockPos) {
        this(containerId, inventory, blockPos, resolveBlockEntity(inventory, blockPos));
    }

    private HydraulicConnectionHeadMenu(int containerId, Inventory inventory, BlockPos blockPos,
                                        @Nullable HydraulicConnectionHeadBlockEntity blockEntity) {
        super(ModMenuTypes.HYDRAULIC_CONNECTION_HEAD.get(), containerId);
        this.blockPos = blockPos.immutable();
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(inventory.player.level(), this.blockPos);
        this.addDataSlots(this);
    }

    @Nullable
    private static HydraulicConnectionHeadBlockEntity resolveBlockEntity(Inventory inventory, BlockPos blockPos) {
        return inventory.player.level().getBlockEntity(blockPos) instanceof HydraulicConnectionHeadBlockEntity be ? be : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.blockEntity != null && this.blockEntity.getLevel() == player.level()
                && !this.blockEntity.isRemoved()
                && player.distanceToSqr(
                        this.blockEntity.getBlockPos().getX() + 0.5D,
                        this.blockEntity.getBlockPos().getY() + 0.5D,
                        this.blockEntity.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public int get(int index) {
        if (this.blockEntity == null) {
            return 0;
        }
        return switch (index) {
            case 0 -> this.blockEntity.getStretchResistance();
            case 1 -> this.blockEntity.isFreeMode() ? 1 : 0;
            case 2 -> this.blockEntity.getExpectedLengthTenths();
            case 3 -> this.blockEntity.getReturnForce();
            case 4 -> this.blockEntity.isExpectedLengthControlledByRegulator() ? 1 : 0;
            case 5 -> this.blockEntity.isCreativeLink() ? 1 : 0;
            case 6 -> this.blockEntity.getRedstoneMinLengthTenths();
            case 7 -> this.blockEntity.getRedstoneMaxLengthTenths();
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
        if (this.blockEntity == null) {
            return;
        }

        int stretchResistance = this.get(0);
        boolean freeMode = this.get(1) != 0;
        int expectedLengthTenths = this.get(2);
        int returnForce = this.get(3);
        boolean creativeLink = this.get(5) != 0;
        int redstoneMinLengthTenths = this.get(6);
        int redstoneMaxLengthTenths = this.get(7);
        switch (index) {
            case 0 -> {
                if (creativeLink) {
                    return;
                }
                stretchResistance = HydraulicConnectionHeadBlockEntity.clampStretchResistance(value);
            }
            case 1 -> {
                if (creativeLink) {
                    return;
                }
                freeMode = value != 0;
            }
            case 2 -> expectedLengthTenths = value;
            case 3 -> {
                if (creativeLink) {
                    return;
                }
                returnForce = HydraulicConnectionHeadBlockEntity.clampReturnForce(value);
            }
            case 4, 5 -> {
                return;
            }
            case 6 -> redstoneMinLengthTenths = value;
            case 7 -> redstoneMaxLengthTenths = value;
            default -> {
                return;
            }
        }
        this.blockEntity.applySyncedSettings(stretchResistance, freeMode, expectedLengthTenths, returnForce,
                redstoneMinLengthTenths, redstoneMaxLengthTenths);
    }

    @Override
    public int getCount() {
        return 8;
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public int getStretchResistance() {
        return this.get(0);
    }

    public boolean isFreeMode() {
        return this.get(1) != 0;
    }

    public int getExpectedLengthTenths() {
        return this.get(2);
    }

    public int getReturnForce() {
        return this.get(3);
    }

    public boolean isExpectedLengthControlledByRegulator() {
        return this.get(4) != 0;
    }

    public boolean isCreativeLink() {
        return this.get(5) != 0;
    }

    public int getRedstoneMinLengthTenths() {
        return this.get(6);
    }

    public int getRedstoneMaxLengthTenths() {
        return this.get(7);
    }

}
