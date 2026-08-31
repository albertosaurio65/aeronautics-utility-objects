package com.enxv.aerouniversaljoint.content.hydraulic;

import com.enxv.aerouniversaljoint.AeroUniversalJointConfig;

public final class HydraulicLengthControl {
    private static final double MIN_DISTANCE = 1.0E-4D;
    private static final double MIN_IMPULSE = 1.0E-8D;

    private HydraulicLengthControl() {
    }

    public static double calculateReturnForce(double returnForce, double deviation) {
        double clampedForce = clamp(returnForce, 0.0D, AeroUniversalJointConfig.hydraulicRodMaxReturnForce());
        double magnitude = Math.abs(deviation);
        if (clampedForce <= 0.0D || magnitude <= MIN_DISTANCE) {
            return 0.0D;
        }

        return Math.signum(deviation)
                * clampedForce
                * AeroUniversalJointConfig.hydraulicRodReturnForcePerUnit()
                * Math.expm1(magnitude * AeroUniversalJointConfig.hydraulicRodReturnForceCurve());
    }

    public static double calculateReturnImpulse(double returnForce, double distance, double targetDistance, double timeStep) {
        if (returnForce <= 0.0D || timeStep <= 0.0D) {
            return 0.0D;
        }

        double impulse = calculateReturnForce(returnForce, distance - targetDistance) * timeStep;
        return Math.abs(impulse) <= MIN_IMPULSE
                ? 0.0D
                : clampImpulse(impulse, AeroUniversalJointConfig.hydraulicRodMaxExpectedReturnImpulse());
    }

    public static double calculateHardLimitImpulse(double distance, double timeStep) {
        return calculateHardLimitImpulse(distance, timeStep,
                AeroUniversalJointConfig.hydraulicRodMinLinkLength(),
                AeroUniversalJointConfig.hydraulicRodMaxLinkLength());
    }

    public static double calculateHardLimitImpulse(double distance, double timeStep,
                                                   double minLength, double maxLength) {
        if (timeStep <= 0.0D) {
            return 0.0D;
        }

        double excess;
        double direction;
        if (distance < minLength) {
            excess = minLength - distance;
            direction = -1.0D;
        } else if (distance > maxLength) {
            excess = distance - maxLength;
            direction = 1.0D;
        } else {
            return 0.0D;
        }

        double impulse = AeroUniversalJointConfig.hydraulicRodLengthLimitStiffness()
                * Math.expm1(excess * AeroUniversalJointConfig.hydraulicRodLengthLimitCurve())
                * timeStep;
        return direction * Math.min(impulse, AeroUniversalJointConfig.hydraulicRodMaxLengthLimitImpulse());
    }

    public static double approach(double current, double target, double maxStep) {
        return current < target ? Math.min(current + maxStep, target) : Math.max(current - maxStep, target);
    }

    public static double clampImpulse(double impulse, double maxMagnitude) {
        return clamp(impulse, -maxMagnitude, maxMagnitude);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
