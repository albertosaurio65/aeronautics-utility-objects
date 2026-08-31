package com.enxv.aerouniversaljoint;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class AeroUniversalJointConfig {
    private static final Pattern BLOCK_ID_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public static final int DEFAULT_DAMPING_MAX_RESISTANCE = 256;
    public static final int DEFAULT_DAMPING_DEFAULT_RESISTANCE = 64;
    public static final double DEFAULT_DAMPING_RATED_OUTPUT_SPEED_RPM = 256.0D;
    public static final double DEFAULT_DAMPING_MAX_FULL_SPEED_STRESS_OUTPUT = 200000.0D;
    public static final double DEFAULT_DAMPING_SUPPRESSED_STRESS_OUTPUT = 40000.0D;
    public static final double DEFAULT_DAMPING_RESISTANCE_TORQUE_PER_UNIT = 20.0D;
    public static final double DEFAULT_DAMPING_SPEED_DAMPING_BASE_RPM = 128.0D;
    public static final double DEFAULT_HYDRAULIC_ROD_MIN_LINK_LENGTH = 2.0D;
    public static final double DEFAULT_HYDRAULIC_ROD_MAX_LINK_LENGTH = 15.0D;
    public static final double DEFAULT_HYDRAULIC_ROD_BREAK_LINK_LENGTH = 17.0D;
    public static final double DEFAULT_HYDRAULIC_ROD_STRAIN_LINK_LENGTH = 16.5D;
    public static final int DEFAULT_HYDRAULIC_ROD_MAX_STRETCH_RESISTANCE = 65536;
    public static final int DEFAULT_HYDRAULIC_ROD_DEFAULT_STRETCH_RESISTANCE = 256;
    public static final int DEFAULT_HYDRAULIC_ROD_MAX_RETURN_FORCE = 4096;
    public static final int DEFAULT_HYDRAULIC_ROD_DEFAULT_RETURN_FORCE = 1024;
    public static final double DEFAULT_HYDRAULIC_ROD_LENGTH_LIMIT_STIFFNESS = 48.0D;
    public static final double DEFAULT_HYDRAULIC_ROD_LENGTH_LIMIT_CURVE = 4.0D;
    public static final double DEFAULT_HYDRAULIC_ROD_STRETCH_DAMPING_PER_UNIT = 0.5D;
    public static final double DEFAULT_HYDRAULIC_ROD_STRETCH_MAX_FORCE_PER_UNIT = 12.0D;
    public static final double DEFAULT_HYDRAULIC_ROD_RETURN_FORCE_PER_UNIT = 2.0D;
    public static final double DEFAULT_HYDRAULIC_ROD_RETURN_FORCE_CURVE = 0.5D;
    public static final double DEFAULT_HYDRAULIC_ROD_EXPECTED_LENGTH_APPROACH_RATE = 3.0D;
    public static final double DEFAULT_HYDRAULIC_ROD_RETURN_FORCE_APPROACH_RATE = 1024.0D;
    public static final double DEFAULT_HYDRAULIC_ROD_MAX_LENGTH_LIMIT_IMPULSE = 256.0D;
    public static final double DEFAULT_HYDRAULIC_ROD_MAX_EXPECTED_RETURN_IMPULSE = 256.0D;
    public static final double DEFAULT_HYDRAULIC_ROD_MAX_COMBINED_LENGTH_CONTROL_IMPULSE = 512.0D;
    public static final double DEFAULT_REGULATOR_STRESS_IMPACT = 8.0D;
    public static final double DEFAULT_REGULATOR_MIN_TRANSITION_SPEED = 16.0D;
    public static final double DEFAULT_REGULATOR_MAX_TRANSITION_SPEED = 256.0D;
    public static final double DEFAULT_REGULATOR_MIN_TRANSITION_MULTIPLIER = 0.5D;
    public static final double DEFAULT_REGULATOR_MAX_TRANSITION_MULTIPLIER = 4.0D;
    public static final double DEFAULT_ANDESITE_JOINT_LINK_RANGE = 15.0D;
    public static final double DEFAULT_ANDESITE_JOINT_SOFT_RANGE = 1.0D;
    public static final double DEFAULT_ANDESITE_JOINT_DISCONNECT_RANGE = 2.5D;
    public static final double DEFAULT_ANDESITE_JOINT_PULL_STIFFNESS = 1320.0D;
    public static final double DEFAULT_ANDESITE_JOINT_PULL_DAMPING = 18.0D;
    public static final double DEFAULT_BRASS_JOINT_LINK_RANGE = 5.0D;
    public static final double DEFAULT_BRASS_JOINT_SOFT_RANGE = 4.0D;
    public static final double DEFAULT_BRASS_JOINT_DISCONNECT_RANGE = 5.5D;
    public static final double DEFAULT_BRASS_JOINT_PULL_STIFFNESS = 12.0D;
    public static final double DEFAULT_BRASS_JOINT_PULL_CURVE = 4.0D;
    public static final double DEFAULT_BRASS_JOINT_PULL_DAMPING = 7.0D;
    public static final double DEFAULT_BRASS_JOINT_PULL_DAMPING_GAIN = 3.0D;
    public static final double DEFAULT_BRASS_JOINT_ENDPOINT_PULL_MULTIPLIER = 4.0D;
    private static final double MIN_JOINT_RANGE_GAP = 0.01D;
    private static final List<String> REQUIRED_STRESS_OUTPUT_BLACKLIST = List.of(
            "simulated:swivel_bearing",
            "simulated:swivel_bearing_link_block",
            "offroad:wheel_mount",
            "simulated:spring",
            "simulated:docking_connector",
            "simulated:paired_docking_connector");
    private static volatile Set<String> cachedBlacklistIds = Set.of();
    private static volatile Set<Block> cachedBlacklistBlocks = Set.of();

    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    static {
        var pair = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = pair.getLeft();
        SERVER_SPEC = pair.getRight();
    }

    private AeroUniversalJointConfig() {
    }

    public static Set<String> getStressOutputBlacklist() {
        LinkedHashSet<String> blacklist = new LinkedHashSet<>();
        blacklist.addAll(REQUIRED_STRESS_OUTPUT_BLACKLIST);
        for (String entry : getConfiguredBlacklist()) {
            if (entry == null) {
                continue;
            }

            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                blacklist.add(normalized);
            }
        }
        return blacklist;
    }

    public static Set<Block> getStressOutputBlacklistBlocks() {
        Set<String> blacklistIds = getStressOutputBlacklist();
        if (blacklistIds.equals(cachedBlacklistIds)) {
            return cachedBlacklistBlocks;
        }

        synchronized (AeroUniversalJointConfig.class) {
            if (blacklistIds.equals(cachedBlacklistIds)) {
                return cachedBlacklistBlocks;
            }

            cachedBlacklistIds = Set.copyOf(blacklistIds);
            cachedBlacklistBlocks = resolveBlockBlacklist(cachedBlacklistIds);
            return cachedBlacklistBlocks;
        }
    }

    private static List<? extends String> getConfiguredBlacklist() {
        try {
            return SERVER.stressOutputBlacklist.get();
        } catch (IllegalStateException | NullPointerException ignored) {
            return SERVER.stressOutputBlacklist.getDefault();
        }
    }

    public static int dampingMaxResistance() {
        return getInt(SERVER.dampingMaxResistance, DEFAULT_DAMPING_MAX_RESISTANCE);
    }

    public static int dampingDefaultResistance() {
        return clampInt(getInt(SERVER.dampingDefaultResistance, DEFAULT_DAMPING_DEFAULT_RESISTANCE),
                0, dampingMaxResistance());
    }

    public static float dampingRatedOutputSpeedRpm() {
        return (float) getDouble(SERVER.dampingRatedOutputSpeedRpm, DEFAULT_DAMPING_RATED_OUTPUT_SPEED_RPM);
    }

    public static float dampingMaxFullSpeedStressOutput() {
        return (float) getDouble(SERVER.dampingMaxFullSpeedStressOutput, DEFAULT_DAMPING_MAX_FULL_SPEED_STRESS_OUTPUT);
    }

    public static float dampingSuppressedStressOutput() {
        return (float) getDouble(SERVER.dampingSuppressedStressOutput, DEFAULT_DAMPING_SUPPRESSED_STRESS_OUTPUT);
    }

    public static double dampingResistanceTorquePerUnit() {
        return getDouble(SERVER.dampingResistanceTorquePerUnit, DEFAULT_DAMPING_RESISTANCE_TORQUE_PER_UNIT);
    }

    public static double dampingSpeedDampingBaseRpm() {
        return getDouble(SERVER.dampingSpeedDampingBaseRpm, DEFAULT_DAMPING_SPEED_DAMPING_BASE_RPM);
    }

    public static double hydraulicRodMinLinkLength() {
        return getFiniteDouble(SERVER.hydraulicRodMinLinkLength, DEFAULT_HYDRAULIC_ROD_MIN_LINK_LENGTH);
    }

    public static double hydraulicRodMaxLinkLength() {
        return Math.max(hydraulicRodMinLinkLength() + MIN_JOINT_RANGE_GAP,
                getFiniteDouble(SERVER.hydraulicRodMaxLinkLength, DEFAULT_HYDRAULIC_ROD_MAX_LINK_LENGTH));
    }

    public static double hydraulicRodBreakLinkLength() {
        return Math.max(hydraulicRodMaxLinkLength() + MIN_JOINT_RANGE_GAP,
                getFiniteDouble(SERVER.hydraulicRodBreakLinkLength, DEFAULT_HYDRAULIC_ROD_BREAK_LINK_LENGTH));
    }

    public static double hydraulicRodStrainLinkLength() {
        return clampDouble(getDouble(SERVER.hydraulicRodStrainLinkLength, DEFAULT_HYDRAULIC_ROD_STRAIN_LINK_LENGTH),
                hydraulicRodMaxLinkLength(), hydraulicRodBreakLinkLength());
    }

    public static int hydraulicRodMaxStretchResistance() {
        return getInt(SERVER.hydraulicRodMaxStretchResistance, DEFAULT_HYDRAULIC_ROD_MAX_STRETCH_RESISTANCE);
    }

    public static int hydraulicRodDefaultStretchResistance() {
        return clampInt(getInt(SERVER.hydraulicRodDefaultStretchResistance,
                        DEFAULT_HYDRAULIC_ROD_DEFAULT_STRETCH_RESISTANCE),
                0, hydraulicRodMaxStretchResistance());
    }

    public static int hydraulicRodMaxReturnForce() {
        return getInt(SERVER.hydraulicRodMaxReturnForce, DEFAULT_HYDRAULIC_ROD_MAX_RETURN_FORCE);
    }

    public static int hydraulicRodDefaultReturnForce() {
        return clampInt(getInt(SERVER.hydraulicRodDefaultReturnForce, DEFAULT_HYDRAULIC_ROD_DEFAULT_RETURN_FORCE),
                0, hydraulicRodMaxReturnForce());
    }

    public static double hydraulicRodLengthLimitStiffness() {
        return getFiniteDouble(SERVER.hydraulicRodLengthLimitStiffness, DEFAULT_HYDRAULIC_ROD_LENGTH_LIMIT_STIFFNESS);
    }

    public static double hydraulicRodLengthLimitCurve() {
        return getFiniteDouble(SERVER.hydraulicRodLengthLimitCurve, DEFAULT_HYDRAULIC_ROD_LENGTH_LIMIT_CURVE);
    }

    public static double hydraulicRodStretchDampingPerUnit() {
        return getFiniteDouble(SERVER.hydraulicRodStretchDampingPerUnit, DEFAULT_HYDRAULIC_ROD_STRETCH_DAMPING_PER_UNIT);
    }

    public static double hydraulicRodStretchMaxForcePerUnit() {
        return getFiniteDouble(SERVER.hydraulicRodStretchMaxForcePerUnit,
                DEFAULT_HYDRAULIC_ROD_STRETCH_MAX_FORCE_PER_UNIT);
    }

    public static double hydraulicRodReturnForcePerUnit() {
        return getFiniteDouble(SERVER.hydraulicRodReturnForcePerUnit, DEFAULT_HYDRAULIC_ROD_RETURN_FORCE_PER_UNIT);
    }

    public static double hydraulicRodReturnForceCurve() {
        return getFiniteDouble(SERVER.hydraulicRodReturnForceCurve, DEFAULT_HYDRAULIC_ROD_RETURN_FORCE_CURVE);
    }

    public static double hydraulicRodExpectedLengthApproachRate() {
        return getFiniteDouble(SERVER.hydraulicRodExpectedLengthApproachRate,
                DEFAULT_HYDRAULIC_ROD_EXPECTED_LENGTH_APPROACH_RATE);
    }

    public static double hydraulicRodReturnForceApproachRate() {
        return getFiniteDouble(SERVER.hydraulicRodReturnForceApproachRate,
                DEFAULT_HYDRAULIC_ROD_RETURN_FORCE_APPROACH_RATE);
    }

    public static double hydraulicRodMaxLengthLimitImpulse() {
        return getFiniteDouble(SERVER.hydraulicRodMaxLengthLimitImpulse, DEFAULT_HYDRAULIC_ROD_MAX_LENGTH_LIMIT_IMPULSE);
    }

    public static double hydraulicRodMaxExpectedReturnImpulse() {
        return getFiniteDouble(SERVER.hydraulicRodMaxExpectedReturnImpulse,
                DEFAULT_HYDRAULIC_ROD_MAX_EXPECTED_RETURN_IMPULSE);
    }

    public static double hydraulicRodMaxCombinedLengthControlImpulse() {
        return getFiniteDouble(SERVER.hydraulicRodMaxCombinedLengthControlImpulse,
                DEFAULT_HYDRAULIC_ROD_MAX_COMBINED_LENGTH_CONTROL_IMPULSE);
    }

    public static float regulatorStressImpact() {
        return (float) getDouble(SERVER.regulatorStressImpact, DEFAULT_REGULATOR_STRESS_IMPACT);
    }

    public static double regulatorMinTransitionSpeed() {
        return getDouble(SERVER.regulatorMinTransitionSpeed, DEFAULT_REGULATOR_MIN_TRANSITION_SPEED);
    }

    public static double regulatorMaxTransitionSpeed() {
        return getDouble(SERVER.regulatorMaxTransitionSpeed, DEFAULT_REGULATOR_MAX_TRANSITION_SPEED);
    }

    public static double regulatorMinTransitionMultiplier() {
        return getDouble(SERVER.regulatorMinTransitionMultiplier, DEFAULT_REGULATOR_MIN_TRANSITION_MULTIPLIER);
    }

    public static double regulatorMaxTransitionMultiplier() {
        return getDouble(SERVER.regulatorMaxTransitionMultiplier, DEFAULT_REGULATOR_MAX_TRANSITION_MULTIPLIER);
    }

    public static double andesiteJointLinkRange() {
        return getDouble(SERVER.andesiteJointLinkRange, DEFAULT_ANDESITE_JOINT_LINK_RANGE);
    }

    public static double andesiteJointSoftRange() {
        return getDouble(SERVER.andesiteJointSoftRange, DEFAULT_ANDESITE_JOINT_SOFT_RANGE);
    }

    public static double andesiteJointDisconnectRange() {
        return Math.max(andesiteJointSoftRange() + MIN_JOINT_RANGE_GAP,
                getDouble(SERVER.andesiteJointDisconnectRange, DEFAULT_ANDESITE_JOINT_DISCONNECT_RANGE));
    }

    public static double andesiteJointPullStiffness() {
        return getDouble(SERVER.andesiteJointPullStiffness, DEFAULT_ANDESITE_JOINT_PULL_STIFFNESS);
    }

    public static double andesiteJointPullDamping() {
        return getDouble(SERVER.andesiteJointPullDamping, DEFAULT_ANDESITE_JOINT_PULL_DAMPING);
    }

    public static double brassJointLinkRange() {
        return getDouble(SERVER.brassJointLinkRange, DEFAULT_BRASS_JOINT_LINK_RANGE);
    }

    public static double brassJointSoftRange() {
        return getDouble(SERVER.brassJointSoftRange, DEFAULT_BRASS_JOINT_SOFT_RANGE);
    }

    public static double brassJointDisconnectRange() {
        return Math.max(brassJointSoftRange() + MIN_JOINT_RANGE_GAP,
                getDouble(SERVER.brassJointDisconnectRange, DEFAULT_BRASS_JOINT_DISCONNECT_RANGE));
    }

    public static double brassJointPullStiffness() {
        return getDouble(SERVER.brassJointPullStiffness, DEFAULT_BRASS_JOINT_PULL_STIFFNESS);
    }

    public static double brassJointPullCurve() {
        return getDouble(SERVER.brassJointPullCurve, DEFAULT_BRASS_JOINT_PULL_CURVE);
    }

    public static double brassJointPullDamping() {
        return getDouble(SERVER.brassJointPullDamping, DEFAULT_BRASS_JOINT_PULL_DAMPING);
    }

    public static double brassJointPullDampingGain() {
        return getDouble(SERVER.brassJointPullDampingGain, DEFAULT_BRASS_JOINT_PULL_DAMPING_GAIN);
    }

    public static double brassJointEndpointPullMultiplier() {
        return getDouble(SERVER.brassJointEndpointPullMultiplier, DEFAULT_BRASS_JOINT_ENDPOINT_PULL_MULTIPLIER);
    }

    private static int getInt(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException | NullPointerException ignored) {
            return fallback;
        }
    }

    private static double getDouble(ModConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (IllegalStateException | NullPointerException ignored) {
            return fallback;
        }
    }

    private static double getFiniteDouble(ModConfigSpec.DoubleValue value, double fallback) {
        double result = getDouble(value, fallback);
        return Double.isFinite(result) ? result : fallback;
    }

    private static int clampInt(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isValidBlockId(Object value) {
        return value instanceof String string
                && BLOCK_ID_PATTERN.matcher(string.trim().toLowerCase(Locale.ROOT)).matches();
    }

    private static Set<Block> resolveBlockBlacklist(Set<String> blacklistIds) {
        if (blacklistIds.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<Block> blocks = new LinkedHashSet<>();
        for (String id : blacklistIds) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key != null) {
                BuiltInRegistries.BLOCK.getOptional(key).ifPresent(blocks::add);
            }
        }
        return Collections.unmodifiableSet(blocks);
    }

    public static final class Server {
        final ModConfigSpec.IntValue dampingMaxResistance;
        final ModConfigSpec.IntValue dampingDefaultResistance;
        final ModConfigSpec.DoubleValue dampingRatedOutputSpeedRpm;
        final ModConfigSpec.DoubleValue dampingMaxFullSpeedStressOutput;
        final ModConfigSpec.DoubleValue dampingSuppressedStressOutput;
        final ModConfigSpec.DoubleValue dampingResistanceTorquePerUnit;
        final ModConfigSpec.DoubleValue dampingSpeedDampingBaseRpm;
        final ModConfigSpec.ConfigValue<List<? extends String>> stressOutputBlacklist;
        final ModConfigSpec.DoubleValue hydraulicRodMinLinkLength;
        final ModConfigSpec.DoubleValue hydraulicRodMaxLinkLength;
        final ModConfigSpec.DoubleValue hydraulicRodBreakLinkLength;
        final ModConfigSpec.DoubleValue hydraulicRodStrainLinkLength;
        final ModConfigSpec.IntValue hydraulicRodMaxStretchResistance;
        final ModConfigSpec.IntValue hydraulicRodDefaultStretchResistance;
        final ModConfigSpec.IntValue hydraulicRodMaxReturnForce;
        final ModConfigSpec.IntValue hydraulicRodDefaultReturnForce;
        final ModConfigSpec.DoubleValue hydraulicRodLengthLimitStiffness;
        final ModConfigSpec.DoubleValue hydraulicRodLengthLimitCurve;
        final ModConfigSpec.DoubleValue hydraulicRodStretchDampingPerUnit;
        final ModConfigSpec.DoubleValue hydraulicRodStretchMaxForcePerUnit;
        final ModConfigSpec.DoubleValue hydraulicRodReturnForcePerUnit;
        final ModConfigSpec.DoubleValue hydraulicRodReturnForceCurve;
        final ModConfigSpec.DoubleValue hydraulicRodExpectedLengthApproachRate;
        final ModConfigSpec.DoubleValue hydraulicRodReturnForceApproachRate;
        final ModConfigSpec.DoubleValue hydraulicRodMaxLengthLimitImpulse;
        final ModConfigSpec.DoubleValue hydraulicRodMaxExpectedReturnImpulse;
        final ModConfigSpec.DoubleValue hydraulicRodMaxCombinedLengthControlImpulse;
        final ModConfigSpec.DoubleValue regulatorStressImpact;
        final ModConfigSpec.DoubleValue regulatorMinTransitionSpeed;
        final ModConfigSpec.DoubleValue regulatorMaxTransitionSpeed;
        final ModConfigSpec.DoubleValue regulatorMinTransitionMultiplier;
        final ModConfigSpec.DoubleValue regulatorMaxTransitionMultiplier;
        final ModConfigSpec.DoubleValue andesiteJointLinkRange;
        final ModConfigSpec.DoubleValue andesiteJointSoftRange;
        final ModConfigSpec.DoubleValue andesiteJointDisconnectRange;
        final ModConfigSpec.DoubleValue andesiteJointPullStiffness;
        final ModConfigSpec.DoubleValue andesiteJointPullDamping;
        final ModConfigSpec.DoubleValue brassJointLinkRange;
        final ModConfigSpec.DoubleValue brassJointSoftRange;
        final ModConfigSpec.DoubleValue brassJointDisconnectRange;
        final ModConfigSpec.DoubleValue brassJointPullStiffness;
        final ModConfigSpec.DoubleValue brassJointPullCurve;
        final ModConfigSpec.DoubleValue brassJointPullDamping;
        final ModConfigSpec.DoubleValue brassJointPullDampingGain;
        final ModConfigSpec.DoubleValue brassJointEndpointPullMultiplier;

        private Server(ModConfigSpec.Builder builder) {
            builder.push("damping_stress_bearing");
            this.dampingMaxResistance = builder
                    .comment("Maximum damping resistance selectable in the bearing GUI.")
                    .defineInRange("max_resistance", DEFAULT_DAMPING_MAX_RESISTANCE, 1, 4096);
            this.dampingDefaultResistance = builder
                    .comment("Default damping resistance for newly placed damping stress bearings.")
                    .defineInRange("default_resistance", DEFAULT_DAMPING_DEFAULT_RESISTANCE, 0, 4096);
            this.dampingRatedOutputSpeedRpm = builder
                    .comment("RPM at which full configured stress output is reached at maximum resistance.")
                    .defineInRange("rated_output_speed_rpm", DEFAULT_DAMPING_RATED_OUTPUT_SPEED_RPM, 1.0D, 4096.0D);
            this.dampingMaxFullSpeedStressOutput = builder
                    .comment("Maximum stress capacity output at rated speed and maximum resistance.")
                    .defineInRange("max_full_speed_stress_output", DEFAULT_DAMPING_MAX_FULL_SPEED_STRESS_OUTPUT, 0.0D, 10000000.0D);
            this.dampingSuppressedStressOutput = builder
                    .comment("Stress capacity cap while a blacklisted block is attached. Speed output is preserved.")
                    .defineInRange("suppressed_stress_output", DEFAULT_DAMPING_SUPPRESSED_STRESS_OUTPUT, 0.0D, 10000000.0D);
            this.dampingResistanceTorquePerUnit = builder
                    .comment("Physical damping impulse strength per resistance point.")
                    .defineInRange("resistance_torque_per_unit", DEFAULT_DAMPING_RESISTANCE_TORQUE_PER_UNIT, 0.0D, 10000.0D);
            this.dampingSpeedDampingBaseRpm = builder
                    .comment("RPM above which damping grows faster to tame high speed assemblies.")
                    .defineInRange("speed_damping_base_rpm", DEFAULT_DAMPING_SPEED_DAMPING_BASE_RPM, 1.0D, 4096.0D);
            this.stressOutputBlacklist = builder
                    .comment(
                            "Blocks that disable damping bearing stress output when found on the attached child sublevel or an adjacent physical structure.",
                            "Speed output is preserved; stress capacity is limited by suppressed_stress_output.",
                            "Use full block ids such as simulated:swivel_bearing.",
                            "Required safety entries are always included even if old configs do not list them.")
                    .defineListAllowEmpty(
                            "stress_output_blacklist",
                            () -> REQUIRED_STRESS_OUTPUT_BLACKLIST,
                            () -> "minecraft:stone",
                            AeroUniversalJointConfig::isValidBlockId);
            builder.pop();

            builder.push("hydraulic_rod");
            this.hydraulicRodMinLinkLength = builder
                    .comment("Minimum allowed hydraulic rod length, in blocks.")
                    .defineInRange("min_link_length", DEFAULT_HYDRAULIC_ROD_MIN_LINK_LENGTH, 0.5D, 128.0D);
            this.hydraulicRodMaxLinkLength = builder
                    .comment("Maximum normal hydraulic rod length before limit force starts, in blocks.")
                    .defineInRange("max_link_length", DEFAULT_HYDRAULIC_ROD_MAX_LINK_LENGTH, 0.5D, 128.0D);
            this.hydraulicRodBreakLinkLength = builder
                    .comment("Hydraulic rod length that breaks the link, in blocks.")
                    .defineInRange("break_link_length", DEFAULT_HYDRAULIC_ROD_BREAK_LINK_LENGTH, 0.5D, 128.0D);
            this.hydraulicRodStrainLinkLength = builder
                    .comment("Hydraulic rod length that starts the red strain warning effect, in blocks.")
                    .defineInRange("strain_link_length", DEFAULT_HYDRAULIC_ROD_STRAIN_LINK_LENGTH, 0.5D, 128.0D);
            this.hydraulicRodMaxStretchResistance = builder
                    .comment("Maximum stretch resistance value available in the hydraulic rod GUI.")
                    .defineInRange("max_stretch_resistance", DEFAULT_HYDRAULIC_ROD_MAX_STRETCH_RESISTANCE, 1, 1048576);
            this.hydraulicRodDefaultStretchResistance = builder
                    .comment("Default stretch resistance for newly linked hydraulic rods.")
                    .defineInRange("default_stretch_resistance", DEFAULT_HYDRAULIC_ROD_DEFAULT_STRETCH_RESISTANCE, 0, 1048576);
            this.hydraulicRodMaxReturnForce = builder
                    .comment("Maximum return force value available in the hydraulic rod GUI.")
                    .defineInRange("max_return_force", DEFAULT_HYDRAULIC_ROD_MAX_RETURN_FORCE, 1, 1048576);
            this.hydraulicRodDefaultReturnForce = builder
                    .comment("Default return force for newly linked hydraulic rods.")
                    .defineInRange("default_return_force", DEFAULT_HYDRAULIC_ROD_DEFAULT_RETURN_FORCE, 0, 1048576);
            this.hydraulicRodLengthLimitStiffness = builder
                    .comment("Physical stiffness applied when the rod is pushed shorter than min_link_length or longer than max_link_length.")
                    .defineInRange("length_limit_stiffness", DEFAULT_HYDRAULIC_ROD_LENGTH_LIMIT_STIFFNESS, 0.0D, 100000.0D);
            this.hydraulicRodLengthLimitCurve = builder
                    .comment("Exponential curve for hydraulic rod length limit force.")
                    .defineInRange("length_limit_curve", DEFAULT_HYDRAULIC_ROD_LENGTH_LIMIT_CURVE, 0.0D, 32.0D);
            this.hydraulicRodStretchDampingPerUnit = builder
                    .comment("Constraint motor damping contributed by each stretch resistance point.")
                    .defineInRange("stretch_damping_per_unit", DEFAULT_HYDRAULIC_ROD_STRETCH_DAMPING_PER_UNIT, 0.0D, 10000.0D);
            this.hydraulicRodStretchMaxForcePerUnit = builder
                    .comment("Maximum constraint motor force contributed by each stretch resistance point.")
                    .defineInRange("stretch_max_force_per_unit", DEFAULT_HYDRAULIC_ROD_STRETCH_MAX_FORCE_PER_UNIT, 0.0D, 100000.0D);
            this.hydraulicRodReturnForcePerUnit = builder
                    .comment("Restoring force contributed by each return force point.")
                    .defineInRange("return_force_per_unit", DEFAULT_HYDRAULIC_ROD_RETURN_FORCE_PER_UNIT, 0.0D, 100000.0D);
            this.hydraulicRodReturnForceCurve = builder
                    .comment("Exponential curve for expected-length return force.")
                    .defineInRange("return_force_curve", DEFAULT_HYDRAULIC_ROD_RETURN_FORCE_CURVE, 0.0D, 32.0D);
            this.hydraulicRodExpectedLengthApproachRate = builder
                    .comment("Blocks per second that expected length can move before regulator speed multipliers.")
                    .defineInRange("expected_length_approach_rate", DEFAULT_HYDRAULIC_ROD_EXPECTED_LENGTH_APPROACH_RATE, 0.0D, 128.0D);
            this.hydraulicRodReturnForceApproachRate = builder
                    .comment("Return force points per second used when smoothing GUI changes.")
                    .defineInRange("return_force_approach_rate", DEFAULT_HYDRAULIC_ROD_RETURN_FORCE_APPROACH_RATE, 0.0D, 1048576.0D);
            this.hydraulicRodMaxLengthLimitImpulse = builder
                    .comment("Maximum per-tick impulse applied by hydraulic rod length limit force.")
                    .defineInRange("max_length_limit_impulse", DEFAULT_HYDRAULIC_ROD_MAX_LENGTH_LIMIT_IMPULSE, 0.0D, 1000000.0D);
            this.hydraulicRodMaxExpectedReturnImpulse = builder
                    .comment("Maximum per-tick impulse applied by expected-length return force.")
                    .defineInRange("max_expected_return_impulse", DEFAULT_HYDRAULIC_ROD_MAX_EXPECTED_RETURN_IMPULSE, 0.0D, 1000000.0D);
            this.hydraulicRodMaxCombinedLengthControlImpulse = builder
                    .comment("Maximum combined per-tick impulse from hydraulic rod length control.")
                    .defineInRange("max_combined_length_control_impulse", DEFAULT_HYDRAULIC_ROD_MAX_COMBINED_LENGTH_CONTROL_IMPULSE, 0.0D, 1000000.0D);
            builder.pop();

            builder.push("hydraulic_regulator");
            this.regulatorStressImpact = builder
                    .comment("Create stress impact of each pneumatic regulator.")
                    .defineInRange("stress_impact", DEFAULT_REGULATOR_STRESS_IMPACT, 0.0D, 1024.0D);
            this.regulatorMinTransitionSpeed = builder
                    .comment("RPM mapped to the minimum hydraulic length transition multiplier.")
                    .defineInRange("min_transition_speed", DEFAULT_REGULATOR_MIN_TRANSITION_SPEED, 0.0D, 4096.0D);
            this.regulatorMaxTransitionSpeed = builder
                    .comment("RPM mapped to the maximum hydraulic length transition multiplier.")
                    .defineInRange("max_transition_speed", DEFAULT_REGULATOR_MAX_TRANSITION_SPEED, 1.0D, 4096.0D);
            this.regulatorMinTransitionMultiplier = builder
                    .comment("Slowest hydraulic length transition multiplier applied by a powered regulator.")
                    .defineInRange("min_transition_multiplier", DEFAULT_REGULATOR_MIN_TRANSITION_MULTIPLIER, 0.01D, 64.0D);
            this.regulatorMaxTransitionMultiplier = builder
                    .comment("Fastest hydraulic length transition multiplier applied by a powered regulator.")
                    .defineInRange("max_transition_multiplier", DEFAULT_REGULATOR_MAX_TRANSITION_MULTIPLIER, 0.01D, 64.0D);
            builder.pop();

            builder.push("universal_joint");
            builder.push("andesite");
            this.andesiteJointLinkRange = builder
                    .comment("Maximum distance for creating an andesite universal joint link.")
                    .defineInRange("link_range", DEFAULT_ANDESITE_JOINT_LINK_RANGE, 0.5D, 128.0D);
            this.andesiteJointSoftRange = builder
                    .comment("Allowed stretch or compression from rest length before andesite elastic pull starts.")
                    .defineInRange("soft_range", DEFAULT_ANDESITE_JOINT_SOFT_RANGE, 0.0D, 128.0D);
            this.andesiteJointDisconnectRange = builder
                    .comment("Stretch or compression from rest length that breaks an andesite joint link.")
                    .defineInRange("disconnect_range", DEFAULT_ANDESITE_JOINT_DISCONNECT_RANGE, 0.01D, 128.0D);
            this.andesiteJointPullStiffness = builder
                    .comment("Elastic pull stiffness for andesite joint links beyond the soft range.")
                    .defineInRange("pull_stiffness", DEFAULT_ANDESITE_JOINT_PULL_STIFFNESS, 0.0D, 100000.0D);
            this.andesiteJointPullDamping = builder
                    .comment("Elastic damping for andesite joint links beyond the soft range.")
                    .defineInRange("pull_damping", DEFAULT_ANDESITE_JOINT_PULL_DAMPING, 0.0D, 10000.0D);
            builder.pop();

            builder.push("brass");
            this.brassJointLinkRange = builder
                    .comment("Maximum distance for creating a brass universal joint link.")
                    .defineInRange("link_range", DEFAULT_BRASS_JOINT_LINK_RANGE, 0.5D, 128.0D);
            this.brassJointSoftRange = builder
                    .comment("Distance before brass joint elastic pull starts.")
                    .defineInRange("soft_range", DEFAULT_BRASS_JOINT_SOFT_RANGE, 0.0D, 128.0D);
            this.brassJointDisconnectRange = builder
                    .comment("Distance that breaks a brass joint link.")
                    .defineInRange("disconnect_range", DEFAULT_BRASS_JOINT_DISCONNECT_RANGE, 0.01D, 128.0D);
            this.brassJointPullStiffness = builder
                    .comment("Elastic pull stiffness for brass joint links beyond the soft range.")
                    .defineInRange("pull_stiffness", DEFAULT_BRASS_JOINT_PULL_STIFFNESS, 0.0D, 100000.0D);
            this.brassJointPullCurve = builder
                    .comment("Exponential pull curve for brass joint links beyond the soft range.")
                    .defineInRange("pull_curve", DEFAULT_BRASS_JOINT_PULL_CURVE, 0.0D, 32.0D);
            this.brassJointPullDamping = builder
                    .comment("Base damping for brass joint links beyond the soft range.")
                    .defineInRange("pull_damping", DEFAULT_BRASS_JOINT_PULL_DAMPING, 0.0D, 10000.0D);
            this.brassJointPullDampingGain = builder
                    .comment("Additional damping per block of brass joint overshoot.")
                    .defineInRange("pull_damping_gain", DEFAULT_BRASS_JOINT_PULL_DAMPING_GAIN, 0.0D, 10000.0D);
            this.brassJointEndpointPullMultiplier = builder
                    .comment("Extra pull multiplier as brass links approach disconnect range.")
                    .defineInRange("endpoint_pull_multiplier", DEFAULT_BRASS_JOINT_ENDPOINT_PULL_MULTIPLIER, 1.0D, 128.0D);
            builder.pop();
            builder.pop();
        }
    }
}
