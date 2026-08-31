package com.enxv.aerouniversaljoint.content.ponder;

import com.enxv.aerouniversaljoint.client.HydraulicConnectionHeadSettingsRenderer;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class HydraulicSettingsOverlayElement extends AnimatedOverlayElementBase {
    private static final float SCALE = 0.58F;
    private static final int EDGE_MARGIN = 10;

    private HydraulicConnectionHeadSettingsRenderer.DisplayState previousState;
    private HydraulicConnectionHeadSettingsRenderer.DisplayState currentState;
    private HydraulicConnectionHeadSettingsRenderer.DisplayState transitionStartState;
    private HydraulicConnectionHeadSettingsRenderer.DisplayState transitionTargetState;
    private int transitionTicks;
    private int transitionDuration;

    public HydraulicSettingsOverlayElement(HydraulicConnectionHeadSettingsRenderer.DisplayState initialState) {
        this.previousState = initialState;
        this.currentState = initialState;
        this.transitionStartState = initialState;
        this.transitionTargetState = initialState;
    }

    @Override
    public void tick(PonderScene scene) {
        this.previousState = this.currentState;
        if (this.transitionTicks <= 0) {
            return;
        }

        this.transitionTicks--;
        this.currentState = this.interpolateTransition(1.0F - this.transitionTicks / (float) this.transitionDuration);
    }

    @Override
    public void whileSkipping(PonderScene scene) {
        this.previousState = this.transitionTargetState;
        this.transitionTicks = 0;
        this.currentState = this.transitionTargetState;
        this.transitionStartState = this.transitionTargetState;
    }

    public void setState(HydraulicConnectionHeadSettingsRenderer.DisplayState state, int durationTicks) {
        this.transitionToState(state, durationTicks);
    }

    public void snapToState(HydraulicConnectionHeadSettingsRenderer.DisplayState state) {
        this.previousState = state;
        this.currentState = state;
        this.transitionStartState = state;
        this.transitionTargetState = state;
        this.transitionTicks = 0;
        this.transitionDuration = 1;
    }

    public void transitionToState(HydraulicConnectionHeadSettingsRenderer.DisplayState state, int durationTicks) {
        if (durationTicks <= 0) {
            this.snapToState(state);
            return;
        }

        this.previousState = this.currentState;
        this.transitionStartState = this.currentState;
        this.transitionTargetState = state;
        this.transitionDuration = Math.max(1, durationTicks);
        this.transitionTicks = this.transitionDuration;
    }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks, float fade) {
        float alpha = this.getFade(partialTicks) * fade;
        if (alpha <= 0.01F) {
            return;
        }

        int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int x = Math.max(EDGE_MARGIN, guiWidth - EDGE_MARGIN
                - Math.round(HydraulicConnectionHeadSettingsRenderer.WINDOW_WIDTH * SCALE));
        int y = EDGE_MARGIN + 18;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 220.0F);
        graphics.pose().scale(SCALE, SCALE, 1.0F);
        HydraulicConnectionHeadSettingsRenderer.render(graphics, Minecraft.getInstance().font,
                Component.translatable("aeronautics_utility_objects.ponder.hydraulic_rod_settings.header"),
                0,
                0,
                this.lerpState(partialTicks));
        graphics.pose().popPose();
    }

    private HydraulicConnectionHeadSettingsRenderer.DisplayState lerpState(float partialTicks) {
        float progress = Mth.clamp(partialTicks, 0.0F, 1.0F);
        return new HydraulicConnectionHeadSettingsRenderer.DisplayState(
                Mth.lerp(progress, this.previousState.stretchLevel(), this.currentState.stretchLevel()),
                this.currentState.freeMode(),
                Mth.lerp(progress, this.previousState.expectedLengthTenths(), this.currentState.expectedLengthTenths()),
                Mth.lerp(progress, this.previousState.returnForceLevel(), this.currentState.returnForceLevel()),
                Mth.lerp(progress, this.previousState.redstoneMinLengthTenths(), this.currentState.redstoneMinLengthTenths()),
                Mth.lerp(progress, this.previousState.redstoneMaxLengthTenths(), this.currentState.redstoneMaxLengthTenths()),
                this.currentState.expectedLengthControlled(),
                this.currentState.creativeLink(),
                false);
    }

    private HydraulicConnectionHeadSettingsRenderer.DisplayState interpolateTransition(float progress) {
        float smoothed = smooth(Mth.clamp(progress, 0.0F, 1.0F));
        return new HydraulicConnectionHeadSettingsRenderer.DisplayState(
                Mth.lerp(smoothed, this.transitionStartState.stretchLevel(), this.transitionTargetState.stretchLevel()),
                this.transitionTargetState.freeMode(),
                Mth.lerp(smoothed, this.transitionStartState.expectedLengthTenths(), this.transitionTargetState.expectedLengthTenths()),
                Mth.lerp(smoothed, this.transitionStartState.returnForceLevel(), this.transitionTargetState.returnForceLevel()),
                Mth.lerp(smoothed, this.transitionStartState.redstoneMinLengthTenths(), this.transitionTargetState.redstoneMinLengthTenths()),
                Mth.lerp(smoothed, this.transitionStartState.redstoneMaxLengthTenths(), this.transitionTargetState.redstoneMaxLengthTenths()),
                this.transitionTargetState.expectedLengthControlled(),
                this.transitionTargetState.creativeLink(),
                false);
    }

    private static float smooth(float progress) {
        return progress * progress * (3.0F - 2.0F * progress);
    }
}
