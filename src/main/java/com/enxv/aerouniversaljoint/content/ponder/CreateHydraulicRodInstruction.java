package com.enxv.aerouniversaljoint.content.ponder;

import net.createmod.ponder.foundation.instruction.FadeIntoSceneInstruction;
import net.minecraft.core.Direction;

public class CreateHydraulicRodInstruction extends FadeIntoSceneInstruction<HydraulicRodElement> {
    public CreateHydraulicRodInstruction(int fadeTicks, Direction fadeInFrom, HydraulicRodElement element) {
        super(fadeTicks, fadeInFrom, element);
    }

    public CreateHydraulicRodInstruction(HydraulicRodElement element) {
        this(10, Direction.DOWN, element);
    }

    @Override
    protected Class<HydraulicRodElement> getElementClass() {
        return HydraulicRodElement.class;
    }
}
