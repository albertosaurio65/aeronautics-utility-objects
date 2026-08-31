package com.enxv.aerouniversaljoint.content.ponder;

import net.createmod.ponder.foundation.instruction.FadeInOutInstruction;
import net.createmod.ponder.foundation.PonderScene;

public class CreateHydraulicSettingsOverlayInstruction extends FadeInOutInstruction {
    private final HydraulicSettingsOverlayElement element;

    public CreateHydraulicSettingsOverlayInstruction(int ticks, HydraulicSettingsOverlayElement element) {
        super(ticks);
        this.element = element;
    }

    @Override
    protected void show(PonderScene scene) {
        scene.addElement(this.element);
        this.element.setVisible(true);
    }

    @Override
    protected void hide(PonderScene scene) {
        this.element.setVisible(false);
    }

    @Override
    protected void applyFade(PonderScene scene, float fade) {
        this.element.setFade(fade);
    }
}
