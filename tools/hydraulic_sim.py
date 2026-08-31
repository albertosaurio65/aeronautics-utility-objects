"""Standalone check for GiantHydraulicPhysics and HydraulicCylinderControl.

The constants and pressure integration intentionally mirror the Java code. It
models a one-dimensional vertical payload so force calibration can be checked
without asking the game-side controller for rigid-body mass.
"""

import math
import sys


DT = 1.0 / 60.0
SECONDS = 60.0
MINIMUM_LENGTH = 3.0
NOMINAL_STROKE = 12.0
POSITION_GAIN = 1.25
VELOCITY_GAIN = 0.6
HOLD_CAPTURE_DISTANCE = 1.0 / 32.0
HOLD_CAPTURE_SPEED = 0.05
MIN_FLOW_LITRES_PER_MINUTE = 0
MAX_FLOW_LITRES_PER_MINUTE = 40
MAXIMUM_TRAVEL_SPEED = 3.0
MAXIMUM_VALVE_FLOW_RATE = MAXIMUM_TRAVEL_SPEED
MINIMUM_SPOOL_RESPONSE_RATE = 0.35
MAXIMUM_SPOOL_RESPONSE_RATE = 15.0

CAP_AREA = 1.0
ROD_AREA = 0.8
CAP_FORCE_AREA = 128.0
ROD_FORCE_AREA = CAP_FORCE_AREA * ROD_AREA
CAP_BASE_VOLUME = 1.0
ROD_BASE_VOLUME = 0.8
MAX_WORKING_PRESSURE_BAR = 400.0
BULK_MODULUS = 600000.0
CHAMBER_LEAKAGE = 1.0e-6
TANK_RETURN_CONDUCTANCE = 0.1
INTEGRATION_SUBSTEPS = 16
GRAVITY = 11.0
MAX_PRESSURE_SLEW_PER_SECOND = 20.0
MOTOR_STIFFNESS = 1.0e6
MOTOR_DAMPING = 1.0e3
HOLD_DAMPING = 1.0e6


def clamp(value, minimum, maximum):
    return max(minimum, min(maximum, value))


def spool_response_rate(flow_lpm):
    if flow_lpm <= 0:
        return 0.0
    normalized = (clamp(flow_lpm, 1.0, MAX_FLOW_LITRES_PER_MINUTE) - 1.0) / (
        MAX_FLOW_LITRES_PER_MINUTE - 1.0
    )
    normalized *= normalized
    return MINIMUM_SPOOL_RESPONSE_RATE + normalized * (
        MAXIMUM_SPOOL_RESPONSE_RATE - MINIMUM_SPOOL_RESPONSE_RATE
    )


def integrate_pressure(pressure, source_flow, pressure_loss, stiffness, time_step):
    """Same backward-Euler pressure update as HydraulicCylinderControl."""
    step = stiffness * time_step
    return (pressure + step * source_flow) / (1.0 + step * pressure_loss)


def slew_pressure(current, target, supply_pressure, time_step, inlet_opening):
    """Pump pressure builds at a finite rate; an opened return line vents freely."""
    maximum_change = MAX_PRESSURE_SLEW_PER_SECOND * max(0.0, inlet_opening) * time_step
    return clamp(target, 0.0, min(supply_pressure, current + maximum_change))


def cylinder_step(cap_pressure, rod_pressure, stroke, velocity, valve, supply_pressure, flow_rate):
    cap_pressure = clamp(cap_pressure, 0.0, MAX_WORKING_PRESSURE_BAR)
    rod_pressure = clamp(rod_pressure, 0.0, MAX_WORKING_PRESSURE_BAR)
    stroke = max(0.0, stroke)
    valve = clamp(valve, -1.0, 1.0)
    sub_step = DT / INTEGRATION_SUBSTEPS
    flow = abs(valve) * flow_rate

    for _ in range(INTEGRATION_SUBSTEPS):
        cap_volume = max(0.25, CAP_BASE_VOLUME + CAP_AREA * stroke)
        rod_volume = max(0.25, ROD_BASE_VOLUME + ROD_AREA * max(0.0, NOMINAL_STROKE - stroke))
        if valve > 0.0:
            cap_source = flow - CAP_AREA * velocity + CHAMBER_LEAKAGE * rod_pressure
            rod_source = ROD_AREA * velocity + CHAMBER_LEAKAGE * cap_pressure
            cap_loss = flow / supply_pressure + CHAMBER_LEAKAGE
            rod_loss = TANK_RETURN_CONDUCTANCE + CHAMBER_LEAKAGE
        elif valve < 0.0:
            cap_source = -CAP_AREA * velocity + CHAMBER_LEAKAGE * rod_pressure
            rod_source = flow + ROD_AREA * velocity + CHAMBER_LEAKAGE * cap_pressure
            cap_loss = TANK_RETURN_CONDUCTANCE + CHAMBER_LEAKAGE
            rod_loss = flow / supply_pressure + CHAMBER_LEAKAGE
        else:
            cap_source = -CAP_AREA * velocity + CHAMBER_LEAKAGE * rod_pressure
            rod_source = ROD_AREA * velocity + CHAMBER_LEAKAGE * cap_pressure
            cap_loss = CHAMBER_LEAKAGE
            rod_loss = CHAMBER_LEAKAGE

        updated_cap_pressure = integrate_pressure(cap_pressure, cap_source, cap_loss, BULK_MODULUS / cap_volume, sub_step)
        updated_rod_pressure = integrate_pressure(rod_pressure, rod_source, rod_loss, BULK_MODULUS / rod_volume, sub_step)
        if valve == 0.0:
            cap_pressure = updated_cap_pressure
            rod_pressure = updated_rod_pressure
        else:
            cap_pressure = slew_pressure(
                cap_pressure, updated_cap_pressure, supply_pressure, sub_step, valve if valve > 0.0 else 0.0
            )
            rod_pressure = slew_pressure(
                rod_pressure, updated_rod_pressure, supply_pressure, sub_step, -valve if valve < 0.0 else 0.0
            )
        cap_pressure = clamp(cap_pressure, 0.0, MAX_WORKING_PRESSURE_BAR)
        rod_pressure = clamp(rod_pressure, 0.0, MAX_WORKING_PRESSURE_BAR)
    return cap_pressure, rod_pressure, cap_pressure * CAP_FORCE_AREA - rod_pressure * ROD_FORCE_AREA


def apply_bounded_motor(length, velocity, target_length, mass, maximum_force, damping=MOTOR_DAMPING):
    """One implicit scalar step matching Sable's bounded position motor role.

    Mass appears here only because this harness is the rigid-body solver. The
    Java controller never observes or branches on rigid-body mass.
    """
    free_velocity = velocity - GRAVITY * DT
    denominator = mass / DT + damping + MOTOR_STIFFNESS * DT
    solved_velocity = (mass * free_velocity / DT + MOTOR_STIFFNESS * (target_length - length)) / denominator
    required_force = mass * (solved_velocity - free_velocity) / DT
    motor_force = clamp(required_force, -abs(maximum_force), abs(maximum_force))
    return free_velocity + motor_force * DT / mass, motor_force


def approach(current, target, maximum_step):
    if not math.isfinite(current):
        return target
    return current + clamp(target - current, -maximum_step, maximum_step)


def hydraulic_control(distance, velocity, command_target, locked_distance, locked_command,
                     spool_opening, flow_lpm):
    locked = (
        math.isfinite(locked_distance)
        and math.isfinite(locked_command)
        and abs(command_target - locked_command) <= 1.0e-4
    )
    if locked:
        return 0.0, locked_distance, locked_distance, locked_command, approach(
            spool_opening, 0.0, spool_response_rate(flow_lpm) * DT
        )

    error = command_target - distance
    if abs(error) <= HOLD_CAPTURE_DISTANCE and abs(velocity) <= HOLD_CAPTURE_SPEED:
        return 0.0, distance, distance, command_target, 0.0
    demand = clamp((POSITION_GAIN * error - VELOCITY_GAIN * velocity) /
                   MAXIMUM_TRAVEL_SPEED, -1.0, 1.0)
    response_rate = spool_response_rate(flow_lpm)
    if abs(error) <= 0.25 or spool_opening * demand < 0.0:
        response_rate = MAXIMUM_SPOOL_RESPONSE_RATE
    opening = 0.0 if flow_lpm <= 0 else approach(
        spool_opening, demand, response_rate * DT
    )
    return opening, command_target, math.nan, math.nan, opening


def hydraulic_motor_target(distance, valve, locked_distance):
    """Same non-winding pressure reference as GiantHydraulicPhysics.

    The solver motor is only allowed to see the position error represented by
    the present chamber pressure. A blocked rod therefore cannot accumulate a
    command-sized spring error that would be released after a block is broken.
    """
    if math.isfinite(locked_distance):
        return locked_distance
    if abs(valve) <= 1.0e-8:
        return distance
    # Rapier's implicit PD solve observes the position reference one substep
    # ahead. Include that term so the reference represents a velocity, rather
    # than being attenuated by the solver stiffness every tick.
    return distance + valve * MAXIMUM_TRAVEL_SPEED * (MOTOR_DAMPING / MOTOR_STIFFNESS + DT)


def run(payload_mass, target_length=9.0, pressure_bar=350.0, flow_lpm=24.0, seconds=SECONDS):
    length = MINIMUM_LENGTH
    velocity = 0.0
    cap_pressure = 0.0
    rod_pressure = 0.0
    effective_target = length
    locked_distance = math.nan
    locked_command = math.nan
    spool_opening = 0.0
    peak_speed = 0.0
    target_crossings = 0
    previous_error = target_length - length

    for _ in range(round(seconds / DT)):
        effective_target = target_length
        error = effective_target - length
        if error * previous_error < 0.0:
            target_crossings += 1
        previous_error = error
        valve, _, locked_distance, locked_command, spool_opening = hydraulic_control(
            length, velocity, effective_target, locked_distance, locked_command,
            spool_opening, flow_lpm
        )
        cap_pressure, rod_pressure, force = cylinder_step(
            cap_pressure,
            rod_pressure,
            length - MINIMUM_LENGTH,
            velocity,
            valve,
            pressure_bar,
            MAXIMUM_VALVE_FLOW_RATE,
        )
        available_force = (MAX_WORKING_PRESSURE_BAR if math.isfinite(locked_distance) else pressure_bar) * CAP_FORCE_AREA
        motor_target = hydraulic_motor_target(length, valve, locked_distance)
        velocity, _ = apply_bounded_motor(
            length, velocity, motor_target, payload_mass, available_force,
            HOLD_DAMPING if math.isfinite(locked_distance) else MOTOR_DAMPING,
        )
        length += velocity * DT
        if length < MINIMUM_LENGTH:
            length = MINIMUM_LENGTH
            velocity = max(0.0, velocity)
        peak_speed = max(peak_speed, abs(velocity))

    return {
        "length": length,
        "velocity": velocity,
        "cap_bar": cap_pressure,
        "rod_bar": rod_pressure,
        "peak_speed": peak_speed,
        "crossings": target_crossings,
    }


def run_load_step(initial_mass, final_mass, change_time=20.0, target_length=9.0,
                  pressure_bar=350.0, flow_lpm=24.0, seconds=SECONDS):
    """Reproduces adding or removing a payload while the cylinder is holding."""
    # Begin at a settled hold. This isolates the load-step response from the
    # deliberate target-travel test in run().
    length = target_length
    velocity = 0.0
    cap_pressure = clamp(initial_mass * GRAVITY / CAP_FORCE_AREA, 0.0, pressure_bar)
    rod_pressure = 0.0
    effective_target = target_length
    locked_distance = math.nan
    locked_command = math.nan
    spool_opening = 0.0
    peak_speed_after_change = 0.0
    minimum_length_after_change = length

    for step in range(round(seconds / DT)):
        time = step * DT
        payload_mass = initial_mass if time < change_time else final_mass
        effective_target = target_length
        valve, _, locked_distance, locked_command, spool_opening = hydraulic_control(
            length, velocity, effective_target, locked_distance, locked_command,
            spool_opening, flow_lpm
        )
        cap_pressure, rod_pressure, force = cylinder_step(
            cap_pressure,
            rod_pressure,
            length - MINIMUM_LENGTH,
            velocity,
            valve,
            pressure_bar,
            MAXIMUM_VALVE_FLOW_RATE,
        )
        available_force = (MAX_WORKING_PRESSURE_BAR if math.isfinite(locked_distance) else pressure_bar) * CAP_FORCE_AREA
        motor_target = hydraulic_motor_target(length, valve, locked_distance)
        velocity, _ = apply_bounded_motor(
            length, velocity, motor_target, payload_mass, available_force,
            HOLD_DAMPING if math.isfinite(locked_distance) else MOTOR_DAMPING,
        )
        length += velocity * DT
        if length < MINIMUM_LENGTH:
            length = MINIMUM_LENGTH
            velocity = max(0.0, velocity)
        if time >= change_time:
            peak_speed_after_change = max(peak_speed_after_change, abs(velocity))
            minimum_length_after_change = min(minimum_length_after_change, length)

    return {
        "length": length,
        "velocity": velocity,
        "cap_bar": cap_pressure,
        "rod_bar": rod_pressure,
        "peak_speed_after_change": peak_speed_after_change,
        "minimum_length_after_change": minimum_length_after_change,
    }


def require_stable(result, label):
    values = tuple(result.values())
    if not all(math.isfinite(value) for value in values):
        raise AssertionError(f"{label}: non-finite state: {result}")
    if result["peak_speed"] > MAXIMUM_TRAVEL_SPEED + 0.1:
        raise AssertionError(f"{label}: travel speed exceeded flow-controlled limit: {result}")
    if result["crossings"] != 0:
        raise AssertionError(f"{label}: target overshoot: {result}")


def verify_gui_stability():
    """Exercise every discrete GUI value plus all meaningful boundary combinations."""
    checked = 0
    masses = (0.01, 1.0, 100.0, 3000.0, 4500.0)

    for flow in range(MIN_FLOW_LITRES_PER_MINUTE, MAX_FLOW_LITRES_PER_MINUTE + 1):
        for mass in masses:
            require_stable(run(mass, flow_lpm=flow, seconds=1.5), f"flow={flow}, mass={mass}")
            checked += 1

    for pressure in range(25, 401):
        for mass in masses:
            require_stable(run(mass, pressure_bar=pressure, seconds=1.5), f"pressure={pressure}, mass={mass}")
            checked += 1

    for target_tenths in range(30, 151):
        for mass in masses:
            require_stable(run(mass, target_length=target_tenths / 10.0, seconds=1.5),
                           f"target={target_tenths / 10.0}, mass={mass}")
            checked += 1

    for flow in (0, 1, 2, 40):
        for pressure in (25, 400):
            for target in (3.0, 9.0, 15.0):
                for mass in masses:
                    require_stable(run(mass, target_length=target, pressure_bar=pressure,
                                       flow_lpm=flow, seconds=12.0),
                                   f"corner={flow}/{pressure}/{target}/{mass}")
                    checked += 1

    for pressure in range(25, 401):
        for before, after in ((4500.0, 0.01), (0.01, 4500.0)):
            result = run_load_step(before, after, change_time=2.0, pressure_bar=pressure, seconds=6.0)
            if not all(math.isfinite(value) for value in result.values()):
                raise AssertionError(f"load step {pressure}/{before}->{after}: {result}")
            if result["peak_speed_after_change"] > 0.05:
                raise AssertionError(f"load step speed kick {pressure}/{before}->{after}: {result}")
            if result["minimum_length_after_change"] < 9.0 - 1.0 / 16.0:
                raise AssertionError(f"load step displacement jump {pressure}/{before}->{after}: {result}")
            checked += 1

    return checked


if __name__ == "__main__":
    if "--verify" in sys.argv:
        print(f"GUI stability sweep passed: {verify_gui_stability()} scenarios")
        raise SystemExit(0)
    for mass, pressure in ((1.0, 350.0), (100.0, 350.0), (3000.0, 350.0), (3000.0, 400.0)):
        result = run(mass, pressure_bar=pressure)
        print(f"payload={mass:g} kpg, supply={pressure:g} bar: {result}")
    for before, after in ((3000.0, 100.0), (100.0, 3000.0)):
        print(f"load step {before:g}->{after:g} kpg: {run_load_step(before, after)}")
