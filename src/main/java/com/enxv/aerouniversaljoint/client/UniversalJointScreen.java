package com.enxv.aerouniversaljoint.client;

import com.enxv.aerouniversaljoint.content.UniversalJointBlockEntity;
import com.enxv.aerouniversaljoint.content.UniversalJointMenu;
import com.enxv.aerouniversaljoint.network.SetJointSpeedRatioPayload;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class UniversalJointScreen extends AbstractContainerScreen<UniversalJointMenu> {
    private static final int WINDOW_WIDTH = 196;
    private static final int WINDOW_HEIGHT = 92;
    private static final int LABEL_X = 14;
    private static final int LABEL_Y = 31;
    private static final int BAR_X = 14;
    private static final int BAR_Y = 48;
    private static final int BAR_WIDTH = 168;
    private static final int BAR_HEIGHT = 8;
    private static final int CURSOR_WIDTH = 14;

    private IconButton confirmButton;
    private float sliderValue;
    private boolean draggingSlider;

    public UniversalJointScreen(UniversalJointMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WINDOW_WIDTH;
        this.imageHeight = WINDOW_HEIGHT;
        this.inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        super.init();
        this.sliderValue = this.menu.getCurrentSpeedRatio();

        this.confirmButton = new IconButton(this.leftPos + this.imageWidth - 33, this.topPos + this.imageHeight - 24, AllIcons.I_CONFIRM);
        this.confirmButton.withCallback(this::onClose);
        this.addRenderableWidget(this.confirmButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!this.draggingSlider) {
            this.sliderValue = this.menu.getCurrentSpeedRatio();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x + 4, y + 4, x + this.imageWidth - 4, y + this.imageHeight - 4, 0xE013171B);
        guiGraphics.fill(x + 8, y + 28, x + this.imageWidth - 8, y + this.imageHeight - 30, 0xA01A2026);
        this.renderBrassFrame(guiGraphics, x, y, this.imageWidth, this.imageHeight);
        this.renderSliderRow(guiGraphics, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 15, 12, 0xE6EEF8, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isHoveringSlider(mouseX, mouseY)) {
            this.draggingSlider = true;
            this.updateSliderFromMouse(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingSlider && button == 0) {
            this.updateSliderFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingSlider = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isHoveringSlider(mouseX, mouseY)) {
            float step = 1.0F;
            float direction = scrollY > 0 ? 1 : -1;
            this.applySliderValue(this.sliderValue + direction * step);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderSliderRow(GuiGraphics guiGraphics, int x, int y) {
        Component titleLabel = Component.translatable("tooltip.aeronautics_utility_objects.speed_ratio");
        Component valueLabel = UniversalJointBlockEntity.describeSpeedRatio(this.sliderValue);
        int labelY = y + LABEL_Y;
        int barY = y + BAR_Y;
        int valueX = x + this.imageWidth - 14 - this.font.width(valueLabel);

        guiGraphics.drawString(this.font, titleLabel, x + LABEL_X, labelY, 0xE6EEF8, false);
        guiGraphics.drawString(this.font, valueLabel, valueX, labelY, 0xC8B27A, false);

        UIRenderHelper.drawStretched(guiGraphics, x + BAR_X, barY, BAR_WIDTH, BAR_HEIGHT, 0, AllGuiTextures.VALUE_SETTINGS_BAR_BG);
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

        for (int i = 0; i <= 8; i++) {
            int markerX = x + BAR_X + Math.round((BAR_WIDTH - 1) * (i / 8.0F));
            AllGuiTextures.VALUE_SETTINGS_MILESTONE.render(guiGraphics, markerX, barY + 1);
        }

        float normalizedValue = (this.sliderValue + 4.0F) / 8.0F;
        int centerX = x + BAR_X + Math.round((BAR_WIDTH - 1) * normalizedValue);
        int cursorLeft = Mth.clamp(centerX - CURSOR_WIDTH / 2, x + BAR_X, x + BAR_X + BAR_WIDTH - CURSOR_WIDTH);

        AllGuiTextures.VALUE_SETTINGS_CURSOR_LEFT.render(guiGraphics, cursorLeft - 3, barY - 3);
        UIRenderHelper.drawCropped(guiGraphics, cursorLeft, barY - 3, CURSOR_WIDTH, 14, 0, AllGuiTextures.VALUE_SETTINGS_CURSOR);
        AllGuiTextures.VALUE_SETTINGS_CURSOR_RIGHT.render(guiGraphics, cursorLeft + CURSOR_WIDTH, barY - 3);
    }

    private void updateSliderFromMouse(double mouseX) {
        float progress = (float) ((mouseX - (this.leftPos + BAR_X)) / (BAR_WIDTH - 1.0D));
        float value = Mth.clamp(progress, 0.0F, 1.0F) * 8.0F - 4.0F;
        this.applySliderValue(value);
    }

    private void applySliderValue(float value) {
        float clamped = Mth.clamp(value, UniversalJointBlockEntity.getMinSpeedRatio(), UniversalJointBlockEntity.getMaxSpeedRatio());
        clamped = Math.round(clamped);
        
        if (Math.abs(clamped - this.sliderValue) < 0.001F) {
            return;
        }

        this.sliderValue = clamped;
        PacketDistributor.sendToServer(new SetJointSpeedRatioPayload(this.menu.getBlockPos(), clamped));
    }

    private boolean isHoveringSlider(double mouseX, double mouseY) {
        int left = this.leftPos + BAR_X - 3;
        int top = this.topPos + BAR_Y - 4;
        return mouseX >= left && mouseX <= left + BAR_WIDTH + 6 && mouseY >= top && mouseY <= top + 18;
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
