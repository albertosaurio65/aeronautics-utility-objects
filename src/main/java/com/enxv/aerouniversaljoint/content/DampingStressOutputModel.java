package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.AeroUniversalJointConfig;

public final class DampingStressOutputModel {
    private DampingStressOutputModel() {
    }

    public static float calculateCapacity(float speedRpm, int resistance, int maxResistance, boolean suppressed) {
        float speedMagnitude = Math.abs(speedRpm);
        float ratedOutputSpeed = AeroUniversalJointConfig.dampingRatedOutputSpeedRpm();
        float maxFullSpeedStressOutput = AeroUniversalJointConfig.dampingMaxFullSpeedStressOutput();
        if (!Float.isFinite(speedMagnitude) || speedMagnitude <= 1.0E-3F
                || ratedOutputSpeed <= 0.0F || maxFullSpeedStressOutput <= 0.0F) {
            return 0.0F;
        }

        int boundedMaxResistance = Math.max(1, maxResistance);
        int boundedResistance = Math.max(0, Math.min(boundedMaxResistance, resistance));
        float coefficient = maxFullSpeedStressOutput / boundedMaxResistance;
        float requestedCapacity = speedMagnitude / ratedOutputSpeed * boundedResistance * coefficient;
        float cappedCapacity = Math.min(maxFullSpeedStressOutput, requestedCapacity);
        return suppressed
                ? Math.min(AeroUniversalJointConfig.dampingSuppressedStressOutput(), cappedCapacity)
                : cappedCapacity;
    }

    public static float calculateFullSpeedCapacity(int resistance, int maxResistance) {
        int boundedMaxResistance = Math.max(1, maxResistance);
        int boundedResistance = Math.max(0, Math.min(boundedMaxResistance, resistance));
        return AeroUniversalJointConfig.dampingMaxFullSpeedStressOutput()
                * boundedResistance / boundedMaxResistance;
    }
}
