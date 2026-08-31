package com.enxv.aerouniversaljoint.content;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

interface SubLevelLinkedEndpoint {
    @Nullable UUID getContainingSubLevelId();

    void preserveLinkForSubLevelMove();

    void remapLinkedReferenceAfterSubLevelMove();

    @Nullable SubLevelLinkedEndpoint getLoadedLinkedEndpoint();

    void updateReferenceTo(BlockPos newPos, @Nullable UUID newSubLevelId);
}
