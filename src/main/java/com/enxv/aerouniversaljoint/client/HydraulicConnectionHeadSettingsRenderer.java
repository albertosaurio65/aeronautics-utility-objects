package com.enxv.aerouniversaljoint.client;

import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadBlockEntity;
import com.enxv.aerouniversaljoint.content.hydraulic.GiantHydraulicSettingsState;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class HydraulicConnectionHeadSettingsRenderer {
    public static final int WINDOW_WIDTH = 214;
    public static final int WINDOW_HEIGHT = 224;
    public static final int HINGE_WINDOW_HEIGHT = 148;
    public static final int FIRST_ROW_Y = 31;
    public static final int ROW_HEIGHT = 26;
    public static final int BAR_X = 14;
    public static final int BAR_WIDTH = 186;
    public static final int REDSTONE_RANGE_ROW = 4;
    public static final int REDSTONE_RANGE_STEP_TENTHS = 5;

    private static final int LABEL_X = 14;
    private static final int BAR_HEIGHT = 8;
    private static final int CURSOR_WIDTH = 14;
    private static final ResourceLocation MODE_FONT = ResourceLocation.fromNamespaceAndPath("minecraft", "uniform");

    private HydraulicConnectionHeadSettingsRenderer() {
    }

    public record DisplayState(float stretchLevel, boolean freeMode, float expectedLengthTenths,
                               float returnForceLevel, float redstoneMinLengthTenths,
                               float redstoneMaxLengthTenths, boolean expectedLengthControlled,
                               boolean creativeLink, boolean giantHydraulicLink) {
    }

    public static DisplayState fromRaw(int stretchResistance, boolean freeMode, int expectedLengthTenths,
                                       int returnForce, int redstoneMinLengthTenths, int redstoneMaxLengthTenths,
                                       boolean expectedLengthControlled, boolean creativeLink, boolean giantHydraulicLink) {
        return new DisplayState(
                giantHydraulicLink ? stretchResistance
                        : HydraulicConnectionHeadBlockEntity.stretchResistanceToLevel(stretchResistance),
                freeMode,
                expectedLengthTenths,
                giantHydraulicLink ? returnForce
                        : HydraulicConnectionHeadBlockEntity.returnForceToLevel(returnForce),
                redstoneMinLengthTenths,
                redstoneMaxLengthTenths,
                expectedLengthControlled,
                creativeLink,
                giantHydraulicLink);
    }

    public static void render(GuiGraphics guiGraphics, Font font, Component title, int x, int y, DisplayState state) {
        renderCreateWindow(guiGraphics, font, title, x, y);

        if (!state.creativeLink()) {
            renderSliderRow(guiGraphics, font, x, y, 0,
                    Component.translatable(state.giantHydraulicLink()
                            ? "setting.aeronautics_utility_objects.giant_hydraulic_flow"
                            : "setting.aeronautics_utility_objects.hydraulic_stretch_resistance"),
                    Component.literal(Math.round(state.stretchLevel()) + (state.giantHydraulicLink() ? " L/min" : "")),
                    state.stretchLevel(),
                    0,
                    state.giantHydraulicLink() ? GiantHydraulicSettingsState.MAX_FLOW_LITRES_PER_MINUTE
                            : HydraulicConnectionHeadBlockEntity.getMaxStretchResistanceLevel(),
                    true,
                    state.giantHydraulicLink() ? 0.0F : stretchWarningIntensity(state.stretchLevel()));
            renderModeRow(guiGraphics, font, x, y, state, 1);
        }

        renderSliderRow(guiGraphics, font, x, y, displayRow(state, 2),
                Component.translatable("setting.aeronautics_utility_objects.hydraulic_expected_length"),
                Component.literal(HydraulicConnectionHeadBlockEntity.formatTenths(Math.round(state.expectedLengthTenths()))),
                state.expectedLengthTenths(),
                HydraulicConnectionHeadBlockEntity.getMinExpectedLengthTenths(state.giantHydraulicLink()),
                HydraulicConnectionHeadBlockEntity.getMaxExpectedLengthTenths(),
                !state.freeMode() && !state.expectedLengthControlled());

        if (!state.creativeLink()) {
            renderSliderRow(guiGraphics, font, x, y, 3,
                    Component.translatable(state.giantHydraulicLink()
                            ? "setting.aeronautics_utility_objects.giant_hydraulic_pressure"
                            : "setting.aeronautics_utility_objects.hydraulic_return_force"),
                    Component.literal(Math.round(state.returnForceLevel()) + (state.giantHydraulicLink() ? " bar" : "")),
                    state.returnForceLevel(),
                    state.giantHydraulicLink() ? GiantHydraulicSettingsState.MIN_PRESSURE_BAR : 0,
                    state.giantHydraulicLink() ? GiantHydraulicSettingsState.MAX_PRESSURE_BAR
                            : HydraulicConnectionHeadBlockEntity.getMaxReturnForceLevel(),
                    !state.freeMode());
        }

        renderRangeSliderRow(guiGraphics, font, x, y, displayRow(state, REDSTONE_RANGE_ROW),
                Component.translatable("setting.aeronautics_utility_objects.hydraulic_redstone_range"),
                Component.literal(HydraulicConnectionHeadBlockEntity.formatTenths(Math.round(state.redstoneMinLengthTenths()))
                        + " - "
                        + HydraulicConnectionHeadBlockEntity.formatTenths(Math.round(state.redstoneMaxLengthTenths()))),
                state.redstoneMinLengthTenths(),
                state.redstoneMaxLengthTenths(),
                HydraulicConnectionHeadBlockEntity.getMinExpectedLengthTenths(state.giantHydraulicLink()),
                HydraulicConnectionHeadBlockEntity.getMaxExpectedLengthTenths(),
                true);
    }

    public static void renderHingeLimits(GuiGraphics guiGraphics, Font font, Component title,
                                         int x, int y, int minAngle, int maxAngle) {
        renderCreateWindow(guiGraphics, font, title, x, y, HINGE_WINDOW_HEIGHT);
        renderSliderRow(guiGraphics, font, x, y, 0,
                Component.translatable("setting.aeronautics_utility_objects.hinge_negative_limit"),
                Component.literal(Math.abs(minAngle) + "\\u00B0"), Math.abs(minAngle), 0, 180, true);
        renderSliderRow(guiGraphics, font, x, y, 1,
                Component.translatable("setting.aeronautics_utility_objects.hinge_positive_limit"),
                Component.literal(Math.abs(maxAngle) + "\\u00B0"), Math.abs(maxAngle), 0, 180, true);
        guiGraphics.drawString(font,
                Component.translatable("setting.aeronautics_utility_objects.hinge_total_travel",
                maxAngle - minAngle), x + LABEL_X, y + FIRST_ROW_Y + ROW_HEIGHT * 2 + 2,
                0xC8B27A, false);
    }

    public static boolean isHoveringHingeSlider(int x, int y, int row, double mouseX, double mouseY) {
        return isHoveringSlider(x, y, false, row, mouseX, mouseY);
    }

    private static void renderCreateWindow(GuiGraphics guiGraphics, Font font, Component title, int x, int y) {
        renderCreateWindow(guiGraphics, font, title, x, y, WINDOW_HEIGHT);
    }

    private static void renderCreateWindow(GuiGraphics guiGraphics, Font font, Component title, int x, int y, int height) {
        guiGraphics.fill(x + 4, y + 4, x + WINDOW_WIDTH - 4, y + height - 4, 0xE013171B);
        guiGraphics.fill(x + 8, y + 28, x + WINDOW_WIDTH - 8, y + height - 30, 0xA01A2026);
        renderBrassFrame(guiGraphics, x, y, WINDOW_WIDTH, height);
        guiGraphics.drawString(font, title, x + 15, y + 12, 0xE6EEF8, false);
    }

    public static int displayRow(DisplayState state, int row) {
        return displayRow(state.creativeLink(), row);
    }

    public static int displayRow(boolean creativeLink, int row) {
        if (!creativeLink) {
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

    public static boolean isHoveringSlider(int x, int y, boolean creativeLink, int row, double mouseX, double mouseY) {
        int left = x + BAR_X - 3;
        int top = y + FIRST_ROW_Y + displayRow(creativeLink, row) * ROW_HEIGHT + 8;
        return mouseX >= left && mouseX <= left + BAR_WIDTH + 6 && mouseY >= top && mouseY <= top + 18;
    }

    public static boolean isHoveringMode(int x, int y, boolean creativeLink, double mouseX, double mouseY) {
        if (creativeLink) {
            return false;
        }
        int left = x + BAR_X - 3;
        int top = y + FIRST_ROW_Y + ROW_HEIGHT + 8;
        return mouseX >= left && mouseX <= left + BAR_WIDTH + 6 && mouseY >= top && mouseY <= top + 18;
    }

    public static int valueToSliderX(int x, float value, int min, int max) {
        float progress = max == min ? 0.0F : (value - min) / (float) (max - min);
        return x + BAR_X + Math.round((BAR_WIDTH - 1) * Mth.clamp(progress, 0.0F, 1.0F));
    }

    public static float stretchWarningIntensity(float level) {
        int warningLevel = HydraulicConnectionHeadBlockEntity.getStretchResistanceWarningLevel();
        int maxLevel = HydraulicConnectionHeadBlockEntity.getMaxStretchResistanceLevel();
        if (level <= warningLevel || maxLevel <= warningLevel) {
            return 0.0F;
        }
        return Mth.clamp((level - warningLevel) / (float) (maxLevel - warningLevel), 0.0F, 1.0F);
    }

    private static void renderModeRow(GuiGraphics guiGraphics, Font font, int x, int y, DisplayState state, int row) {
        int rowY = y + FIRST_ROW_Y + row * ROW_HEIGHT;
        Component titleLabel = Component.translatable("setting.aeronautics_utility_objects.hydraulic_free_mode");

        guiGraphics.drawString(font, titleLabel, x + LABEL_X, rowY, 0xE6EEF8, false);
        int switchY = rowY + 12;
        int switchWidth = 116;
        int switchHeight = 12;
        int switchX = x + BAR_X;
        int half = switchWidth / 2;
        guiGraphics.fill(switchX, switchY, switchX + switchWidth, switchY + switchHeight, 0xFF242B32);
        guiGraphics.fill(switchX + 1, switchY + 1, switchX + switchWidth - 1, switchY + switchHeight - 1, 0xFF343D46);
        int selectedLeft = state.freeMode() ? switchX + half : switchX;
        guiGraphics.fill(selectedLeft + 1, switchY + 1, selectedLeft + half - 1, switchY + switchHeight - 1,
                state.freeMode() ? 0xFF6DAED6 : 0xFFC8B27A);
        Component targetLabel = modeFont(Component.translatable(
                "setting.aeronautics_utility_objects.hydraulic_free_mode.target"));
        Component freeLabel = modeFont(Component.translatable(
                "setting.aeronautics_utility_objects.hydraulic_free_mode.free"));
        drawCenteredStringNoShadow(guiGraphics, font, targetLabel, switchX + half / 2, switchY + 2,
                state.freeMode() ? 0xFFC5CED8 : 0xFF3F351F);
        drawCenteredStringNoShadow(guiGraphics, font, freeLabel, switchX + half + half / 2, switchY + 2,
                state.freeMode() ? 0xFF173040 : 0xFFC5CED8);
    }

    private static void renderSliderRow(GuiGraphics guiGraphics, Font font, int x, int y, int row,
                                        Component titleLabel, Component valueLabel, float value, int min, int max,
                                        boolean active) {
        renderSliderRow(guiGraphics, font, x, y, row, titleLabel, valueLabel, value, min, max, active, 0.0F);
    }

    private static void renderSliderRow(GuiGraphics guiGraphics, Font font, int x, int y, int row,
                                        Component titleLabel, Component valueLabel, float value, int min, int max,
                                        boolean active, float warningIntensity) {
        int rowY = y + FIRST_ROW_Y + row * ROW_HEIGHT;
        boolean warning = warningIntensity > 0.0F;
        int warningTextColor = lerpRgb(0xFFFFD08A, 0xFFFF4242, warningIntensity);
        int warningValueColor = lerpRgb(0xFFFF6A3D, 0xFFFF0000, warningIntensity);
        int textColor = active ? warning ? warningTextColor : 0xE6EEF8 : 0x77808A;
        int valueColor = active ? warning ? warningValueColor : 0xC8B27A : 0x77808A;
        int valueX = x + WINDOW_WIDTH - 14 - font.width(valueLabel);

        guiGraphics.drawString(font, titleLabel, x + LABEL_X, rowY, textColor, false);
        guiGraphics.drawString(font, valueLabel, valueX, rowY, valueColor, false);

        int barY = rowY + 12;
        UIRenderHelper.drawStretched(guiGraphics, x + BAR_X, barY, BAR_WIDTH, BAR_HEIGHT, 0, AllGuiTextures.VALUE_SETTINGS_BAR_BG);
        if (active) {
            renderActiveBar(guiGraphics, x, barY);
            if (warning) {
                int overlayAlpha = 0x50 + Math.round(0x50 * warningIntensity);
                int overlayRgb = lerpRgb(0xFFFF6A24, 0xFFFF0000, warningIntensity) & 0x00FFFFFF;
                guiGraphics.fill(x + BAR_X, barY + 1, x + BAR_X + BAR_WIDTH, barY + BAR_HEIGHT,
                        (overlayAlpha << 24) | overlayRgb);
                guiGraphics.fill(x + BAR_X, barY + BAR_HEIGHT, x + BAR_X + BAR_WIDTH, barY + BAR_HEIGHT + 1,
                        0xFF000000 | overlayRgb);
            }
        } else {
            guiGraphics.fill(x + BAR_X, barY + 1, x + BAR_X + BAR_WIDTH, barY + BAR_HEIGHT, 0x80505A64);
        }

        renderSliderCursor(guiGraphics, x, valueToSliderX(x, value, min, max), barY, 0);
    }

    private static void renderRangeSliderRow(GuiGraphics guiGraphics, Font font, int x, int y, int row,
                                             Component titleLabel, Component valueLabel, float minValue, float maxValue,
                                             int min, int max, boolean active) {
        int rowY = y + FIRST_ROW_Y + row * ROW_HEIGHT;
        int textColor = active ? 0xE6EEF8 : 0x77808A;
        int valueColor = active ? 0xC8B27A : 0x77808A;
        int valueX = x + WINDOW_WIDTH - 14 - font.width(valueLabel);

        guiGraphics.drawString(font, titleLabel, x + LABEL_X, rowY, textColor, false);
        guiGraphics.drawString(font, valueLabel, valueX, rowY, valueColor, false);

        int barY = rowY + 12;
        UIRenderHelper.drawStretched(guiGraphics, x + BAR_X, barY, BAR_WIDTH, BAR_HEIGHT, 0, AllGuiTextures.VALUE_SETTINGS_BAR_BG);
        if (active) {
            renderActiveBar(guiGraphics, x, barY);
        } else {
            guiGraphics.fill(x + BAR_X, barY + 1, x + BAR_X + BAR_WIDTH, barY + BAR_HEIGHT, 0x80505A64);
        }

        int minCenterX = valueToSliderX(x, minValue, min, max);
        int maxCenterX = valueToSliderX(x, maxValue, min, max);
        if (active) {
            guiGraphics.fill(minCenterX, barY + 1, maxCenterX + 1, barY + BAR_HEIGHT, 0xB0C8B27A);
            guiGraphics.fill(minCenterX, barY + BAR_HEIGHT, maxCenterX + 1, barY + BAR_HEIGHT + 1, 0xFFC8B27A);
        }
        renderSliderCursor(guiGraphics, x, minCenterX, barY, 0xFFFFD37A);
        renderSliderCursor(guiGraphics, x, maxCenterX, barY, 0xFFFFD37A);
    }

    private static void renderActiveBar(GuiGraphics guiGraphics, int x, int barY) {
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
    }

    private static void renderSliderCursor(GuiGraphics guiGraphics, int x, int centerX, int barY, int tint) {
        int cursorLeft = Mth.clamp(centerX - CURSOR_WIDTH / 2, x + BAR_X, x + BAR_X + BAR_WIDTH - CURSOR_WIDTH);
        AllGuiTextures.VALUE_SETTINGS_CURSOR_LEFT.render(guiGraphics, cursorLeft - 3, barY - 3);
        UIRenderHelper.drawCropped(guiGraphics, cursorLeft, barY - 3, CURSOR_WIDTH, 14, 0, AllGuiTextures.VALUE_SETTINGS_CURSOR);
        AllGuiTextures.VALUE_SETTINGS_CURSOR_RIGHT.render(guiGraphics, cursorLeft + CURSOR_WIDTH, barY - 3);
        if (tint != 0) {
            guiGraphics.fill(cursorLeft, barY - 1, cursorLeft + CURSOR_WIDTH, barY + 10, tint & 0x80FFFFFF);
        }
    }

    private static int lerpRgb(int from, int to, float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        int r = Mth.lerpInt(clamped, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = Mth.lerpInt(clamped, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = Mth.lerpInt(clamped, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }

    private static void drawCenteredStringNoShadow(GuiGraphics guiGraphics, Font font, Component text, int centerX, int y,
                                                   int color) {
        guiGraphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private static Component modeFont(Component text) {
        return text.copy().withStyle(style -> style.withFont(MODE_FONT));
    }

    private static void renderBrassFrame(GuiGraphics guiGraphics, int x, int y, int width, int height) {
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
