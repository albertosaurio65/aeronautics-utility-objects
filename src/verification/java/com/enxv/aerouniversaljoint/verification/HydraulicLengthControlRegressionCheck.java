package com.enxv.aerouniversaljoint.verification;

import com.enxv.aerouniversaljoint.AeroUniversalJointConfig;
import com.enxv.aerouniversaljoint.content.DampingStressOutputModel;
import com.enxv.aerouniversaljoint.content.GiantHydraulicRodVisualState;
import com.enxv.aerouniversaljoint.content.hydraulic.HydraulicLengthControl;
import com.enxv.aerouniversaljoint.content.hydraulic.GiantHydraulicPhysics;
import com.enxv.aerouniversaljoint.content.hydraulic.HydraulicCylinderControl;
import com.enxv.aerouniversaljoint.content.hydraulic.HydraulicSettings;
import com.enxv.aerouniversaljoint.content.hydraulic.HydraulicSettingsState;

public final class HydraulicLengthControlRegressionCheck {
    private HydraulicLengthControlRegressionCheck() {
    }

    public static void main(String[] args) {
        verifyApproach();
        verifyReturnForceDirection();
        verifyHardLengthLimits();
        verifyImpulseCaps();
        verifyDampingStressOutput();
        verifyHydraulicSettings();
        verifyHydraulicSettingsState();
        verifyGiantHydraulicVisualStages();
        verifyGiantHydraulicNoWindup();
    }

    private static void verifyApproach() {
        requireEqual(3.0D, HydraulicLengthControl.approach(1.0D, 5.0D, 2.0D), "approach must advance by maxStep");
        requireEqual(5.0D, HydraulicLengthControl.approach(4.0D, 5.0D, 2.0D), "approach must not overshoot upward");
        requireEqual(3.0D, HydraulicLengthControl.approach(5.0D, 1.0D, 2.0D), "approach must retreat by maxStep");
    }

    private static void verifyReturnForceDirection() {
        double force = AeroUniversalJointConfig.hydraulicRodDefaultReturnForce();
        require(HydraulicLengthControl.calculateReturnForce(force, 1.0D) > 0.0D,
                "positive deviation must produce a positive restoring impulse direction");
        require(HydraulicLengthControl.calculateReturnForce(force, -1.0D) < 0.0D,
                "negative deviation must produce a negative restoring impulse direction");
        requireEqual(0.0D, HydraulicLengthControl.calculateReturnForce(force, 0.0D),
                "zero deviation must produce no restoring force");
    }

    private static void verifyHardLengthLimits() {
        double min = AeroUniversalJointConfig.hydraulicRodMinLinkLength();
        double max = AeroUniversalJointConfig.hydraulicRodMaxLinkLength();
        double timeStep = 1.0D / 20.0D;
        require(HydraulicLengthControl.calculateHardLimitImpulse(min - 1.0D, timeStep) < 0.0D,
                "compressed rods must receive a negative limit impulse");
        require(HydraulicLengthControl.calculateHardLimitImpulse(max + 1.0D, timeStep) > 0.0D,
                "stretched rods must receive a positive limit impulse");
        requireEqual(0.0D, HydraulicLengthControl.calculateHardLimitImpulse((min + max) * 0.5D, timeStep),
                "normal rod lengths must receive no hard-limit impulse");
    }

    private static void verifyImpulseCaps() {
        double cap = AeroUniversalJointConfig.hydraulicRodMaxExpectedReturnImpulse();
        requireEqual(cap, HydraulicLengthControl.clampImpulse(Double.MAX_VALUE, cap),
                "positive impulses must be capped");
        requireEqual(-cap, HydraulicLengthControl.clampImpulse(-Double.MAX_VALUE, cap),
                "negative impulses must be capped");
    }

    private static void verifyDampingStressOutput() {
        float fullSpeed = DampingStressOutputModel.calculateFullSpeedCapacity(256, 256);
        requireEqual(AeroUniversalJointConfig.dampingMaxFullSpeedStressOutput(), fullSpeed,
                "maximum damping resistance must produce full configured stress capacity");
        requireEqual(0.0D, DampingStressOutputModel.calculateCapacity(Float.NaN, 256, 256, false),
                "non-finite physics speed must not enter Create's stress network");
        require(DampingStressOutputModel.calculateCapacity(64.0F, 256, 256, true)
                        <= AeroUniversalJointConfig.dampingSuppressedStressOutput(),
                "suppressed damping output must respect its configured cap");
    }

    private static void verifyHydraulicSettings() {
        int minLength = HydraulicSettings.minExpectedLengthTenths();
        int maxLength = HydraulicSettings.maxExpectedLengthTenths();
        require(minLength < maxLength, "hydraulic expected-length range must be ordered");
        requireEqual(minLength, HydraulicSettings.clampExpectedLengthTenths(Integer.MIN_VALUE),
                "expected length must clamp at the configured minimum");
        requireEqual(maxLength, HydraulicSettings.clampExpectedLengthTenths(Integer.MAX_VALUE),
                "expected length must clamp at the configured maximum");
        requireEqual(1.0D, HydraulicSettings.clampApproachMultiplier(Double.NaN),
                "non-finite approach multipliers must reset to the neutral value");
        requireEqual(0.0D, HydraulicSettings.returnForceFromLevel(0),
                "the first return-force level must remain disabled");
    }

    private static void verifyHydraulicSettingsState() {
        HydraulicSettingsState settings = new HydraulicSettingsState();
        int minLength = HydraulicSettings.minExpectedLengthTenths();
        int maxLength = HydraulicSettings.maxExpectedLengthTenths();
        HydraulicSettingsState.Change initialChange = settings.applyBaseSettings(-1, true, Integer.MAX_VALUE, -1);
        require(initialChange.changed(), "base settings must report persisted changes");
        requireEqual(0.0D, settings.stretchResistance(), "stretch resistance must clamp before persistence");
        require(settings.freeMode(), "free mode must persist independently from runtime state");
        requireEqual(maxLength, settings.expectedLengthTenths(), "target length must clamp before persistence");
        requireEqual(0.0D, settings.returnForce(), "return force must clamp before persistence");
        require(settings.applyRedstoneLengthRange(minLength, minLength),
                "redstone range changes must be observable for block-entity sync");
        require(settings.applyRedstoneLengthRange(maxLength, minLength),
                "reordered redstone bounds must be normalized after a persisted change");
        requireEqual(minLength, settings.redstoneMinLengthTenths(), "redstone range must be sorted at persistence boundary");
        requireEqual(maxLength, settings.redstoneMaxLengthTenths(), "redstone range must be sorted at persistence boundary");
        require(settings.applyBaseSettings(0, false, maxLength, 0).leftFreeMode(),
                "leaving free mode must remain visible to physics interpolation");
    }

    private static void verifyGiantHydraulicVisualStages() {
        double stageLength = GiantHydraulicRodVisualState.STAGED_LENGTH;
        double rodLength = GiantHydraulicRodVisualState.NOMINAL_ROD_LENGTH;
        double baseLength = GiantHydraulicRodVisualState.BASE_LENGTH;
        requireEqual(40.0D / 16.0D, stageLength, "stage travel must end at the fixed barrel endpoint");
        requireEqual(41.0D / 16.0D, rodLength, "moving rod must retain its one-pixel insertion overlap");
        requireEqual(rodLength, baseLength, "collapsed assembly length must match the moving rod");
        GiantHydraulicRodVisualState collapsed = GiantHydraulicRodVisualState.fromDistance(baseLength);
        requireEqual(0.0D, collapsed.thickOffset(), "giant rod must start fully nested");
        requireEqual(0.0D, collapsed.mediumOffset(), "giant rod medium stage must start nested");
        requireEqual(0.0D, collapsed.thinOffset(), "giant rod thin stage must start nested");
        requireEqual(1.0D, collapsed.continuousScale(), "nested giant rod must not be scaled");

        GiantHydraulicRodVisualState firstStage = GiantHydraulicRodVisualState.fromDistance(baseLength + stageLength);
        requireEqual(stageLength, firstStage.thickOffset(), "first stage must move the thick rod by its calibrated length");
        requireEqual(stageLength, firstStage.mediumOffset(), "first stage must move the medium rod by its calibrated length");
        requireEqual(stageLength, firstStage.thinOffset(), "first stage must move the thin rod by its calibrated length");

        GiantHydraulicRodVisualState secondStage = GiantHydraulicRodVisualState.fromDistance(baseLength + stageLength * 2.0D);
        requireEqual(stageLength, secondStage.thickOffset(), "thick rod must stop after the first calibrated stage");
        requireEqual(stageLength * 2.0D, secondStage.mediumOffset(), "medium rod must stop after the second calibrated stage");
        requireEqual(stageLength * 2.0D, secondStage.thinOffset(), "thin rod must follow the medium rod in the second calibrated stage");

        GiantHydraulicRodVisualState thirdStage = GiantHydraulicRodVisualState.fromDistance(baseLength + stageLength * 3.0D);
        requireEqual(stageLength * 3.0D, thirdStage.thinOffset(), "thin rod must finish the third calibrated stage");
        requireEqual(1.0D, thirdStage.continuousScale(), "staged movement must not scale the rods");

        GiantHydraulicRodVisualState stretched = GiantHydraulicRodVisualState.fromDistance(baseLength + stageLength * 3.0D + 8.0D);
        requireEqual(stageLength, stretched.thickOffset(), "thick rod offset must remain fixed while stretching");
        requireClose(stageLength * 2.0D + 8.0D / 3.0D, stretched.mediumOffset(),
                "medium rod anchor must follow the thick rod's shared extension");
        requireClose(stageLength * 3.0D + 16.0D / 3.0D, stretched.thinOffset(),
                "thin rod anchor must follow both preceding shared extensions");
        requireClose(1.0D + 8.0D / (rodLength * 3.0D), stretched.continuousScale(),
                "continuous extension must be divided equally between all three rods");

        double stretchedTip = stretched.thinOffset() + rodLength * stretched.continuousScale();
        double stagedTip = stageLength * 3.0D + rodLength;
        requireClose(stagedTip + 8.0D, stretchedTip,
                "distributed rod stretching must still reach the requested endpoint");

        GiantHydraulicRodVisualState capped = GiantHydraulicRodVisualState.fromDistance(100.0D);
        requireClose(1.0D + 8.0D / (rodLength * 3.0D), capped.continuousScale(),
                "visual stretch must be capped at eight blocks and shared between all rods");
    }

    private static void verifyGiantHydraulicNoWindup() {
        GiantHydraulicPhysics.Result extending = GiantHydraulicPhysics.step(
                GiantHydraulicPhysics.State.empty(), 3.0D, 15.0D,
                false, 400, 40, 1.0D / 60.0D);
        require(extending.valve() > 0.0D, "an extended target must open the cap-side spool");
        require(extending.motorTargetDistance() < 3.1D,
                "hydraulic motor reference must stay near the piston, not wind up to the command");

        GiantHydraulicPhysics.State locked = new GiantHydraulicPhysics.State(
                new HydraulicCylinderControl.PressureState(100.0D, 0.0D), 9.0D, 9.0D, 9.0D, 0.0D);
        GiantHydraulicPhysics.Result holding = GiantHydraulicPhysics.step(
                locked, 9.0D, 9.0D, false, 25, 0, 1.0D / 60.0D);
        requireEqual(0.0D, holding.valve(), "closed-center state must keep the spool closed");
        requireEqual(9.0D, holding.motorTargetDistance(), "closed-center reference must remain local");
        GiantHydraulicPhysics.Result disturbedHold = GiantHydraulicPhysics.step(
                locked, 8.5D, 9.0D, false, 25, 1, 1.0D / 60.0D);
        requireEqual(0.0D, disturbedHold.valve(),
                "external displacement must not release a closed-center hydraulic hold");
        requireEqual(9.0D, disturbedHold.motorTargetDistance(),
                "external displacement must retain the captured hydraulic reference");
        requireEqual(400.0D, HydraulicCylinderControl.MAX_WORKING_PRESSURE_BAR,
                "closed-center cylinder rating must remain independent from GUI pump pressure");

        GiantHydraulicPhysics.Result lowFlow = GiantHydraulicPhysics.step(
                GiantHydraulicPhysics.State.empty(), 3.0D, 15.0D,
                false, 400, 1, 1.0D / 60.0D);
        GiantHydraulicPhysics.Result highFlow = GiantHydraulicPhysics.step(
                GiantHydraulicPhysics.State.empty(), 3.0D, 15.0D,
                false, 400, 40, 1.0D / 60.0D);
        require(highFlow.valve() > lowFlow.valve(),
                "higher GUI flow must open the valve spool faster");
        GiantHydraulicPhysics.Result zeroFlow = GiantHydraulicPhysics.step(
                GiantHydraulicPhysics.State.empty(), 3.0D, 15.0D,
                false, 400, 0, 1.0D / 60.0D);
        requireEqual(0.0D, zeroFlow.valve(), "zero flow must leave the directional valve closed");
        require(GiantHydraulicPhysics.spoolResponseRate(40)
                        > GiantHydraulicPhysics.spoolResponseRate(1),
                "flow range must affect spool response rate only");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireEqual(double expected, double actual, String message) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1.0E-12D) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
