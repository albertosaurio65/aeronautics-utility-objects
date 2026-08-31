package com.enxv.aerouniversaljoint.content.ponder;

import net.createmod.ponder.foundation.instruction.FadeIntoSceneInstruction;
import net.minecraft.core.Direction;

public class CreateUniversalJointLinkInstruction extends FadeIntoSceneInstruction<UniversalJointLinkElement> {
    public CreateUniversalJointLinkInstruction(int fadeTicks, Direction fadeInFrom, UniversalJointLinkElement element) {
        super(fadeTicks, fadeInFrom, element);
    }

    public CreateUniversalJointLinkInstruction(UniversalJointLinkElement element) {
        this(10, Direction.DOWN, element);
    }

    @Override
    protected Class<UniversalJointLinkElement> getElementClass() {
        return UniversalJointLinkElement.class;
    }
}
