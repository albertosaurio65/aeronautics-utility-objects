package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class HydraulicConnectionHeadMenu extends AbstractContainerMenu implements ContainerData {
    private final ContainerLevelAccess access;
    private final BlockPos blockPos;
    @Nullable
    private final HydraulicConnectionHeadBlockEntity blockEntity;
    private final boolean rodSettingsMode;

    public HydraulicConnectionHeadMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), extraData.readBoolean());
    }

    public HydraulicConnectionHeadMenu(int containerId, Inventory inventory, HydraulicConnectionHeadBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity, false);
    }

    public HydraulicConnectionHeadMenu(int containerId, Inventory inventory,
                                       HydraulicConnectionHeadBlockEntity blockEntity, boolean rodSettingsMode) {
        this(containerId, inventory, blockEntity.getBlockPos(), blockEntity, rodSettingsMode);
    }

    private HydraulicConnectionHeadMenu(int containerId, Inventory inventory, BlockPos blockPos,
                                        boolean rodSettingsMode) {
        this(containerId, inventory, blockPos, resolveBlockEntity(inventory, blockPos), rodSettingsMode);
    }

    private HydraulicConnectionHeadMenu(int containerId, Inventory inventory, BlockPos blockPos,
                                        @Nullable HydraulicConnectionHeadBlockEntity blockEntity,
                                        boolean rodSettingsMode) {
        super(ModMenuTypes.HYDRAULIC_CONNECTION_HEAD.get(), containerId);
        this.blockPos = blockPos.immutable();
        this.blockEntity = blockEntity;
        this.rodSettingsMode = rodSettingsMode;
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
        return this.blockEntity != null && this.blockEntity.isSettingsInteractionValid(player);
    }

    @Override
    public int get(int index) {
        if (this.blockEntity == null) {
            return 0;
        }
        return switch (index) {
            case 0 -> this.isHingeLimitMode() ? this.blockEntity.getHingeMinAngle() : this.blockEntity.getStretchResistance();
            case 1 -> this.blockEntity.isFreeMode() ? 1 : 0;
            case 2 -> this.blockEntity.getExpectedLengthTenths();
            case 3 -> this.isHingeLimitMode() ? this.blockEntity.getHingeMaxAngle() : this.blockEntity.getReturnForce();
            case 4 -> this.blockEntity.isExpectedLengthControlledByRegulator() ? 1 : 0;
            case 5 -> this.blockEntity.isCreativeLink() ? 1 : 0;
            case 6 -> this.blockEntity.getRedstoneMinLengthTenths();
            case 7 -> this.blockEntity.getRedstoneMaxLengthTenths();
            case 8 -> this.blockEntity.isGiantHydraulicLink() ? 1 : 0;
            case 9 -> this.isHingeLimitMode() ? 1 : 0;
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
        boolean giantHydraulicLink = this.get(8) != 0;
        if (this.isBrassHingeHead()) {
            if (index == 0 || index == 3) {
                this.blockEntity.setHingeAngleLimits(index == 0 ? value : this.get(0),
                        index == 3 ? value : this.get(3));
            }
            return;
        }
        switch (index) {
            case 0 -> {
                if (creativeLink) {
                    return;
                }
                stretchResistance = giantHydraulicLink ? value : HydraulicConnectionHeadBlockEntity.clampStretchResistance(value);
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
                returnForce = giantHydraulicLink ? value : HydraulicConnectionHeadBlockEntity.clampReturnForce(value);
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
        if (giantHydraulicLink) {
            this.blockEntity.setGiantHydraulicSettingsAndMirror(stretchResistance, freeMode, expectedLengthTenths,
                    returnForce, redstoneMinLengthTenths, redstoneMaxLengthTenths);
        } else {
            this.blockEntity.applySyncedSettings(stretchResistance, freeMode, expectedLengthTenths, returnForce,
                    redstoneMinLengthTenths, redstoneMaxLengthTenths);
        }
    }

    @Override
    public int getCount() {
        return 10;
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    @Nullable
    public HydraulicConnectionHeadBlockEntity getBlockEntity() {
        return this.blockEntity;
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

    public boolean isGiantHydraulicLink() {
        return this.get(8) != 0;
    }

    public boolean isBrassHingeHead() {
        return this.get(9) != 0;
    }

    public boolean isRodSettingsMode() {
        return this.rodSettingsMode;
    }

    public static void open(Player player, HydraulicConnectionHeadBlockEntity head, boolean rodSettingsMode) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new HydraulicConnectionHeadMenu(
                        containerId, inventory, head, rodSettingsMode),
                head.getDisplayName()), buffer -> {
            buffer.writeBlockPos(head.getBlockPos());
            buffer.writeBoolean(rodSettingsMode);
        });
    }

    private boolean isHingeLimitMode() {
        return this.blockEntity != null && this.blockEntity.isBrassHingeHead() && !this.rodSettingsMode;
    }

}
