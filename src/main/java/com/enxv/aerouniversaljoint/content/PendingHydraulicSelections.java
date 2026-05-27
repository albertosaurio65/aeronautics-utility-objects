package com.enxv.aerouniversaljoint.content;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

public final class PendingHydraulicSelections {
    private static final Map<UUID, JointBindingData.Selection> SELECTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, JointBindingData.Selection> CLIENT_SELECTIONS = new ConcurrentHashMap<>();

    private PendingHydraulicSelections() {
    }

    public static Optional<JointBindingData.Selection> read(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SELECTIONS.get(player.getUUID()));
    }

    public static void write(Player player, JointBindingData.Selection selection) {
        if (player == null) {
            return;
        }
        SELECTIONS.put(player.getUUID(), selection);
    }

    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        SELECTIONS.remove(player.getUUID());
    }

    public static Optional<JointBindingData.Selection> readClient(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CLIENT_SELECTIONS.get(player.getUUID()));
    }

    public static void writeClient(Player player, JointBindingData.Selection selection) {
        if (player == null) {
            return;
        }
        CLIENT_SELECTIONS.put(player.getUUID(), selection);
    }

    public static void clearClient(Player player) {
        if (player == null) {
            return;
        }
        CLIENT_SELECTIONS.remove(player.getUUID());
    }
}
