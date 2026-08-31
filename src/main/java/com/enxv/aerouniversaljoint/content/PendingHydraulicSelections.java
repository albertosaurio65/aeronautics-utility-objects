package com.enxv.aerouniversaljoint.content;

import java.util.Optional;
import net.minecraft.world.entity.player.Player;

public final class PendingHydraulicSelections {
    private static final PlayerSelectionStore<JointBindingData.Selection> SELECTIONS = new PlayerSelectionStore<>();

    private PendingHydraulicSelections() {
    }

    public static Optional<JointBindingData.Selection> read(Player player) {
        return SELECTIONS.read(player);
    }

    public static void write(Player player, JointBindingData.Selection selection) {
        SELECTIONS.write(player, selection);
    }

    public static void clear(Player player) {
        SELECTIONS.clear(player);
    }

    public static Optional<JointBindingData.Selection> readClient(Player player) {
        return SELECTIONS.readClient(player);
    }

    public static void writeClient(Player player, JointBindingData.Selection selection) {
        SELECTIONS.writeClient(player, selection);
    }

    public static void clearClient(Player player) {
        SELECTIONS.clearClient(player);
    }
}
