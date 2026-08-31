package com.enxv.aerouniversaljoint.content.hydraulic;

import com.enxv.aerouniversaljoint.AeroUniversalJointConfig;

/** Persistent settings for a hydraulic link endpoint. */
public final class HydraulicSettingsState {
    private int stretchResistance;
    private boolean freeMode;
    private int expectedLengthTenths;
    private int redstoneMinLengthTenths;
    private int redstoneMaxLengthTenths;
    private int returnForce;

    public HydraulicSettingsState() {
        this.resetToDefaults();
    }

    public void resetToDefaults() {
        this.stretchResistance = AeroUniversalJointConfig.hydraulicRodDefaultStretchResistance();
        this.freeMode = false;
        this.expectedLengthTenths = HydraulicSettings.minExpectedLengthTenths();
        this.redstoneMinLengthTenths = HydraulicSettings.minExpectedLengthTenths();
        this.redstoneMaxLengthTenths = HydraulicSettings.maxExpectedLengthTenths();
        this.returnForce = AeroUniversalJointConfig.hydraulicRodDefaultReturnForce();
    }

    public Change applyBaseSettings(int stretchResistance, boolean freeMode, int expectedLengthTenths, int returnForce) {
        int boundedStretchResistance = HydraulicSettings.clampStretchResistance(stretchResistance);
        int boundedExpectedLength = HydraulicSettings.clampExpectedLengthTenths(expectedLengthTenths);
        int boundedReturnForce = HydraulicSettings.clampReturnForce(returnForce);
        boolean changed = this.stretchResistance != boundedStretchResistance
                || this.freeMode != freeMode
                || this.expectedLengthTenths != boundedExpectedLength
                || this.returnForce != boundedReturnForce;
        boolean leftFreeMode = this.freeMode && !freeMode;
        this.stretchResistance = boundedStretchResistance;
        this.freeMode = freeMode;
        this.expectedLengthTenths = boundedExpectedLength;
        this.returnForce = boundedReturnForce;
        return new Change(changed, leftFreeMode);
    }

    public boolean applyRedstoneLengthRange(int minLengthTenths, int maxLengthTenths) {
        int boundedMin = HydraulicSettings.clampExpectedLengthTenths(minLengthTenths);
        int boundedMax = HydraulicSettings.clampExpectedLengthTenths(maxLengthTenths);
        if (boundedMin > boundedMax) {
            int swapped = boundedMin;
            boundedMin = boundedMax;
            boundedMax = swapped;
        }

        boolean changed = this.redstoneMinLengthTenths != boundedMin || this.redstoneMaxLengthTenths != boundedMax;
        this.redstoneMinLengthTenths = boundedMin;
        this.redstoneMaxLengthTenths = boundedMax;
        return changed;
    }

    public boolean normalize() {
        Change baseChange = this.applyBaseSettings(this.stretchResistance, this.freeMode,
                this.expectedLengthTenths, this.returnForce);
        return baseChange.changed()
                || this.applyRedstoneLengthRange(this.redstoneMinLengthTenths, this.redstoneMaxLengthTenths);
    }

    public int stretchResistance() {
        return this.stretchResistance;
    }

    public boolean freeMode() {
        return this.freeMode;
    }

    public int expectedLengthTenths() {
        return this.expectedLengthTenths;
    }

    public int redstoneMinLengthTenths() {
        return this.redstoneMinLengthTenths;
    }

    public int redstoneMaxLengthTenths() {
        return this.redstoneMaxLengthTenths;
    }

    public int returnForce() {
        return this.returnForce;
    }

    public record Change(boolean changed, boolean leftFreeMode) {
    }
}
