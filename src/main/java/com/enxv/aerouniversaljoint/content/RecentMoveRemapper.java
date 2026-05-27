package com.enxv.aerouniversaljoint.content;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

final class RecentMoveRemapper {
    private static final long ENTRY_TTL_MS = 30_000L;
    private static final Map<Key, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<PendingKey, PendingMove> PENDING_MOVES = new ConcurrentHashMap<>();

    private RecentMoveRemapper() {
    }

    static void prepare(Level level, BlockPos oldPos, @Nullable UUID oldSubLevelId) {
        pruneExpired();
        PENDING_MOVES.put(
                new PendingKey(level.dimension().location(), oldPos.immutable()),
                new PendingMove(oldSubLevelId, System.currentTimeMillis() + ENTRY_TTL_MS));
    }

    static void record(Level level, BlockPos oldPos, BlockPos newPos, @Nullable UUID newSubLevelId) {
        pruneExpired();
        PendingMove pendingMove = PENDING_MOVES.remove(new PendingKey(level.dimension().location(), oldPos.immutable()));
        UUID oldSubLevelId = pendingMove != null ? pendingMove.oldSubLevelId() : null;
        ENTRIES.put(
                new Key(level.dimension().location(), oldPos.immutable(), oldSubLevelId),
                new Entry(new JointBindingData.Selection(level.dimension().location(), newPos.immutable(), newSubLevelId),
                        System.currentTimeMillis() + ENTRY_TTL_MS));
    }

    static Optional<JointBindingData.Selection> remap(Level level, JointBindingData.Selection selection) {
        pruneExpired();
        if (!level.dimension().location().equals(selection.dimensionId())) {
            return Optional.empty();
        }

        Entry entry = ENTRIES.remove(new Key(selection.dimensionId(), selection.pos(), selection.subLevelId()));
        if (entry == null) {
            return Optional.empty();
        }

        return Optional.of(entry.selection());
    }

    private static void pruneExpired() {
        long now = System.currentTimeMillis();
        ENTRIES.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        PENDING_MOVES.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    private record PendingKey(ResourceLocation dimensionId, BlockPos pos) {
    }

    private record PendingMove(@Nullable UUID oldSubLevelId, long expiresAt) {
    }

    private record Key(ResourceLocation dimensionId, BlockPos pos, @Nullable UUID subLevelId) {
    }

    private record Entry(JointBindingData.Selection selection, long expiresAt) {
    }
}

