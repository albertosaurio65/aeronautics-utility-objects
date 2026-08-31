package com.enxv.aerouniversaljoint.content.hydraulic;

/** Persistent user settings for a giant hydraulic cylinder. */
public final class GiantHydraulicSettingsState {
    public static final int MIN_FLOW_LITRES_PER_MINUTE = 0;
    public static final int MIN_ACTIVE_FLOW_LITRES_PER_MINUTE = 1;
    public static final int MAX_FLOW_LITRES_PER_MINUTE = 40;
    public static final int DEFAULT_FLOW_LITRES_PER_MINUTE = 24;
    public static final int MIN_PRESSURE_BAR = 25;
    public static final int MAX_PRESSURE_BAR = 400;
    public static final int DEFAULT_PRESSURE_BAR = 350;

    private int flowLitresPerMinute = DEFAULT_FLOW_LITRES_PER_MINUTE;
    private boolean vented;
    private int targetLengthTenths = 30;
    private int pressureBar = DEFAULT_PRESSURE_BAR;
    private int redstoneMinLengthTenths = 30;
    private int redstoneMaxLengthTenths = 150;

    public boolean applyBase(int flowLitresPerMinute, boolean vented, int targetLengthTenths, int pressureBar) {
        int flow = clampFlow(flowLitresPerMinute);
        int target = Math.max(30, targetLengthTenths);
        int pressure = clampPressure(pressureBar);
        boolean changed = this.flowLitresPerMinute != flow || this.vented != vented
                || this.targetLengthTenths != target || this.pressureBar != pressure;
        this.flowLitresPerMinute = flow;
        this.vented = vented;
        this.targetLengthTenths = target;
        this.pressureBar = pressure;
        return changed;
    }

    public boolean applyRedstoneRange(int minLengthTenths, int maxLengthTenths) {
        int min = Math.max(30, minLengthTenths);
        int max = Math.max(min, maxLengthTenths);
        boolean changed = this.redstoneMinLengthTenths != min || this.redstoneMaxLengthTenths != max;
        this.redstoneMinLengthTenths = min;
        this.redstoneMaxLengthTenths = max;
        return changed;
    }

    public int flowLitresPerMinute() { return this.flowLitresPerMinute; }
    public boolean vented() { return this.vented; }
    public int targetLengthTenths() { return this.targetLengthTenths; }
    public int pressureBar() { return this.pressureBar; }
    public int redstoneMinLengthTenths() { return this.redstoneMinLengthTenths; }
    public int redstoneMaxLengthTenths() { return this.redstoneMaxLengthTenths; }

    public static int clampFlow(int value) {
        return Math.max(MIN_FLOW_LITRES_PER_MINUTE, Math.min(MAX_FLOW_LITRES_PER_MINUTE, value));
    }

    public static int clampPressure(int value) {
        return Math.max(MIN_PRESSURE_BAR, Math.min(MAX_PRESSURE_BAR, value));
    }
}
