package com.enxv.aerouniversaljoint.content;

/** Client-side geometry state for the giant rod. */
public record GiantHydraulicRodVisualState(
        double thickOffset,
        double mediumOffset,
        double thinOffset,
        double continuousExtension,
        double continuousScale) {
    /** The fixed barrel ends at pixel 40; each moving stage travels that far. */
    public static final double STAGED_LENGTH = 40.0D / 16.0D;
    public static final double CONTINUOUS_LENGTH = 8.0D;
    /** The moving rod keeps a one-pixel insertion overlap, so its mesh is 41 pixels long. */
    public static final double NOMINAL_ROD_LENGTH = 41.0D / 16.0D;
    /** The collapsed assembly occupies exactly one moving-rod length. */
    public static final double BASE_LENGTH = NOMINAL_ROD_LENGTH;

    public static GiantHydraulicRodVisualState fromDistance(double distance) {
        double safeDistance = Double.isFinite(distance) ? Math.max(0.0D, distance) : 0.0D;
        double extension = Math.max(0.0D, safeDistance - BASE_LENGTH);
        double stageOne = clamp(extension, 0.0D, STAGED_LENGTH);
        double stageTwo = clamp(extension - STAGED_LENGTH, 0.0D, STAGED_LENGTH);
        double stageThree = clamp(extension - STAGED_LENGTH * 2.0D, 0.0D, STAGED_LENGTH);
        double continuous = clamp(extension - STAGED_LENGTH * 3.0D, 0.0D, CONTINUOUS_LENGTH);
        double extensionPerStage = continuous / 3.0D;
        double scale = 1.0D + extensionPerStage / NOMINAL_ROD_LENGTH;
        return new GiantHydraulicRodVisualState(
                stageOne,
                stageOne + stageTwo + extensionPerStage,
                stageOne + stageTwo + stageThree + extensionPerStage * 2.0D,
                continuous,
                scale);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
