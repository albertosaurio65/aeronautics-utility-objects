package com.enxv.aerouniversaljoint.content;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class RecentMoveRemapper {
    private static final long ENTRY_TTL_TICKS = 600L;
    private static final Map<Key, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<PendingKey, PendingMove> PENDING_MOVES = new ConcurrentHashMap<>();

    private RecentMoveRemapper() {
    }

    static void prepare(Level level, BlockPos oldPos, @Nullable UUID oldSubLevelId) {
        pruneExpired(level);
        PENDING_MOVES.put(
                new PendingKey(level.dimension().location(), oldPos.immutable()),
                new PendingMove(oldSubLevelId, expiresAt(level)));
    }

    static void record(Level level, BlockPos oldPos, BlockPos newPos, @Nullable UUID newSubLevelId) {
        pruneExpired(level);
        PendingMove pendingMove = PENDING_MOVES.remove(new PendingKey(level.dimension().location(), oldPos.immutable()));
        UUID oldSubLevelId = pendingMove != null ? pendingMove.oldSubLevelId() : null;
        ENTRIES.put(
                new Key(level.dimension().location(), oldPos.immutable(), oldSubLevelId),
                new Entry(new JointBindingData.Selection(level.dimension().location(), newPos.immutable(), newSubLevelId),
                        expiresAt(level)));
    }

    static Optional<JointBindingData.Selection> remap(Level level, JointBindingData.Selection selection) {
        pruneExpired(level);
        if (!level.dimension().location().equals(selection.dimensionId())) {
            return Optional.empty();
        }

        Entry entry = ENTRIES.remove(new Key(selection.dimensionId(), selection.pos(), selection.subLevelId()));
        if (entry == null) {
            return Optional.empty();
        }

        return Optional.of(entry.selection());
    }

    public static void pruneExpired(long currentGameTime) {
        ENTRIES.entrySet().removeIf(entry -> entry.getValue().expiresAt() < currentGameTime);
        PENDING_MOVES.entrySet().removeIf(entry -> entry.getValue().expiresAt() < currentGameTime);
    }

    public static void clear() {
        ENTRIES.clear();
        PENDING_MOVES.clear();
    }

    private static long expiresAt(Level level) {
        return level.getGameTime() + ENTRY_TTL_TICKS;
    }

    private static void pruneExpired(Level level) {
        pruneExpired(level.getGameTime());
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

