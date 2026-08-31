package com.enxv.aerouniversaljoint.content;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

final class PlayerSelectionStore<T> {
    private final Map<UUID, T> serverSelections = new ConcurrentHashMap<>();
    private final Map<UUID, T> clientSelections = new ConcurrentHashMap<>();

    Optional<T> read(Player player) {
        return player == null ? Optional.empty() : Optional.ofNullable(this.serverSelections.get(player.getUUID()));
    }

    void write(Player player, T selection) {
        if (player != null) {
            this.serverSelections.put(player.getUUID(), selection);
        }
    }

    void clear(Player player) {
        if (player != null) {
            this.serverSelections.remove(player.getUUID());
        }
    }

    Optional<T> readClient(Player player) {
        return player == null ? Optional.empty() : Optional.ofNullable(this.clientSelections.get(player.getUUID()));
    }

    void writeClient(Player player, T selection) {
        if (player != null) {
            this.clientSelections.put(player.getUUID(), selection);
        }
    }

    void clearClient(Player player) {
        if (player != null) {
            this.clientSelections.remove(player.getUUID());
        }
    }
}
