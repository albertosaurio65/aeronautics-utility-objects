package com.enxv.aerouniversaljoint.content;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

public final class PendingRodSelections {
    private static final Map<UUID, PendingSelection> SELECTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingSelection> CLIENT_SELECTIONS = new ConcurrentHashMap<>();

    private PendingRodSelections() {
    }

    public static Optional<JointBindingData.Selection> read(Player player) {
        return readPending(player).map(PendingSelection::selection);
    }

    public static Optional<PendingSelection> readPending(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SELECTIONS.get(player.getUUID()));
    }

    public static void write(Player player, JointBindingData.Selection selection) {
        write(player, selection, true);
    }

    public static void write(Player player, JointBindingData.Selection selection, boolean brassRod) {
        if (player == null) {
            return;
        }
        SELECTIONS.put(player.getUUID(), new PendingSelection(selection, brassRod));
    }

    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        SELECTIONS.remove(player.getUUID());
    }

    public static Optional<PendingSelection> readClientPending(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CLIENT_SELECTIONS.get(player.getUUID()));
    }

    public static void writeClient(Player player, JointBindingData.Selection selection, boolean brassRod) {
        if (player == null) {
            return;
        }
        CLIENT_SELECTIONS.put(player.getUUID(), new PendingSelection(selection, brassRod));
    }

    public static void clearClient(Player player) {
        if (player == null) {
            return;
        }
        CLIENT_SELECTIONS.remove(player.getUUID());
    }

    public record PendingSelection(JointBindingData.Selection selection, boolean brassRod) {
    }
}

