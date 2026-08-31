package com.enxv.aerouniversaljoint.content.hydraulic;

/** Server-side controller for the giant hydraulic rod. */
public final class GiantHydraulicPhysics {
    public static final double MINIMUM_LENGTH = 3.0D;
    /** Must match the Sable motor stiffness configured by the block entity. */
    public static final double MOTOR_POSITION_STIFFNESS = 1.0E6D;
    /** Moving-spool damping; closed-center damping is configured separately. */
    public static final double MOTOR_POSITION_DAMPING = 1.0E3D;
    /** Piston speed at 40 L/min and a fully open directional valve, in blocks per second. */
    public static final double MAXIMUM_TRAVEL_SPEED = 3.0D;
    private static final double MAXIMUM_VALVE_FLOW_RATE = MAXIMUM_TRAVEL_SPEED;
    private static final double MINIMUM_SPOOL_RESPONSE_RATE = 0.35D;
    private static final double MAXIMUM_SPOOL_RESPONSE_RATE = 15.0D;
    private static final double POSITION_GAIN = 1.25D;
    private static final double VELOCITY_GAIN = 0.6D;
    private static final double HOLD_CAPTURE_DISTANCE = 1.0D / 32.0D;
    private static final double HOLD_CAPTURE_SPEED = 0.05D;
    private static final double MAX_STEP_DISTANCE = 2.0D;

    private GiantHydraulicPhysics() {
    }

    public record State(HydraulicCylinderControl.PressureState pressure, double previousDistance,
                        double lockedDistance, double lockedCommandDistance, double spoolOpening) {
        public static State empty() {
            return new State(HydraulicCylinderControl.PressureState.empty(), Double.NaN,
                    Double.NaN, Double.NaN, 0.0D);
        }
    }

    public record Result(State state, double force, double velocity, double valve, double motorTargetDistance) {
    }

    public static Result step(State initial, double distance, double targetDistance,
                              boolean vented, int pressureBar, int flowLitresPerMinute,
                              double timeStep) {
        if (timeStep <= 0.0D || !Double.isFinite(distance)) {
            return new Result(initial, 0.0D, 0.0D, 0.0D, targetDistance);
        }

        double velocity = Double.isFinite(initial.previousDistance())
                ? (distance - initial.previousDistance()) / timeStep
                : 0.0D;
        if (!Double.isFinite(velocity) || Math.abs(distance - initial.previousDistance()) > MAX_STEP_DISTANCE) {
            velocity = 0.0D;
            initial = new State(initial.pressure(), Double.NaN,
                    initial.lockedDistance(), initial.lockedCommandDistance(), initial.spoolOpening());
        }

        HydraulicCylinderControl.PressureState pressure = initial.pressure();
        double supplyPressure = Math.max(0.0D, pressureBar);
        if (vented || supplyPressure <= 0.0D) {
            pressure = HydraulicCylinderControl.vent(pressure, timeStep);
            return new Result(new State(pressure, distance, Double.NaN, Double.NaN, 0.0D),
                    0.0D, velocity, 0.0D, targetDistance);
        }

        boolean locked = Double.isFinite(initial.lockedDistance())
                && Double.isFinite(initial.lockedCommandDistance())
                && Math.abs(targetDistance - initial.lockedCommandDistance()) <= 1.0E-4D;

        double positioningReference = locked ? initial.lockedDistance() : targetDistance;
        double error = positioningReference - distance;
        double valve;
        double spoolOpening;
        double lockedDistance = Double.NaN;
        double lockedCommandDistance = Double.NaN;
        if (locked) {
            valve = 0.0D;
            spoolOpening = approach(initial.spoolOpening(), 0.0D,
                    spoolResponseRate(flowLitresPerMinute) * timeStep);
            lockedDistance = initial.lockedDistance();
            lockedCommandDistance = initial.lockedCommandDistance();
        } else if (Math.abs(error) <= HOLD_CAPTURE_DISTANCE && Math.abs(velocity) <= HOLD_CAPTURE_SPEED) {
            valve = 0.0D;
            spoolOpening = 0.0D;
            lockedDistance = distance;
            lockedCommandDistance = targetDistance;
        } else {
            double spoolDemand = clamp((POSITION_GAIN * error - VELOCITY_GAIN * velocity)
                    / MAXIMUM_TRAVEL_SPEED, -1.0D, 1.0D);
            double responseRate = spoolResponseRate(flowLitresPerMinute);
            if (Math.abs(error) <= 0.25D
                    || initial.spoolOpening() * spoolDemand < 0.0D) {
                responseRate = MAXIMUM_SPOOL_RESPONSE_RATE;
            }
            spoolOpening = flowLitresPerMinute <= 0 ? 0.0D : approach(initial.spoolOpening(), spoolDemand,
                    responseRate * timeStep);
            valve = spoolOpening;
        }
        double flowScale = flowScale(flowLitresPerMinute);
        HydraulicCylinderControl.StepResult update = HydraulicCylinderControl.step(
                pressure, Math.max(0.0D, distance - MINIMUM_LENGTH), velocity, valve,
                supplyPressure, MAXIMUM_VALVE_FLOW_RATE * flowScale, timeStep);
        double force = update.force();
        double motorTargetDistance = locked
                ? lockedDistance
                : hydraulicMotorReference(distance, valve, flowScale, timeStep);
        return new Result(new State(update.state(), distance, lockedDistance, lockedCommandDistance, spoolOpening),
                force, velocity, valve, motorTargetDistance);
    }

    public static double spoolResponseRate(int flowLitresPerMinute) {
        if (flowLitresPerMinute <= 0) {
            return 0.0D;
        }

        double normalized = (clamp(flowLitresPerMinute,
                GiantHydraulicSettingsState.MIN_ACTIVE_FLOW_LITRES_PER_MINUTE,
                GiantHydraulicSettingsState.MAX_FLOW_LITRES_PER_MINUTE)
                - GiantHydraulicSettingsState.MIN_ACTIVE_FLOW_LITRES_PER_MINUTE)
                / (GiantHydraulicSettingsState.MAX_FLOW_LITRES_PER_MINUTE
                - GiantHydraulicSettingsState.MIN_ACTIVE_FLOW_LITRES_PER_MINUTE);
        normalized *= normalized;
        return MINIMUM_SPOOL_RESPONSE_RATE
                + normalized * (MAXIMUM_SPOOL_RESPONSE_RATE - MINIMUM_SPOOL_RESPONSE_RATE);
    }

    private static double hydraulicMotorReference(double distance, double valve,
                                                   double flowScale, double timeStep) {
        if (Math.abs(valve) <= 1.0E-8D) {
            return distance;
        }

        return distance + valve * MAXIMUM_TRAVEL_SPEED * flowScale
                * (MOTOR_POSITION_DAMPING / MOTOR_POSITION_STIFFNESS + timeStep);
    }

    private static double flowScale(int flowLitresPerMinute) {
        return clamp(flowLitresPerMinute, GiantHydraulicSettingsState.MIN_FLOW_LITRES_PER_MINUTE,
                GiantHydraulicSettingsState.MAX_FLOW_LITRES_PER_MINUTE)
                / GiantHydraulicSettingsState.MAX_FLOW_LITRES_PER_MINUTE;
    }

    private static double approach(double current, double target, double maximumStep) {
        if (!Double.isFinite(current)) {
            return target;
        }
        return current + clamp(target - current, -maximumStep, maximumStep);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
