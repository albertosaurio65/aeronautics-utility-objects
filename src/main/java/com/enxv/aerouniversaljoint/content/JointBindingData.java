package com.enxv.aerouniversaljoint.content;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class JointBindingData {
    private JointBindingData() {
    }

    public record Selection(ResourceLocation dimensionId, BlockPos pos, @Nullable UUID subLevelId, boolean creativeHydraulic) {
        public Selection(ResourceLocation dimensionId, BlockPos pos, @Nullable UUID subLevelId) {
            this(dimensionId, pos, subLevelId, false);
        }
    }
}

