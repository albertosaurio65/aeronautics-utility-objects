package com.enxv.aerouniversaljoint.client;

import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadBlockEntity;
import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadMenu;
import com.enxv.aerouniversaljoint.network.SetHydraulicSettingsPayload;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

public class HydraulicConnectionHeadScreen extends AbstractContainerScreen<HydraulicConnectionHeadMenu> {
    private static final int WINDOW_WIDTH = 214;
    private static final int WINDOW_HEIGHT = 224;
    private static final int LABEL_X = 14;
    private static final int FIRST_ROW_Y = 31;
    private static final int ROW_HEIGHT = 26;
    private static final int BAR_X = 14;
    private static final int BAR_WIDTH = 186;
    private static final int BAR_HEIGHT = 8;
    private static final int CURSOR_WIDTH = 14;
    private static final int REDSTONE_RANGE_ROW = 4;
    private static final int REDSTONE_RANGE_STEP_TENTHS = 5;
    private static final ResourceLocation MODE_FONT = ResourceLocation.fromNamespaceAndPath("minecraft", "uniform");

    private IconButton confirmButton;
    private int stretchResistance;
    private boolean freeMode;
    private int expectedLengthTenths;
    private int returnForce;
    private int redstoneMinLengthTenths;
    private int redstoneMaxLengthTenths;
    private boolean expectedLengthControlled;
    private boolean creativeLink;
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
                    && this.menu.isCreativeLink() == this.creativeLink) {
                this.syncGraceTicks = 0;
            } else {
                this.syncGraceTicks--;
            }
            return;
        }
        this.readMenuValues();
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
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x + 4, y + 4, x + this.imageWidth - 4, y + this.imageHeight - 4, 0xE013171B);
        guiGraphics.fill(x + 8, y + 28, x + this.imageWidth - 8, y + this.imageHeight - 30, 0xA01A2026);
        this.renderBrassFrame(guiGraphics, x, y, this.imageWidth, this.imageHeight);
        if (!this.creativeLink) {
            int stretchLevel = HydraulicConnectionHeadBlockEntity.stretchResistanceToLevel(this.stretchResistance);
            this.renderSliderRow(guiGraphics, x, y, 0,
                    Component.translatable("setting.aeronautics_utility_objects.hydraulic_stretch_resistance"),
                    Component.literal(Integer.toString(stretchLevel)),
                    stretchLevel,
                    0,
                    HydraulicConnectionHeadBlockEntity.getMaxStretchResistanceLevel(),
                    true,
                    this.stretchWarningIntensity(stretchLevel));
            this.renderModeRow(guiGraphics, x, y, 1);
        }
        int expectedLengthRow = this.displayRow(2);
        this.renderSliderRow(guiGraphics, x, y, expectedLengthRow,
                Component.translatable("setting.aeronautics_utility_objects.hydraulic_expected_length"),
                Component.literal(HydraulicConnectionHeadBlockEntity.formatTenths(this.expectedLengthTenths)),
                this.expectedLengthTenths,
                HydraulicConnectionHeadBlockEntity.getMinExpectedLengthTenths(),
                HydraulicConnectionHeadBlockEntity.getMaxExpectedLengthTenths(),
                !this.freeMode && !this.expectedLengthControlled);
        if (!this.creativeLink) {
            this.renderSliderRow(guiGraphics, x, y, 3,
                    Component.translatable("setting.aeronautics_utility_objects.hydraulic_return_force"),
                    Component.literal(Integer.toString(HydraulicConnectionHeadBlockEntity.returnForceToLevel(this.returnForce))),
                    HydraulicConnectionHeadBlockEntity.returnForceToLevel(this.returnForce),
                    0,
                    HydraulicConnectionHeadBlockEntity.getMaxReturnForceLevel(),
                    !this.freeMode);
        }
        this.renderRangeSliderRow(guiGraphics, x, y, this.displayRow(REDSTONE_RANGE_ROW),
                Component.translatable("setting.aeronautics_utility_objects.hydraulic_redstone_range"),
                Component.literal(HydraulicConnectionHeadBlockEntity.formatTenths(this.redstoneMinLengthTenths)
                        + " - "
                        + HydraulicConnectionHeadBlockEntity.formatTenths(this.redstoneMaxLengthTenths)),
                this.redstoneMinLengthTenths,
                this.redstoneMaxLengthTenths,
                HydraulicConnectionHeadBlockEntity.getMinExpectedLengthTenths(),
                HydraulicConnectionHeadBlockEntity.getMaxExpectedLengthTenths(),
                true);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 15, 12, 0xE6EEF8, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
    }

    private void renderModeRow(GuiGraphics guiGraphics, int x, int y, int row) {
        int rowY = y + FIRST_ROW_Y + row * ROW_HEIGHT;
        Component titleLabel = Component.translatable("setting.aeronautics_utility_objects.hydraulic_free_mode");

        guiGraphics.drawString(this.font, titleLabel, x + LABEL_X, rowY, 0xE6EEF8, false);
        int switchY = rowY + 12;
        int switchWidth = 116;
        int switchHeight = 12;
        int switchX = x + BAR_X;
        int half = switchWidth / 2;
        guiGraphics.fill(switchX, switchY, switchX + switchWidth, switchY + switchHeight, 0xFF242B32);
        guiGraphics.fill(switchX + 1, switchY + 1, switchX + switchWidth - 1, switchY + switchHeight - 1, 0xFF343D46);
        int selectedLeft = this.freeMode ? switchX + half : switchX;
        guiGraphics.fill(selectedLeft + 1, switchY + 1, selectedLeft + half - 1, switchY + switchHeight - 1,
                this.freeMode ? 0xFF6DAED6 : 0xFFC8B27A);
        Component targetLabel = this.modeFont(Component.translatable("setting.aeronautics_utility_objects.hydraulic_free_mode.target"));
        Component freeLabel = this.modeFont(Component.translatable("setting.aeronautics_utility_objects.hydraulic_free_mode.free"));
        this.drawCenteredStringNoShadow(guiGraphics, targetLabel, switchX + half / 2, switchY + 2,
                this.freeMode ? 0xFFC5CED8 : 0xFF3F351F);
        this.drawCenteredStringNoShadow(guiGraphics, freeLabel, switchX + half + half / 2, switchY + 2,
                this.freeMode ? 0xFF173040 : 0xFFC5CED8);
    }

    private void renderSliderRow(GuiGraphics guiGraphics, int x, int y, int row, Component titleLabel, Component valueLabel,
                                 int value, int min, int max, boolean active) {
        this.renderSliderRow(guiGraphics, x, y, row, titleLabel, valueLabel, value, min, max, active, 0.0F);
    }

    private void renderSliderRow(GuiGraphics guiGraphics, int x, int y, int row, Component titleLabel, Component valueLabel,
                                 int value, int min, int max, boolean active, float warningIntensity) {
        int rowY = y + FIRST_ROW_Y + row * ROW_HEIGHT;
        boolean warning = warningIntensity > 0.0F;
        int warningTextColor = this.lerpRgb(0xFFFFD08A, 0xFFFF4242, warningIntensity);
        int warningValueColor = this.lerpRgb(0xFFFF6A3D, 0xFFFF0000, warningIntensity);
        int textColor = active ? warning ? warningTextColor : 0xE6EEF8 : 0x77808A;
        int valueColor = active ? warning ? warningValueColor : 0xC8B27A : 0x77808A;
        int valueX = x + this.imageWidth - 14 - this.font.width(valueLabel);

        guiGraphics.drawString(this.font, titleLabel, x + LABEL_X, rowY, textColor, false);
        guiGraphics.drawString(this.font, valueLabel, valueX, rowY, valueColor, false);

        int barY = rowY + 12;
        UIRenderHelper.drawStretched(guiGraphics, x + BAR_X, barY, BAR_WIDTH, BAR_HEIGHT, 0, AllGuiTextures.VALUE_SETTINGS_BAR_BG);
        if (active) {
            int segmentWidth = AllGuiTextures.VALUE_SETTINGS_BAR.getWidth() - 1;
            for (int offset = 0; offset < BAR_WIDTH; offset += segmentWidth) {
                UIRenderHelper.drawCropped(guiGraphics,
                        x + BAR_X + offset,
                        barY + 1,
                        Math.min(segmentWidth, BAR_WIDTH - offset),
                        BAR_HEIGHT,
                        0,
                        AllGuiTextures.VALUE_SETTINGS_BAR);
            }
            if (warning) {
                int overlayAlpha = 0x50 + Math.round(0x50 * warningIntensity);
                int overlayRgb = this.lerpRgb(0xFFFF6A24, 0xFFFF0000, warningIntensity) & 0x00FFFFFF;
                guiGraphics.fill(x + BAR_X, barY + 1, x + BAR_X + BAR_WIDTH, barY + BAR_HEIGHT,
                        (overlayAlpha << 24) | overlayRgb);
                guiGraphics.fill(x + BAR_X, barY + BAR_HEIGHT, x + BAR_X + BAR_WIDTH, barY + BAR_HEIGHT + 1,
                        0xFF000000 | overlayRgb);
            }
        } else {
            guiGraphics.fill(x + BAR_X, barY + 1, x + BAR_X + BAR_WIDTH, barY + BAR_HEIGHT, 0x80505A64);
        }

        float progress = max == min ? 0.0F : (value - min) / (float) (max - min);
        int centerX = x + BAR_X + Math.round((BAR_WIDTH - 1) * Mth.clamp(progress, 0.0F, 1.0F));
        int cursorLeft = Mth.clamp(centerX - CURSOR_WIDTH / 2, x + BAR_X, x + BAR_X + BAR_WIDTH - CURSOR_WIDTH);
        AllGuiTextures.VALUE_SETTINGS_CURSOR_LEFT.render(guiGraphics, cursorLeft - 3, barY - 3);
        UIRenderHelper.drawCropped(guiGraphics, cursorLeft, barY - 3, CURSOR_WIDTH, 14, 0, AllGuiTextures.VALUE_SETTINGS_CURSOR);
        AllGuiTextures.VALUE_SETTINGS_CURSOR_RIGHT.render(guiGraphics, cursorLeft + CURSOR_WIDTH, barY - 3);
    }

    private void renderRangeSliderRow(GuiGraphics guiGraphics, int x, int y, int row, Component titleLabel, Component valueLabel,
                                      int minValue, int maxValue, int min, int max, boolean active) {
        int rowY = y + FIRST_ROW_Y + row * ROW_HEIGHT;
        int textColor = active ? 0xE6EEF8 : 0x77808A;
        int valueColor = active ? 0xC8B27A : 0x77808A;
        int valueX = x + this.imageWidth - 14 - this.font.width(valueLabel);

        guiGraphics.drawString(this.font, titleLabel, x + LABEL_X, rowY, textColor, false);
        guiGraphics.drawString(this.font, valueLabel, valueX, rowY, valueColor, false);

        int barY = rowY + 12;
        UIRenderHelper.drawStretched(guiGraphics, x + BAR_X, barY, BAR_WIDTH, BAR_HEIGHT, 0, AllGuiTextures.VALUE_SETTINGS_BAR_BG);
        if (active) {
            int segmentWidth = AllGuiTextures.VALUE_SETTINGS_BAR.getWidth() - 1;
            for (int offset = 0; offset < BAR_WIDTH; offset += segmentWidth) {
                UIRenderHelper.drawCropped(guiGraphics,
                        x + BAR_X + offset,
                        barY + 1,
                        Math.min(segmentWidth, BAR_WIDTH - offset),
                        BAR_HEIGHT,
                        0,
                        AllGuiTextures.VALUE_SETTINGS_BAR);
            }
        } else {
            guiGraphics.fill(x + BAR_X, barY + 1, x + BAR_X + BAR_WIDTH, barY + BAR_HEIGHT, 0x80505A64);
        }

        int minCenterX = this.valueToSliderX(minValue, min, max);
        int maxCenterX = this.valueToSliderX(maxValue, min, max);
        if (active) {
            guiGraphics.fill(minCenterX, barY + 1, maxCenterX + 1, barY + BAR_HEIGHT, 0xB0C8B27A);
            guiGraphics.fill(minCenterX, barY + BAR_HEIGHT, maxCenterX + 1, barY + BAR_HEIGHT + 1, 0xFFC8B27A);
        }
        this.renderSliderCursor(guiGraphics, minCenterX, barY, 0xFFFFD37A);
        this.renderSliderCursor(guiGraphics, maxCenterX, barY, 0xFFFFD37A);
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
            this.stretchResistance = HydraulicConnectionHeadBlockEntity.stretchResistanceFromLevel(clamped);
        } else if (row == 2) {
            if (this.expectedLengthControlled) {
                return;
            }
            this.expectedLengthTenths = clamped;
        } else if (row == 3) {
            this.returnForce = HydraulicConnectionHeadBlockEntity.returnForceFromLevel(clamped);
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

    private static int snapAndClampRedstoneRangeValue(int value) {
        return Mth.clamp(snapRedstoneRangeValue(value),
                HydraulicConnectionHeadBlockEntity.getMinExpectedLengthTenths(),
                HydraulicConnectionHeadBlockEntity.getMaxExpectedLengthTenths());
    }

    private void sendSettings() {
        this.syncGraceTicks = 10;
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
        List<Component> lines = new ArrayList<>();
        int hoveredRow = this.hoveredSettingRow(mouseX, mouseY);
        if (hoveredRow == 0) {
            int level = HydraulicConnectionHeadBlockEntity.stretchResistanceToLevel(this.stretchResistance);
            this.addTooltip(lines,
                    "tooltip.aeronautics_utility_objects.gui.stretch_resistance.1",
                    "tooltip.aeronautics_utility_objects.gui.stretch_resistance.2",
                    "tooltip.aeronautics_utility_objects.gui.stretch_resistance.3");
            if (level > HydraulicConnectionHeadBlockEntity.getStretchResistanceWarningLevel()) {
                this.addTooltip(lines,
                        "tooltip.aeronautics_utility_objects.gui.stretch_resistance.warning.1",
                        "tooltip.aeronautics_utility_objects.gui.stretch_resistance.warning.2");
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
            this.addTooltip(lines,
                    "tooltip.aeronautics_utility_objects.gui.return_force.1",
                    "tooltip.aeronautics_utility_objects.gui.return_force.2",
                    "tooltip.aeronautics_utility_objects.gui.return_force.3");
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
        int left = this.leftPos + BAR_X - 3;
        int top = this.topPos + FIRST_ROW_Y + this.displayRow(row) * ROW_HEIGHT + 8;
        return mouseX >= left && mouseX <= left + BAR_WIDTH + 6 && mouseY >= top && mouseY <= top + 18;
    }

    private boolean isHoveringMode(double mouseX, double mouseY) {
        if (this.creativeLink) {
            return false;
        }
        int left = this.leftPos + BAR_X - 3;
        int top = this.topPos + FIRST_ROW_Y + ROW_HEIGHT + 8;
        return mouseX >= left && mouseX <= left + BAR_WIDTH + 6 && mouseY >= top && mouseY <= top + 18;
    }

    private int getRowValue(int row) {
        return switch (row) {
            case 0 -> HydraulicConnectionHeadBlockEntity.stretchResistanceToLevel(this.stretchResistance);
            case 2 -> this.expectedLengthTenths;
            case 3 -> HydraulicConnectionHeadBlockEntity.returnForceToLevel(this.returnForce);
            case REDSTONE_RANGE_ROW -> (this.redstoneMinLengthTenths + this.redstoneMaxLengthTenths) / 2;
            default -> 0;
        };
    }

    private int getRowMin(int row) {
        return switch (row) {
            case REDSTONE_RANGE_ROW -> HydraulicConnectionHeadBlockEntity.getMinExpectedLengthTenths();
            case 2 -> HydraulicConnectionHeadBlockEntity.getMinExpectedLengthTenths();
            default -> 0;
        };
    }

    private int getRowMax(int row) {
        return switch (row) {
            case 0 -> HydraulicConnectionHeadBlockEntity.getMaxStretchResistanceLevel();
            case 2 -> HydraulicConnectionHeadBlockEntity.getMaxExpectedLengthTenths();
            case 3 -> HydraulicConnectionHeadBlockEntity.getMaxReturnForceLevel();
            case REDSTONE_RANGE_ROW -> HydraulicConnectionHeadBlockEntity.getMaxExpectedLengthTenths();
            default -> 0;
        };
    }

    private int displayRow(int row) {
        if (!this.creativeLink) {
            return row;
        }
        if (row == 2) {
            return 0;
        }
        if (row == REDSTONE_RANGE_ROW) {
            return 1;
        }
        return row;
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
        float progress = max == min ? 0.0F : (value - min) / (float) (max - min);
        return this.leftPos + BAR_X + Math.round((BAR_WIDTH - 1) * Mth.clamp(progress, 0.0F, 1.0F));
    }

    private void renderSliderCursor(GuiGraphics guiGraphics, int centerX, int barY, int tint) {
        int cursorLeft = Mth.clamp(centerX - CURSOR_WIDTH / 2, this.leftPos + BAR_X,
                this.leftPos + BAR_X + BAR_WIDTH - CURSOR_WIDTH);
        AllGuiTextures.VALUE_SETTINGS_CURSOR_LEFT.render(guiGraphics, cursorLeft - 3, barY - 3);
        UIRenderHelper.drawCropped(guiGraphics, cursorLeft, barY - 3, CURSOR_WIDTH, 14, 0, AllGuiTextures.VALUE_SETTINGS_CURSOR);
        AllGuiTextures.VALUE_SETTINGS_CURSOR_RIGHT.render(guiGraphics, cursorLeft + CURSOR_WIDTH, barY - 3);
        guiGraphics.fill(cursorLeft, barY - 1, cursorLeft + CURSOR_WIDTH, barY + 10, tint & 0x80FFFFFF);
    }

    private float stretchWarningIntensity(int level) {
        int warningLevel = HydraulicConnectionHeadBlockEntity.getStretchResistanceWarningLevel();
        int maxLevel = HydraulicConnectionHeadBlockEntity.getMaxStretchResistanceLevel();
        if (level <= warningLevel || maxLevel <= warningLevel) {
            return 0.0F;
        }
        return Mth.clamp((level - warningLevel) / (float) (maxLevel - warningLevel), 0.0F, 1.0F);
    }

    private int lerpRgb(int from, int to, float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        int r = Mth.lerpInt(clamped, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = Mth.lerpInt(clamped, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = Mth.lerpInt(clamped, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }

    private void drawCenteredStringNoShadow(GuiGraphics guiGraphics, Component text, int centerX, int y, int color) {
        guiGraphics.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    private Component modeFont(Component text) {
        return text.copy().withStyle(style -> style.withFont(MODE_FONT));
    }

    private void renderBrassFrame(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        AllGuiTextures.BRASS_FRAME_TL.render(guiGraphics, x, y);
        AllGuiTextures.BRASS_FRAME_TR.render(guiGraphics, x + width - 4, y);
        AllGuiTextures.BRASS_FRAME_BL.render(guiGraphics, x, y + height - 4);
        AllGuiTextures.BRASS_FRAME_BR.render(guiGraphics, x + width - 4, y + height - 4);

        if (height > 8) {
            UIRenderHelper.drawStretched(guiGraphics, x, y + 4, 3, height - 8, 0, AllGuiTextures.BRASS_FRAME_LEFT);
            UIRenderHelper.drawStretched(guiGraphics, x + width - 3, y + 4, 3, height - 8, 0, AllGuiTextures.BRASS_FRAME_RIGHT);
        }

        if (width > 8) {
            UIRenderHelper.drawCropped(guiGraphics, x + 4, y, width - 8, 3, 0, AllGuiTextures.BRASS_FRAME_TOP);
            UIRenderHelper.drawCropped(guiGraphics, x + 4, y + height - 3, width - 8, 3, 0, AllGuiTextures.BRASS_FRAME_BOTTOM);
        }
    }
}
