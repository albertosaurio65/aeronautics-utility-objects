package com.enxv.aerouniversaljoint.content;

import net.createmod.catnip.theme.Color;

final class LinkVisualEffects {
    private LinkVisualEffects() {
    }

    static Color effectColor(float effect) {
        if (effect == 0.0F) {
            return Color.WHITE;
        }
        boolean strained = effect > 0.0F;
        Color color = strained ? Color.RED : Color.SPRING_GREEN;
        float weight = strained ? effect : -effect;
        return Color.WHITE.mixWith(color, weight);
    }

}
