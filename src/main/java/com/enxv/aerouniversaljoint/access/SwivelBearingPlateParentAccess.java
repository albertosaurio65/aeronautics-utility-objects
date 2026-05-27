package com.enxv.aerouniversaljoint.access;

import com.enxv.aerouniversaljoint.content.DampingStressBearingBlockEntity;
import com.enxv.aerouniversaljoint.util.SubLevelReferenceHelper;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public interface SwivelBearingPlateParentAccess {
    @Nullable
    BlockPos aeronautics$getParentPos();

    @Nullable
    UUID aeronautics$getParentSubLevelId();

    @Nullable
    default BlockEntity aeronautics$getParentBlockEntity(Level level) {
        BlockPos parentPos = this.aeronautics$getParentPos();
        return parentPos == null
                ? null
                : SubLevelReferenceHelper.resolveBlockEntity(level, parentPos, this.aeronautics$getParentSubLevelId());
    }

    default boolean aeronautics$isDampingStressBearingParent(Level level) {
        return this.aeronautics$getParentBlockEntity(level) instanceof DampingStressBearingBlockEntity;
    }
}
