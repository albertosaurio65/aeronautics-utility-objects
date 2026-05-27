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
        final ModConfigSpec.ConfigValue<List<? extends String>> stressOutputBlacklist;

        private Server(ModConfigSpec.Builder builder) {
            builder.push("damping_stress_bearing");
            this.stressOutputBlacklist = builder
                    .comment(
                            "Blocks that disable damping bearing stress output when found on the attached child sublevel or an adjacent physical structure.",
                            "Speed output is preserved; stress capacity is limited to 40000 SU.",
                            "Use full block ids such as simulated:swivel_bearing.",
                            "Required safety entries are always included even if old configs do not list them.")
                    .defineListAllowEmpty(
                            "stress_output_blacklist",
                            () -> REQUIRED_STRESS_OUTPUT_BLACKLIST,
                            () -> "minecraft:stone",
                            AeroUniversalJointConfig::isValidBlockId);
            builder.pop();
        }
    }
}
