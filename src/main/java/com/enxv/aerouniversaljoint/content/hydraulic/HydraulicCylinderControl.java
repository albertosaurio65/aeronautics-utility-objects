package com.enxv.aerouniversaljoint.content.hydraulic;

/** Double-acting hydraulic-cylinder model used by the giant rod. */
public final class HydraulicCylinderControl {
    public static final double CAP_AREA = 1.0D;
    public static final double ROD_AREA = 0.8D;
    /** Sable-force output per bar on the giant cylinder's cap side. */
    public static final double CAP_FORCE_AREA = 128.0D;
    public static final double ROD_FORCE_AREA = CAP_FORCE_AREA * ROD_AREA;
    public static final double CAP_BASE_VOLUME = 1.0D;
    public static final double ROD_BASE_VOLUME = 0.8D;
    public static final double NOMINAL_STROKE = 12.0D;
    /** Structural pressure rating of the cylinder and its closed-center oil volume. */
    public static final double MAX_WORKING_PRESSURE_BAR = 400.0D;
    public static final double BULK_MODULUS = 600000.0D;
    /** Small internal leakage; the return line is modeled separately below. */
    public static final double CHAMBER_LEAKAGE = 1.0E-6D;
    /** Conductance from the exhausted chamber to the oil tank. */
    public static final double TANK_RETURN_CONDUCTANCE = 0.1D;
    /** Pump/valve pressure build-up in bar per second at full spool opening. */
    public static final double MAX_PRESSURE_SLEW_PER_SECOND = 20.0D;
    public static final int INTEGRATION_SUBSTEPS = 16;

    private HydraulicCylinderControl() {
    }

    public record PressureState(double capPressure, double rodPressure) {
        public static PressureState empty() {
            return new PressureState(0.0D, 0.0D);
        }
    }

    public record StepResult(PressureState state, double force) {
    }

    public static StepResult step(PressureState initial, double stroke, double velocity,
                                  double valve, double supplyPressure, double flowRate, double timeStep) {
        if (timeStep <= 0.0D || supplyPressure <= 0.0D || flowRate <= 0.0D) {
            return new StepResult(initial, force(initial));
        }

        double capPressure = clampPressure(initial.capPressure());
        double rodPressure = clampPressure(initial.rodPressure());
        double clampedStroke = Math.max(0.0D, stroke);
        double clampedValve = clamp(valve, -1.0D, 1.0D);
        double subStep = timeStep / INTEGRATION_SUBSTEPS;
        double flow = Math.abs(clampedValve) * flowRate;

        for (int i = 0; i < INTEGRATION_SUBSTEPS; i++) {
            double capVolume = Math.max(0.25D, CAP_BASE_VOLUME + CAP_AREA * clampedStroke);
            double rodVolume = Math.max(0.25D,
                    ROD_BASE_VOLUME + ROD_AREA * Math.max(0.0D, NOMINAL_STROKE - clampedStroke));
            double capSource;
            double rodSource;
            double capLoss;
            double rodLoss;
            if (clampedValve > 0.0D) {
                capSource = flow - CAP_AREA * velocity + CHAMBER_LEAKAGE * rodPressure;
                rodSource = ROD_AREA * velocity + CHAMBER_LEAKAGE * capPressure;
                capLoss = flow / supplyPressure + CHAMBER_LEAKAGE;
                rodLoss = TANK_RETURN_CONDUCTANCE + CHAMBER_LEAKAGE;
            } else if (clampedValve < 0.0D) {
                capSource = -CAP_AREA * velocity + CHAMBER_LEAKAGE * rodPressure;
                rodSource = flow + ROD_AREA * velocity + CHAMBER_LEAKAGE * capPressure;
                capLoss = TANK_RETURN_CONDUCTANCE + CHAMBER_LEAKAGE;
                rodLoss = flow / supplyPressure + CHAMBER_LEAKAGE;
            } else {
                capSource = -CAP_AREA * velocity + CHAMBER_LEAKAGE * rodPressure;
                rodSource = ROD_AREA * velocity + CHAMBER_LEAKAGE * capPressure;
                capLoss = CHAMBER_LEAKAGE;
                rodLoss = CHAMBER_LEAKAGE;
            }

            double updatedCapPressure = integratePressure(capPressure, capSource, capLoss,
                    BULK_MODULUS / capVolume, subStep);
            double updatedRodPressure = integratePressure(rodPressure, rodSource, rodLoss,
                    BULK_MODULUS / rodVolume, subStep);
            if (clampedValve == 0.0D) {
                capPressure = updatedCapPressure;
                rodPressure = updatedRodPressure;
            } else {
                capPressure = slewPressure(capPressure, updatedCapPressure,
                        supplyPressure, subStep, clampedValve > 0.0D ? clampedValve : 0.0D);
                rodPressure = slewPressure(rodPressure, updatedRodPressure,
                        supplyPressure, subStep, clampedValve < 0.0D ? -clampedValve : 0.0D);
            }
            capPressure = clampPressure(capPressure);
            rodPressure = clampPressure(rodPressure);
        }

        PressureState state = new PressureState(capPressure, rodPressure);
        return new StepResult(state, force(state));
    }

    public static PressureState vent(PressureState state, double timeStep) {
        if (timeStep <= 0.0D) {
            return state;
        }
        double factor = Math.exp(-8.0D * timeStep);
        return new PressureState(state.capPressure() * factor, state.rodPressure() * factor);
    }

    public static double force(PressureState state) {
        return state.capPressure() * CAP_FORCE_AREA - state.rodPressure() * ROD_FORCE_AREA;
    }

    private static double clampPressure(double pressure) {
        return Double.isFinite(pressure) ? clamp(pressure, 0.0D, MAX_WORKING_PRESSURE_BAR) : 0.0D;
    }

    /** Backward-Euler pressure update; stable for a stiff oil column. */
    private static double integratePressure(double pressure, double sourceFlow, double pressureLoss,
                                            double stiffness, double timeStep) {
        double step = stiffness * timeStep;
        return (pressure + step * sourceFlow) / (1.0D + step * pressureLoss);
    }

    private static double slewPressure(double current, double target, double supplyPressure,
                                       double timeStep, double inletOpening) {
        double maximumChange = MAX_PRESSURE_SLEW_PER_SECOND * Math.max(0.0D, inletOpening) * timeStep;
        return clamp(target, 0.0D, Math.min(supplyPressure, current + maximumChange));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
