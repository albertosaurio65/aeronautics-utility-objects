package com.enxv.aerouniversaljoint.content.ponder;

import com.enxv.aerouniversaljoint.client.HydraulicConnectionHeadSettingsRenderer;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;

public class SetHydraulicSettingsOverlayInstruction extends PonderInstruction {
    private final HydraulicSettingsOverlayElement element;
    private final HydraulicConnectionHeadSettingsRenderer.DisplayState startState;
    private final HydraulicConnectionHeadSettingsRenderer.DisplayState state;
    private final int transitionTicks;
    private boolean complete;

    public SetHydraulicSettingsOverlayInstruction(HydraulicSettingsOverlayElement element,
                                                  HydraulicConnectionHeadSettingsRenderer.DisplayState state) {
        this(element, state, 24);
    }

    public SetHydraulicSettingsOverlayInstruction(HydraulicSettingsOverlayElement element,
                                                  HydraulicConnectionHeadSettingsRenderer.DisplayState state,
                                                  int transitionTicks) {
        this(element, null, state, transitionTicks);
    }

    public SetHydraulicSettingsOverlayInstruction(HydraulicSettingsOverlayElement element,
                                                  HydraulicConnectionHeadSettingsRenderer.DisplayState startState,
                                                  HydraulicConnectionHeadSettingsRenderer.DisplayState state,
                                                  int transitionTicks) {
        this.element = element;
        this.startState = startState;
        this.state = state;
        this.transitionTicks = transitionTicks;
    }

    @Override
    public boolean isComplete() {
        return this.complete;
    }

    @Override
    public void reset(PonderScene scene) {
        super.reset(scene);
        this.complete = false;
    }

    @Override
    public void tick(PonderScene scene) {
        if (this.startState != null) {
            this.element.snapToState(this.startState);
        }
        this.element.transitionToState(this.state, this.transitionTicks);
        this.complete = true;
    }
}
