package com.enxv.aerouniversaljoint.content.hydraulic;

import com.enxv.aerouniversaljoint.AeroUniversalJointConfig;
import java.util.Locale;

public final class HydraulicSettings {
    private static final double MIN_APPROACH_MULTIPLIER = 0.01D;
    private static final double MAX_APPROACH_MULTIPLIER = 64.0D;
    private static final int MAX_STRETCH_RESISTANCE_LEVEL = 20;
    private static final int STRETCH_RESISTANCE_WARNING_LEVEL = 10;
    private static final int MAX_RETURN_FORCE_LEVEL = 20;

    private HydraulicSettings() {
    }

    public static int clampStretchResistance(int value) {
        return Math.max(0, Math.min(maxStretchResistance(), value));
    }

    public static int clampReturnForce(int value) {
        return Math.max(0, Math.min(maxReturnForce(), value));
    }

    public static int clampExpectedLengthTenths(int value) {
        return Math.max(minExpectedLengthTenths(), Math.min(maxExpectedLengthTenths(), value));
    }

    public static double clampApproachMultiplier(double value) {
        if (!Double.isFinite(value)) {
            return 1.0D;
        }
        return Math.max(MIN_APPROACH_MULTIPLIER, Math.min(MAX_APPROACH_MULTIPLIER, value));
    }

    public static int maxStretchResistance() {
        return AeroUniversalJointConfig.hydraulicRodMaxStretchResistance();
    }

    public static int maxStretchResistanceLevel() {
        return MAX_STRETCH_RESISTANCE_LEVEL;
    }

    public static int stretchResistanceWarningLevel() {
        return STRETCH_RESISTANCE_WARNING_LEVEL;
    }

    public static int stretchResistanceFromLevel(int level) {
        int clampedLevel = Math.max(0, Math.min(maxStretchResistanceLevel(), level));
        if (clampedLevel <= 0) {
            return 0;
        }
        if (clampedLevel <= stretchResistanceWarningLevel()) {
            double normalized = clampedLevel / (double) stretchResistanceWarningLevel();
            return clampStretchResistance((int) Math.round(1024.0D * normalized * normalized));
        }
        double normalized = (clampedLevel - stretchResistanceWarningLevel())
                / (double) (maxStretchResistanceLevel() - stretchResistanceWarningLevel());
        double maxResistance = Math.max(1024.0D, maxStretchResistance());
        return clampStretchResistance((int) Math.round(1024.0D * Math.pow(maxResistance / 1024.0D, normalized)));
    }

    public static int stretchResistanceToLevel(int resistance) {
        int clampedResistance = clampStretchResistance(resistance);
        if (clampedResistance <= 0) {
            return 0;
        }
        int midpointResistance = Math.min(1024, maxStretchResistance());
        if (clampedResistance <= midpointResistance) {
            double normalized = Math.sqrt(clampedResistance / (double) Math.max(1, midpointResistance));
            return Math.max(0, Math.min(stretchResistanceWarningLevel(),
                    (int) Math.round(normalized * stretchResistanceWarningLevel())));
        }
        double maxResistance = Math.max(1025.0D, maxStretchResistance());
        double normalized = Math.log(clampedResistance / 1024.0D) / Math.log(maxResistance / 1024.0D);
        double level = stretchResistanceWarningLevel() + Math.max(0.0D, normalized)
                * (maxStretchResistanceLevel() - stretchResistanceWarningLevel());
        return Math.max(stretchResistanceWarningLevel(), Math.min(maxStretchResistanceLevel(), (int) Math.round(level)));
    }

    public static int maxReturnForce() {
        return AeroUniversalJointConfig.hydraulicRodMaxReturnForce();
    }

    public static int maxReturnForceLevel() {
        return MAX_RETURN_FORCE_LEVEL;
    }

    public static int returnForceFromLevel(int level) {
        int clampedLevel = Math.max(0, Math.min(maxReturnForceLevel(), level));
        double normalized = clampedLevel / (double) maxReturnForceLevel();
        return clampReturnForce((int) Math.round(maxReturnForce() * normalized * normalized));
    }

    public static int returnForceToLevel(int force) {
        int clampedForce = clampReturnForce(force);
        if (clampedForce <= 0) {
            return 0;
        }
        double normalized = Math.sqrt(clampedForce / (double) Math.max(1, maxReturnForce()));
        return Math.max(0, Math.min(maxReturnForceLevel(), (int) Math.round(normalized * maxReturnForceLevel())));
    }

    public static int minExpectedLengthTenths() {
        return (int) Math.round(AeroUniversalJointConfig.hydraulicRodMinLinkLength() * 10.0D);
    }

    public static int maxExpectedLengthTenths() {
        return Math.max(minExpectedLengthTenths() + 1,
                (int) Math.round(AeroUniversalJointConfig.hydraulicRodMaxLinkLength() * 10.0D));
    }

    public static String formatTenths(int value) {
        int clamped = clampExpectedLengthTenths(value);
        return clamped % 10 == 0 ? Integer.toString(clamped / 10)
                : String.format(Locale.ROOT, "%.1f", clamped / 10.0D);
    }
}
