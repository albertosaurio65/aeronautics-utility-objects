package com.enxv.aerouniversaljoint.util;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class SubLevelReferenceHelper {
    private SubLevelReferenceHelper() {
    }

    @Nullable
    public static BlockEntity resolveBlockEntity(Level level, BlockPos pos, @Nullable UUID preferredSubLevelId) {
        if (!isResolvablePosition(level, pos)) {
            return null;
        }

        if (preferredSubLevelId != null) {
            SubLevel preferredSubLevel = SubLevelContainer.getContainer(level).getSubLevel(preferredSubLevelId);
            if (preferredSubLevel != null) {
                BlockEntity blockEntity = resolveInSubLevel(level, preferredSubLevel, pos, true);
                if (blockEntity != null) {
                    return blockEntity;
                }
            }
        }

        BlockEntity blockEntity = getLoadedBlockEntity(level, pos);
        if (blockEntity != null) {
            return blockEntity;
        }

        SubLevel containingSubLevel = Sable.HELPER.getContaining(level, pos);
        return containingSubLevel != null
                ? resolveInSubLevel(level, containingSubLevel, pos, false)
                : null;
    }

    @Nullable
    public static BlockEntity resolveBlockEntityFast(Level level, BlockPos pos, @Nullable UUID preferredSubLevelId) {
        if (!isResolvablePosition(level, pos)) {
            return null;
        }

        if (preferredSubLevelId != null) {
            SubLevel preferredSubLevel = SubLevelContainer.getContainer(level).getSubLevel(preferredSubLevelId);
            if (preferredSubLevel != null) {
                BlockEntity blockEntity = resolveInSubLevel(level, preferredSubLevel, pos, true);
                if (blockEntity != null) {
                    return blockEntity;
                }
            }
        }

        return getLoadedBlockEntity(level, pos);
    }

    @Nullable
    public static UUID findContainingSubLevelId(Level level, BlockPos pos) {
        if (!isResolvablePosition(level, pos)) {
            return null;
        }

        SubLevel containingSubLevel = Sable.HELPER.getContaining(level, pos);
        return containingSubLevel != null ? containingSubLevel.getUniqueId() : null;
    }

    @Nullable
    public static UUID findContainingSubLevelId(BlockEntity blockEntity) {
        SubLevel containingSubLevel = Sable.HELPER.getContaining(blockEntity);
        return containingSubLevel != null ? containingSubLevel.getUniqueId() : null;
    }

    public static int compareNullableUuids(@Nullable UUID first, @Nullable UUID second) {
        if (first == second) {
            return 0;
        }
        if (first == null) {
            return -1;
        }
        if (second == null) {
            return 1;
        }

        int most = Long.compare(first.getMostSignificantBits(), second.getMostSignificantBits());
        return most != 0 ? most : Long.compare(first.getLeastSignificantBits(), second.getLeastSignificantBits());
    }

    @Nullable
    private static BlockEntity resolveInSubLevel(Level level, SubLevel subLevel, BlockPos pos, boolean allowStoredPos) {
        if (allowStoredPos) {
            BlockEntity blockEntity = getLoadedBlockEntity(level, pos);
            if (belongsToSubLevel(blockEntity, subLevel)) {
                return blockEntity;
            }
        }

        Vec3 localCenter = subLevel.logicalPose().transformPositionInverse(Vec3.atCenterOf(pos));
        BlockPos localPos = BlockPos.containing(localCenter);
        if (!isResolvablePosition(level, localPos)) {
            return null;
        }
        BlockEntity blockEntity = localPos.equals(pos) && allowStoredPos
                ? null
                : getLoadedBlockEntity(level, localPos);
        if (belongsToSubLevel(blockEntity, subLevel)) {
            return blockEntity;
        }

        return null;
    }

    @Nullable
    private static BlockEntity getLoadedBlockEntity(Level level, BlockPos pos) {
        if (!isResolvablePosition(level, pos)) {
            return null;
        }

        ChunkAccess chunk = level.getChunk(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()),
                ChunkStatus.FULL,
                false);
        return chunk instanceof LevelChunk levelChunk
                ? levelChunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK)
                : null;
    }

    private static boolean isResolvablePosition(Level level, BlockPos pos) {
        return level.isInWorldBounds(pos);
    }

    private static boolean belongsToSubLevel(@Nullable BlockEntity blockEntity, SubLevel subLevel) {
        UUID containingSubLevelId = blockEntity != null ? findContainingSubLevelId(blockEntity) : null;
        return containingSubLevelId != null && containingSubLevelId.equals(subLevel.getUniqueId());
    }
}
