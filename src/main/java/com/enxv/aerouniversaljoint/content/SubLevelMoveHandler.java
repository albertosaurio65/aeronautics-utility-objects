package com.enxv.aerouniversaljoint.content;

import com.enxv.aerouniversaljoint.util.SubLevelReferenceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

final class SubLevelMoveHandler {
    private SubLevelMoveHandler() {
    }

    static void beforeMove(ServerLevel originLevel, BlockPos oldPos, @Nullable SubLevelLinkedEndpoint moving) {
        RecentMoveRemapper.prepare(originLevel, oldPos,
                moving != null ? moving.getContainingSubLevelId()
                        : SubLevelReferenceHelper.findContainingSubLevelId(originLevel, oldPos));
        if (moving != null) {
            moving.preserveLinkForSubLevelMove();
        }
    }

    static void afterMove(ServerLevel resultingLevel, BlockPos oldPos, BlockPos newPos,
                          @Nullable SubLevelLinkedEndpoint moved) {
        var newSubLevelId = SubLevelReferenceHelper.findContainingSubLevelId(resultingLevel, newPos);
        RecentMoveRemapper.record(resultingLevel, oldPos, newPos, newSubLevelId);
        if (moved == null) {
            return;
        }

        moved.remapLinkedReferenceAfterSubLevelMove();
        SubLevelLinkedEndpoint linked = moved.getLoadedLinkedEndpoint();
        if (linked != null) {
            linked.updateReferenceTo(newPos, newSubLevelId);
        }
    }
}
