package com.enxv.aerouniversaljoint.content;

import java.util.Optional;
import net.minecraft.world.entity.player.Player;

public final class PendingRodSelections {
    private static final PlayerSelectionStore<PendingSelection> SELECTIONS = new PlayerSelectionStore<>();

    private PendingRodSelections() {
    }

    public static Optional<JointBindingData.Selection> read(Player player) {
        return readPending(player).map(PendingSelection::selection);
    }

    public static Optional<PendingSelection> readPending(Player player) {
        return SELECTIONS.read(player);
    }

    public static void write(Player player, JointBindingData.Selection selection) {
        write(player, selection, true);
    }

    public static void write(Player player, JointBindingData.Selection selection, boolean brassRod) {
        SELECTIONS.write(player, new PendingSelection(selection, brassRod));
    }

    public static void clear(Player player) {
        SELECTIONS.clear(player);
    }

    public static Optional<PendingSelection> readClientPending(Player player) {
        return SELECTIONS.readClient(player);
    }

    public static void writeClient(Player player, JointBindingData.Selection selection, boolean brassRod) {
        SELECTIONS.writeClient(player, new PendingSelection(selection, brassRod));
    }

    public static void clearClient(Player player) {
        SELECTIONS.clearClient(player);
    }

    public record PendingSelection(JointBindingData.Selection selection, boolean brassRod) {
    }
}

