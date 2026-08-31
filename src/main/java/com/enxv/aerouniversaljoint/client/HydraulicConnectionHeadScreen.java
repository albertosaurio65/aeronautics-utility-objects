package com.enxv.aerouniversaljoint.client;

import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadBlockEntity;
import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadMenu;
import com.enxv.aerouniversaljoint.network.SetHydraulicSettingsPayload;
import com.enxv.aerouniversaljoint.network.SetGiantHydraulicSettingsPayload;
import com.enxv.aerouniversaljoint.network.SetHydraulicHingeLimitsPayload;
import com.enxv.aerouniversaljoint.content.hydraulic.GiantHydraulicSettingsState;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

public class HydraulicConnectionHeadScreen extends AbstractContainerScreen<HydraulicConnectionHeadMenu> {
    private static final int WINDOW_WIDTH = HydraulicConnectionHeadSettingsRenderer.WINDOW_WIDTH;
    private static final int WINDOW_HEIGHT = HydraulicConnectionHeadSettingsRenderer.WINDOW_HEIGHT;
    private static final int BAR_X = HydraulicConnectionHeadSettingsRenderer.BAR_X;
    private static final int BAR_WIDTH = HydraulicConnectionHeadSettingsRenderer.BAR_WIDTH;
    private static final int REDSTONE_RANGE_ROW = HydraulicConnectionHeadSettingsRenderer.REDSTONE_RANGE_ROW;
    private static final int REDSTONE_RANGE_STEP_TENTHS = HydraulicConnectionHeadSettingsRenderer.REDSTONE_RANGE_STEP_TENTHS;

    private IconButton confirmButton;
    private int stretchResistance;
    private boolean freeMode;
    private int expectedLengthTenths;
    private int returnForce;
    private int redstoneMinLengthTenths;
    private int redstoneMaxLengthTenths;
    private boolean expectedLengthControlled;
    private boolean creativeLink;
    private boolean giantHydraulicLink;
    private boolean brassHingeHead;
    private int draggingRow = -1;
    private int draggingRangeEndpoint = 0;
    private int syncGraceTicks;

    public HydraulicConnectionHeadScreen(HydraulicConnectionHeadMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WINDOW_WIDTH;
        this.imageHeight = WINDOW_HEIGHT;
        this.inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        super.init();
        this.readMenuValues();
        this.imageHeight = this.brassHingeHead
                ? HydraulicConnectionHeadSettingsRenderer.HINGE_WINDOW_HEIGHT : WINDOW_HEIGHT;
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        this.confirmButton = new IconButton(this.leftPos + this.imageWidth - 33, this.topPos + this.imageHeight - 24, AllIcons.I_CONFIRM);
        this.confirmButton.withCallback(this::onClose);
        this.addRenderableWidget(this.confirmButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.draggingRow >= 0) {
            return;
        }
        if (this.syncGraceTicks > 0) {
            if (this.menu.getStretchResistance() == this.stretchResistance
                    && this.menu.isFreeMode() == this.freeMode
                    && this.menu.getExpectedLengthTenths() == this.expectedLengthTenths
                    && this.menu.getReturnForce() == this.returnForce
                    && this.menu.getRedstoneMinLengthTenths() == this.redstoneMinLengthTenths
                    && this.menu.getRedstoneMaxLengthTenths() == this.redstoneMaxLengthTenths
                    && this.menu.isExpectedLengthControlledByRegulator() == this.expectedLengthControlled
                    && this.menu.isCreativeLink() == this.creativeLink
                    && this.menu.isGiantHydraulicLink() == this.giantHydraulicLink) {
                this.syncGraceTicks = 0;
            } else {
                this.syncGraceTicks--;
            }
            return;
        }
        this.readMenuValues();
        if (this.brassHingeHead) {
            return;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        this.renderSettingTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        if (this.brassHingeHead) {
            HydraulicConnectionHeadSettingsRenderer.renderHingeLimits(guiGraphics, this.font, this.title,
                    this.leftPos, this.topPos, this.stretchResistance, this.returnForce);
            return;
        }
        HydraulicConnectionHeadSettingsRenderer.render(guiGraphics, this.font, this.title, this.leftPos, this.topPos,
                HydraulicConnectionHeadSettingsRenderer.fromRaw(
                        this.stretchResistance,
                        this.freeMode,
                        this.expectedLengthTenths,
                        this.returnForce,
                        this.redstoneMinLengthTenths,
                        this.redstoneMaxLengthTenths,
                        this.expectedLengthControlled,
                        this.creativeLink,
                        this.giantHydraulicLink));
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.brassHingeHead) {
            int row = this.getHingeSliderRow(mouseX, mouseY);
            if (button == 0 && row >= 0) {
                this.draggingRow = row;
                this.updateHingeLimitFromMouse(mouseX);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (!this.creativeLink && this.isHoveringMode(mouseX, mouseY)) {
            this.freeMode = !this.freeMode;
            this.sendSettings();
            return true;
        }
        int row = this.hoveredSliderRow(mouseX, mouseY);
        if (row >= 0) {
            this.draggingRow = row;
            this.draggingRangeEndpoint = row == REDSTONE_RANGE_ROW ? this.getRangeEndpointForMouse(mouseX) : 0;
            this.updateSliderFromMouse(row, mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.brassHingeHead && this.draggingRow >= 0 && button == 0) {
            this.updateHingeLimitFromMouse(mouseX);
            return true;
        }
        if (this.draggingRow >= 0 && button == 0) {
            this.updateSliderFromMouse(this.draggingRow, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingRow = -1;
        this.draggingRangeEndpoint = 0;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.brassHingeHead && (this.getHingeSliderRow(mouseX, mouseY) >= 0)) {
            this.draggingRow = this.getHingeSliderRow(mouseX, mouseY);
            int delta = scrollY > 0 ? 5 : -5;
            this.changeHingeLimit(this.draggingRow == 0 ? delta : 0,
                    this.draggingRow == 1 ? delta : 0);
            return true;
        }
        int row = this.hoveredSliderRow(mouseX, mouseY);
        if (row >= 0) {
            int step = switch (row) {
                case 0 -> Screen.hasShiftDown() ? 2 : 1;
                case 2 -> Screen.hasShiftDown() ? 10 : 1;
                case REDSTONE_RANGE_ROW -> Screen.hasShiftDown() ? 10 : REDSTONE_RANGE_STEP_TENTHS;
                case 3 -> Screen.hasShiftDown() ? 5 : 1;
                default -> 1;
            };
            this.applySliderValue(row, this.getRowValue(row) + (scrollY > 0 ? step : -step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void readMenuValues() {
        this.stretchResistance = this.menu.getStretchResistance();
        this.freeMode = this.menu.isFreeMode();
        this.expectedLengthTenths = this.menu.getExpectedLengthTenths();
        this.returnForce = this.menu.getReturnForce();
        this.redstoneMinLengthTenths = this.menu.getRedstoneMinLengthTenths();
        this.redstoneMaxLengthTenths = this.menu.getRedstoneMaxLengthTenths();
        this.expectedLengthControlled = this.menu.isExpectedLengthControlledByRegulator();
        this.creativeLink = this.menu.isCreativeLink();
        this.giantHydraulicLink = this.menu.isGiantHydraulicLink();
        this.brassHingeHead = this.menu.isBrassHingeHead();
    }

    private void changeHingeLimit(int minDelta, int maxDelta) {
        int min = Mth.clamp(this.stretchResistance + minDelta, -180, 180);
        int max = Mth.clamp(this.returnForce + maxDelta, -180, 180);
        if (max <= min) {
            return;
        }
        this.stretchResistance = min;
        this.returnForce = max;
        this.syncGraceTicks = 10;
        PacketDistributor.sendToServer(new SetHydraulicHingeLimitsPayload(this.menu.getBlockPos(), min, max));
    }

    private int getHingeEndpointForMouse(double mouseX) {
        int minX = HydraulicConnectionHeadSettingsRenderer.valueToSliderX(this.leftPos,
                this.stretchResistance, -180, 180);
        int maxX = HydraulicConnectionHeadSettingsRenderer.valueToSliderX(this.leftPos,
                this.returnForce, -180, 180);
        return Math.abs(mouseX - minX) <= Math.abs(mouseX - maxX) ? -1 : 1;
    }

    private void updateHingeLimitFromMouse(double mouseX) {
        float progress = (float) ((mouseX - (this.leftPos + BAR_X)) / (BAR_WIDTH - 1.0D));
        int value = Math.round(Mth.clamp(progress, 0.0F, 1.0F) * 180.0F);
        value = Math.round(value / 5.0F) * 5;
        int min = this.stretchResistance;
        int max = this.returnForce;
        if (this.draggingRow == 0) min = -Math.min(value, max - 5);
        if (this.draggingRow == 1) max = Math.max(value, min + 5);
        min = Mth.clamp(min, -180, 175);
        max = Mth.clamp(max, -175, 180);
        if (min == this.stretchResistance && max == this.returnForce) {
            return;
        }
        this.stretchResistance = min;
        this.returnForce = max;
        this.sendSettings();
    }

    private int getHingeSliderRow(double mouseX, double mouseY) {
        for (int row = 0; row < 2; row++) {
            if (HydraulicConnectionHeadSettingsRenderer.isHoveringHingeSlider(this.leftPos, this.topPos, row, mouseX, mouseY)) return row;
        }
        return -1;
    }

    private void updateSliderFromMouse(int row, double mouseX) {
        int min = this.getRowMin(row);
        int max = this.getRowMax(row);
        float progress = (float) ((mouseX - (this.leftPos + BAR_X)) / (BAR_WIDTH - 1.0D));
        int value = Math.round(Mth.clamp(progress, 0.0F, 1.0F) * (max - min)) + min;
        this.applySliderValue(row, row == REDSTONE_RANGE_ROW ? snapRedstoneRangeValue(value) : value);
    }

    private void applySliderValue(int row, int value) {
        int clamped = Mth.clamp(value, this.getRowMin(row), this.getRowMax(row));
        if (row == 0) {
            this.stretchResistance = this.giantHydraulicLink ? clamped
                    : HydraulicConnectionHeadBlockEntity.stretchResistanceFromLevel(clamped);
        } else if (row == 2) {
            if (this.expectedLengthControlled) {
                return;
            }
            this.expectedLengthTenths = clamped;
        } else if (row == 3) {
            this.returnForce = this.giantHydraulicLink ? clamped
                    : HydraulicConnectionHeadBlockEntity.returnForceFromLevel(clamped);
        } else if (row == REDSTONE_RANGE_ROW) {
            this.applyRedstoneRangeValue(clamped);
        } else {
            return;
        }
        this.sendSettings();
    }

    private void applyRedstoneRangeValue(int value) {
        value = snapAndClampRedstoneRangeValue(value);
        int currentMin = snapAndClampRedstoneRangeValue(this.redstoneMinLengthTenths);
        int currentMax = snapAndClampRedstoneRangeValue(this.redstoneMaxLengthTenths);
        if (currentMin > currentMax) {
            int swapped = currentMin;
            currentMin = currentMax;
            currentMax = swapped;
        }
        if (this.draggingRangeEndpoint < 0) {
            this.redstoneMinLengthTenths = Math.min(value, currentMax);
            this.redstoneMaxLengthTenths = currentMax;
            return;
        }
        if (this.draggingRangeEndpoint > 0) {
            this.redstoneMinLengthTenths = currentMin;
            this.redstoneMaxLengthTenths = Math.max(value, currentMin);
            return;
        }

        int span = currentMax - currentMin;
        int min = this.getRowMin(REDSTONE_RANGE_ROW);
        int max = this.getRowMax(REDSTONE_RANGE_ROW);
        int newMin = Mth.clamp(snapRedstoneRangeValue(value - span / 2), min, max - span);
        this.redstoneMinLengthTenths = newMin;
        this.redstoneMaxLengthTenths = newMin + span;
    }

    private static int snapRedstoneRangeValue(int value) {
        return Math.round(value / (float) REDSTONE_RANGE_STEP_TENTHS) * REDSTONE_RANGE_STEP_TENTHS;
    }

    private int snapAndClampRedstoneRangeValue(int value) {
        return Mth.clamp(snapRedstoneRangeValue(value),
                HydraulicConnectionHeadBlockEntity.getMinExpectedLengthTenths(this.giantHydraulicLink),
                HydraulicConnectionHeadBlockEntity.getMaxExpectedLengthTenths());
    }

    private void sendSettings() {
        this.syncGraceTicks = 10;
        if (this.brassHingeHead) {
            PacketDistributor.sendToServer(new SetHydraulicHingeLimitsPayload(this.menu.getBlockPos(),
                    this.stretchResistance, this.returnForce));
            return;
        }
        if (this.giantHydraulicLink) {
            PacketDistributor.sendToServer(new SetGiantHydraulicSettingsPayload(
                    this.menu.getBlockPos(), this.stretchResistance, this.freeMode, this.expectedLengthTenths,
                    this.returnForce, this.redstoneMinLengthTenths, this.redstoneMaxLengthTenths));
            return;
        }
        PacketDistributor.sendToServer(new SetHydraulicSettingsPayload(
                this.menu.getBlockPos(),
                this.stretchResistance,
                this.freeMode,
                this.expectedLengthTenths,
                this.returnForce,
                this.redstoneMinLengthTenths,
                this.redstoneMaxLengthTenths));
    }

    private int hoveredSliderRow(double mouseX, double mouseY) {
        for (int row : new int[] {0, 2, 3, REDSTONE_RANGE_ROW}) {
            if (this.creativeLink && row != 2) {
                if (row != REDSTONE_RANGE_ROW) {
                    continue;
                }
            }
            if (row == REDSTONE_RANGE_ROW && this.isHoveringSlider(row, mouseX, mouseY)) {
                return row;
            }
            if (this.creativeLink && row != 2) {
                continue;
            }
            if (this.freeMode && (row == 2 || row == 3)) {
                continue;
            }
            if (this.expectedLengthControlled && row == 2) {
                continue;
            }
            if (this.isHoveringSlider(row, mouseX, mouseY)) {
                return row;
            }
        }
        return -1;
    }

    private void renderSettingTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.brassHingeHead) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        int hoveredRow = this.hoveredSettingRow(mouseX, mouseY);
        if (hoveredRow == 0) {
            int level = HydraulicConnectionHeadBlockEntity.stretchResistanceToLevel(this.stretchResistance);
            if (this.giantHydraulicLink) {
                this.addTooltip(lines,
                        "tooltip.aeronautics_utility_objects.gui.giant_hydraulic_flow.1",
                        "tooltip.aeronautics_utility_objects.gui.giant_hydraulic_flow.2");
            } else {
            this.addTooltip(lines,
                    "tooltip.aeronautics_utility_objects.gui.stretch_resistance.1",
                    "tooltip.aeronautics_utility_objects.gui.stretch_resistance.2",
                    "tooltip.aeronautics_utility_objects.gui.stretch_resistance.3");
            if (level > HydraulicConnectionHeadBlockEntity.getStretchResistanceWarningLevel()) {
                this.addTooltip(lines,
                        "tooltip.aeronautics_utility_objects.gui.stretch_resistance.warning.1",
                        "tooltip.aeronautics_utility_objects.gui.stretch_resistance.warning.2");
            }
            }
        } else if (hoveredRow == 2) {
            if (this.creativeLink) {
                this.addTooltip(lines,
                        "tooltip.aeronautics_utility_objects.gui.creative_expected_length.1",
                        "tooltip.aeronautics_utility_objects.gui.creative_expected_length.2",
                        "tooltip.aeronautics_utility_objects.gui.creative_expected_length.3");
            } else if (this.expectedLengthControlled) {
                this.addTooltip(lines,
                        "tooltip.aeronautics_utility_objects.gui.expected_length.regulator.1",
                        "tooltip.aeronautics_utility_objects.gui.expected_length.regulator.2");
            } else {
                this.addTooltip(lines,
                        "tooltip.aeronautics_utility_objects.gui.expected_length.1",
                        "tooltip.aeronautics_utility_objects.gui.expected_length.2",
                        "tooltip.aeronautics_utility_objects.gui.expected_length.3");
            }
            if (this.freeMode) {
                this.addTooltip(lines, "tooltip.aeronautics_utility_objects.gui.disabled.free_mode");
            }
        } else if (hoveredRow == 3) {
            if (this.giantHydraulicLink) {
                this.addTooltip(lines,
                        "tooltip.aeronautics_utility_objects.gui.giant_hydraulic_pressure.1",
                        "tooltip.aeronautics_utility_objects.gui.giant_hydraulic_pressure.2");
            } else {
                this.addTooltip(lines,
                        "tooltip.aeronautics_utility_objects.gui.return_force.1",
                        "tooltip.aeronautics_utility_objects.gui.return_force.2",
                        "tooltip.aeronautics_utility_objects.gui.return_force.3");
            }
            if (this.freeMode) {
                this.addTooltip(lines, "tooltip.aeronautics_utility_objects.gui.disabled.free_mode");
            }
        } else if (hoveredRow == REDSTONE_RANGE_ROW) {
            this.addTooltip(lines,
                    "tooltip.aeronautics_utility_objects.gui.redstone_range.1",
                    "tooltip.aeronautics_utility_objects.gui.redstone_range.2",
                    "tooltip.aeronautics_utility_objects.gui.redstone_range.3");
        } else if (this.isHoveringMode(mouseX, mouseY)) {
            this.addTooltip(lines,
                    "tooltip.aeronautics_utility_objects.gui.mode.1",
                    "tooltip.aeronautics_utility_objects.gui.mode.2",
                    "tooltip.aeronautics_utility_objects.gui.mode.3");
        }

        if (!lines.isEmpty()) {
            guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    private int hoveredSettingRow(double mouseX, double mouseY) {
        for (int row : new int[] {0, 2, 3, REDSTONE_RANGE_ROW}) {
            if (this.creativeLink && row != 2 && row != REDSTONE_RANGE_ROW) {
                continue;
            }
            if (this.isHoveringSlider(row, mouseX, mouseY)) {
                return row;
            }
        }
        return -1;
    }

    private void addTooltip(List<Component> lines, String... keys) {
        for (String key : keys) {
            lines.add(Component.translatable(key));
        }
    }

    private boolean isHoveringSlider(int row, double mouseX, double mouseY) {
        return HydraulicConnectionHeadSettingsRenderer.isHoveringSlider(this.leftPos, this.topPos, this.creativeLink, row, mouseX, mouseY);
    }

    private boolean isHoveringMode(double mouseX, double mouseY) {
        return HydraulicConnectionHeadSettingsRenderer.isHoveringMode(this.leftPos, this.topPos, this.creativeLink, mouseX, mouseY);
    }

    private int getRowValue(int row) {
        return switch (row) {
            case 0 -> this.giantHydraulicLink ? this.stretchResistance
                    : HydraulicConnectionHeadBlockEntity.stretchResistanceToLevel(this.stretchResistance);
            case 2 -> this.expectedLengthTenths;
            case 3 -> this.giantHydraulicLink ? this.returnForce
                    : HydraulicConnectionHeadBlockEntity.returnForceToLevel(this.returnForce);
            case REDSTONE_RANGE_ROW -> (this.redstoneMinLengthTenths + this.redstoneMaxLengthTenths) / 2;
            default -> 0;
        };
    }

    private int getRowMin(int row) {
        return switch (row) {
            case 0 -> this.giantHydraulicLink ? GiantHydraulicSettingsState.MIN_FLOW_LITRES_PER_MINUTE : 0;
            case REDSTONE_RANGE_ROW -> HydraulicConnectionHeadBlockEntity.getMinExpectedLengthTenths(this.giantHydraulicLink);
            case 2 -> HydraulicConnectionHeadBlockEntity.getMinExpectedLengthTenths(this.giantHydraulicLink);
            case 3 -> this.giantHydraulicLink ? GiantHydraulicSettingsState.MIN_PRESSURE_BAR : 0;
            default -> 0;
        };
    }

    private int getRowMax(int row) {
        return switch (row) {
            case 0 -> this.giantHydraulicLink ? GiantHydraulicSettingsState.MAX_FLOW_LITRES_PER_MINUTE
                    : HydraulicConnectionHeadBlockEntity.getMaxStretchResistanceLevel();
            case 2 -> HydraulicConnectionHeadBlockEntity.getMaxExpectedLengthTenths();
            case 3 -> this.giantHydraulicLink ? GiantHydraulicSettingsState.MAX_PRESSURE_BAR
                    : HydraulicConnectionHeadBlockEntity.getMaxReturnForceLevel();
            case REDSTONE_RANGE_ROW -> HydraulicConnectionHeadBlockEntity.getMaxExpectedLengthTenths();
            default -> 0;
        };
    }

    private int displayRow(int row) {
        return HydraulicConnectionHeadSettingsRenderer.displayRow(this.creativeLink, row);
    }

    private int getRangeEndpointForMouse(double mouseX) {
        int minX = this.valueToSliderX(this.redstoneMinLengthTenths, this.getRowMin(REDSTONE_RANGE_ROW),
                this.getRowMax(REDSTONE_RANGE_ROW));
        int maxX = this.valueToSliderX(this.redstoneMaxLengthTenths, this.getRowMin(REDSTONE_RANGE_ROW),
                this.getRowMax(REDSTONE_RANGE_ROW));
        if (Math.abs(mouseX - minX) <= Math.abs(mouseX - maxX)) {
            return -1;
        }
        return 1;
    }

    private int valueToSliderX(int value, int min, int max) {
        return HydraulicConnectionHeadSettingsRenderer.valueToSliderX(this.leftPos, value, min, max);
    }
}
