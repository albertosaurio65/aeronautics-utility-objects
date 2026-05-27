package com.enxv.aerouniversaljoint.access;

public interface KineticVisualEffectAccess {
    void aeronautics$triggerLinkStrainEffect(boolean strained);

    float aeronautics$getLinkStrainEffect();

    void aeronautics$tickLinkStrainEffect();
}
